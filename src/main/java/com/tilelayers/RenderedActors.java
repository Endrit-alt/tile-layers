package com.tilelayers;

import java.util.IdentityHashMap;
import java.util.Map;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.client.callback.RenderCallback;
import net.runelite.client.callback.RenderCallbackManager;

/** Tracks scene-selected actors and checks all render filters before masking. */
@Singleton
final class RenderedActors implements RenderCallback
{
    private final Map<Actor, GameObject> players = new IdentityHashMap<>();
    private final Map<Actor, GameObject> npcs = new IdentityHashMap<>();
    private final Map<Actor, Scene> scenes = new IdentityHashMap<>();
    private Player localPlayer;
    private GameObject localObject;
    private Scene localScene;
    private boolean checkingVisibility;

    void beginFrame(Player local)
    {
        players.clear();
        npcs.clear();
        scenes.clear();
        localPlayer = local;
        localObject = null;
        localScene = null;
    }

    @Override
    public boolean drawObject(Scene scene, TileObject object)
    {
        // Visibility queries must not look like another scene submission.
        if (checkingVisibility) return true;
        if (object instanceof GameObject)
        {
            GameObject gameObject = (GameObject) object;
            Renderable renderable = gameObject.getRenderable();
            if (renderable instanceof Player)
            {
                if (renderable == localPlayer)
                {
                    localObject = gameObject;
                    localScene = scene;
                }
                else if (players.size() < CrowdActors.MAX_LIMIT)
                {
                    players.put((Player) renderable, gameObject);
                    scenes.put((Player) renderable, scene);
                }
            }
            else if (renderable instanceof NPC && npcs.size() < CrowdActors.MAX_LIMIT)
            {
                npcs.put((NPC) renderable, gameObject);
                scenes.put((NPC) renderable, scene);
            }
        }
        // Observe drawing without changing the scene or other plugins' decisions.
        return true;
    }

    boolean contains(Actor actor)
    {
        if (actor == localPlayer) return localObject != null;
        return actor instanceof Player ? players.containsKey(actor) : npcs.containsKey(actor);
    }

    boolean isVisible(Actor actor, RenderCallbackManager callbacks)
    {
        GameObject object = actor == localPlayer ? localObject
                : actor instanceof Player ? players.get(actor) : npcs.get(actor);
        if (object == null) return false;
        Scene scene = actor == localPlayer ? localScene : scenes.get(actor);

        // drawObject callbacks are filters, not a notification that drawing was
        // accepted. A later callback can still hide an actor we observed. Use
        // the manager's combined decision on the same scene/object, independent
        // of registration order, before spending time on its model or mask.
        checkingVisibility = true;
        try
        {
            return callbacks.drawObject(scene, object);
        }
        finally
        {
            checkingVisibility = false;
        }
    }
}

package io.leikvolle.tileindicators;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.TileObject;
import net.runelite.client.callback.RenderCallback;

/** Records actors submitted to the renderer after the scene resolves stacks. */
@Singleton
final class RenderedActors implements RenderCallback
{
    private final Set<Actor> players = Collections.newSetFromMap(new IdentityHashMap<>());
    private final Set<Actor> npcs = Collections.newSetFromMap(new IdentityHashMap<>());
    private Player localPlayer;
    private boolean localDrawn;

    void beginFrame(Player local)
    {
        players.clear();
        npcs.clear();
        localPlayer = local;
        localDrawn = false;
    }

    @Override
    public boolean drawObject(Scene scene, TileObject object)
    {
        if (object instanceof GameObject)
        {
            Renderable renderable = ((GameObject) object).getRenderable();
            if (renderable instanceof Player)
            {
                if (renderable == localPlayer) localDrawn = true;
                else if (players.size() < CrowdActors.MAX_LIMIT) players.add((Player) renderable);
            }
            else if (renderable instanceof NPC && npcs.size() < CrowdActors.MAX_LIMIT)
            {
                npcs.add((NPC) renderable);
            }
        }
        // Observe drawing without changing the scene or other plugins' decisions.
        return true;
    }

    boolean contains(Actor actor)
    {
        if (actor == localPlayer) return localDrawn;
        return actor instanceof Player ? players.contains(actor) : npcs.contains(actor);
    }
}

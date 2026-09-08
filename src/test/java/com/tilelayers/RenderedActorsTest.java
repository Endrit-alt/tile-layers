package com.tilelayers;

import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Renderable;
import net.runelite.api.WallObject;
import org.junit.Test;
import static com.tilelayers.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class RenderedActorsTest
{
    @Test
    public void sceneAdmissionAndOverheadsDoNotCreateMasks()
    {
        RenderedActors rendered = new RenderedActors();
        Player local = stub(Player.class, values()), other = stub(Player.class, values());
        rendered.beginFrame(local);
        assertTrue(rendered.addEntity(local, false));
        assertTrue(rendered.addEntity(other, true));
        assertFalse(rendered.contains(local)); assertFalse(rendered.contains(other));
        assertTrue(rendered.drawObject(null, stub(WallObject.class, values())));
        assertTrue(rendered.drawObject(null, object(stub(Renderable.class, values()))));
        assertTrue(rendered.drawObject(null, object(other)));
        assertTrue(rendered.contains(other)); assertFalse(rendered.contains(local));
        rendered.beginFrame(local);
        assertFalse(rendered.contains(other));
        assertTrue(rendered.drawObject(null, object(local)));
        assertTrue(rendered.contains(local));
        rendered.beginFrame(null);
        assertFalse(rendered.contains(local));
    }

    @Test
    public void largeCrowdsCannotDisplaceSelfOrTheOtherCategory()
    {
        RenderedActors rendered = new RenderedActors();
        Player local = stub(Player.class, values());
        NPC npc = stub(NPC.class, values());
        Player overflow = stub(Player.class, values());
        rendered.beginFrame(local);
        for (int i = 0; i < 2000; i++)
            rendered.drawObject(null, object(stub(Player.class, values())));
        rendered.drawObject(null, object(overflow));
        assertFalse(rendered.contains(overflow));
        rendered.drawObject(null, object(npc));
        rendered.drawObject(null, object(local));
        assertTrue(rendered.contains(npc)); assertTrue(rendered.contains(local));
        rendered.beginFrame(local);
        rendered.drawObject(null, object(overflow));
        assertTrue(rendered.contains(overflow));
        assertFalse(rendered.contains(npc)); assertFalse(rendered.contains(local));
    }

    private static GameObject object(Renderable renderable)
    {
        return stub(GameObject.class, values("getRenderable", renderable));
    }
}

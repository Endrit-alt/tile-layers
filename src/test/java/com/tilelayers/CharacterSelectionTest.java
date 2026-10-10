package com.tilelayers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.tilelayers.TestSupport.*;
import static org.junit.Assert.*;

public class CharacterSelectionTest
{
    @Test
    public void crowdedScenesKeepTheNearestEligibleCharactersInsteadOfDisablingAllMasks()
    {
        WorldView world = stub(WorldView.class, values("isTopLevel", true));
        Player local = stub(Player.class, values("getWorldView", world, "getLocalLocation", new LocalPoint(128, 128, 0)));
        ArrayList<NPC> crowd = new ArrayList<>();
        for (int i = 0; i < 120; i++) crowd.add(npc(world, "NPC " + i, 256 + i * 128));
        Collections.reverse(crowd);
        NearestActors nearest = new NearestActors();
        nearest.setExcludedNpcs("NPC 0");
        nearest.select(local, crowd, null, false, false, 2);
        assertEquals(2, nearest.size());
        assertEquals("NPC 1", ((NPC) nearest.get(0)).getName());
        assertEquals("NPC 2", ((NPC) nearest.get(1)).getName());
        nearest.select(local, crowd, null, false, false, 0);
        assertEquals(0, nearest.size());
        assertTrue(nearest.selected().isEmpty());
    }

    @Test
    public void hiddenModelCannotBlockGradualAdmissionAndDeselectedActorsAreReleased()
    {
        WorldView world = stub(WorldView.class, values("isTopLevel", true));
        Player local = stub(Player.class, values("getWorldView", world, "getLocalLocation", new LocalPoint(128, 128, 0)));
        NPC first = npc(world, "First", 256), second = npc(world, "Second", 384);
        NearestActors nearest = new NearestActors();
        nearest.select(local, Arrays.asList(first, second), null, false, false, 2);
        ActorMaskAdmission admission = new ActorMaskAdmission();
        admission.beginFrame(nearest);
        assertTrue(admission.canRequest(first)); assertFalse(admission.canRequest(second));
        admission.beginFrame(nearest); // First was hidden/unavailable, so try the next candidate.
        assertFalse(admission.canRequest(first)); assertTrue(admission.canRequest(second));
        admission.modelAvailable(second); admission.beginFrame(nearest);
        assertTrue(admission.canRequest(first)); assertTrue(admission.canRequest(second));
        nearest.setExcludedNpcs("Second"); nearest.select(local, Arrays.asList(first, second), null, false, false, 2);
        admission.beginFrame(nearest);
        assertFalse(admission.canRequest(second));
    }

    private static NPC npc(WorldView world, String name, int x)
    {
        return stub(NPC.class, values("getWorldView", world, "getLocalLocation", new LocalPoint(x, 128, 0), "getName", name));
    }
}

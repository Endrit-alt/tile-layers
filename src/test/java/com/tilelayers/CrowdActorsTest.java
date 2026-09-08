package com.tilelayers;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.*;
import org.junit.Test;
import static com.tilelayers.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class CrowdActorsTest
{
    @Test
    public void stopsAtTheThresholdWithoutQueryingActorsOrTraversingMoreWorlds()
    {
        Player local = stub(Player.class, values());
        Player other = stub(Player.class, values());
        NPC npc = stub(NPC.class, values());
        AtomicInteger playersRead = new AtomicInteger(), npcsRead = new AtomicInteger();
        // Unconfigured actor methods and worldViews() throw if called. Once
        // the combined threshold is reached, even world traversal must stop.
        WorldView world = stub(WorldView.class, values(
                "players", repeated(other, 2000, playersRead), "npcs", repeated(npc, 2000, npcsRead)));
        CrowdActors crowd = new CrowdActors();
        crowd.collect(world, local, 50, true, true);
        assertEquals(50, playersRead.get()); assertEquals(0, npcsRead.get());
        assertFalse(crowd.allowed());
        assertEquals(0, crowd.playerCount()); assertEquals(0, crowd.npcCount());
        crowd.collect(world, local, 0, true, true);
        crowd.collect(world, local, 80, false, false);
        assertEquals("Zero cutoff and self-only mode do not enumerate actors", 50, playersRead.get());
        assertEquals(0, npcsRead.get());
    }

    @Test
    public void stopsAfterEightyCombinedCharactersInAMixedCrowd()
    {
        Player local = stub(Player.class, values()), other = stub(Player.class, values());
        NPC npc = stub(NPC.class, values());
        AtomicInteger playersRead = new AtomicInteger(), npcsRead = new AtomicInteger();
        WorldView world = stub(WorldView.class, values(
                "players", repeated(other, 79, playersRead), "npcs", repeated(npc, 79, npcsRead)));
        CrowdActors crowd = new CrowdActors();
        crowd.collect(world, local, 80, true, true);
        assertEquals(79, playersRead.get()); assertEquals(1, npcsRead.get());
        assertFalse(crowd.allowed());
        assertEquals(0, crowd.playerCount()); assertEquals(0, crowd.npcCount());
    }

    @Test
    public void disabledCategoriesStillContributeToTheCombinedCount()
    {
        Player local = stub(Player.class, values()), other = stub(Player.class, values());
        NPC npc = stub(NPC.class, values());
        WorldView world = stub(WorldView.class, values(
                "players", indexed(Arrays.asList(null, local, other)),
                "npcs", indexed(Arrays.asList(null, npc)), "worldViews", indexed(Collections.emptyList())));
        CrowdActors crowd = new CrowdActors();
        for (boolean includePlayers : new boolean[]{false, true})
        {
            boolean includeNpcs = !includePlayers;
            crowd.collect(world, local, 2, includePlayers, includeNpcs);
            assertFalse(crowd.allowed());
            assertEquals(0, crowd.playerCount()); assertEquals(0, crowd.npcCount());
            crowd.collect(world, local, 3, includePlayers, includeNpcs);
            assertTrue(crowd.allowed());
            assertEquals(includePlayers ? 1 : 0, crowd.playerCount());
            assertEquals(includeNpcs ? 1 : 0, crowd.npcCount());
        }
    }

    @Test
    public void countsAcrossChildWorldsAndExcludesLocalPlayer()
    {
        Player local = stub(Player.class, values()), other = stub(Player.class, values());
        NPC npc = stub(NPC.class, values());
        Map<String, Object> childData = values("players", repeated(other, 1, new AtomicInteger()),
                "npcs", repeated(npc, 0, new AtomicInteger()), "worldViews", indexed(Collections.emptyList()));
        WorldView child = stub(WorldView.class, childData);
        List<Player> players = new ArrayList<>(Collections.nCopies(48, other));
        players.add(local);
        WorldView top = stub(WorldView.class, values("players", indexed(players), "npcs", indexed(Collections.singletonList(npc)),
                "worldViews", indexed(Collections.singletonList(child))));
        CrowdActors crowd = new CrowdActors();
        crowd.collect(top, local, 50, true, true);
        assertFalse(crowd.allowed());
        assertEquals(0, crowd.playerCount()); assertEquals(0, crowd.npcCount());
        childData.put("players", indexed(Collections.emptyList()));
        crowd.collect(top, local, 50, true, true);
        assertTrue(crowd.allowed());
        assertEquals(48, crowd.playerCount()); assertEquals(1, crowd.npcCount());
    }

    private static <T> IndexedObjectSet<T> repeated(T item, int count, AtomicInteger reads)
    {
        return new IndexedObjectSet<T>()
        {
            @Override public T byIndex(int index) { return item; }
            @Override public Iterator<T> iterator()
            {
                return new Iterator<T>()
                {
                    int remaining = count;
                    @Override public boolean hasNext() { return remaining > 0; }
                    @Override public T next() { remaining--; reads.incrementAndGet(); return item; }
                };
            }
        };
    }

    private static <T> IndexedObjectSet<T> indexed(List<T> items)
    {
        return new IndexedObjectSet<T>()
        {
            @Override public T byIndex(int index) { return items.get(index); }
            @Override public Iterator<T> iterator() { return items.iterator(); }
        };
    }
}

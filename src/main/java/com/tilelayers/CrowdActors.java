package com.tilelayers;

import java.util.Arrays;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

/** Stops collecting both categories as soon as their combined cutoff is reached. */
final class CrowdActors
{
    static final int MAX_LIMIT = 500;
    private final Group players = new Group();
    private final Group npcs = new Group();
    private int count;
    private int limit;
    private boolean paused;
    private boolean includePlayers;
    private boolean includeNpcs;

    void collect(WorldView world, Player local, int requestedLimit, boolean includePlayers, boolean includeNpcs)
    {
        clear();
        count = 0;
        limit = Math.max(0, Math.min(MAX_LIMIT, requestedLimit));
        paused = limit == 0 || (!includePlayers && !includeNpcs);
        this.includePlayers = includePlayers;
        this.includeNpcs = includeNpcs;
        collectWorld(world, local);
    }

    private void collectWorld(WorldView world, Player local)
    {
        if (world == null || paused) return;
        for (Player player : world.players())
        {
            if (player == null || player == local) continue;
            if (!countActor()) return;
            if (includePlayers) players.add(player);
        }
        for (NPC npc : world.npcs())
        {
            if (npc == null) continue;
            if (!countActor()) return;
            if (includeNpcs) npcs.add(npc);
        }
        for (WorldView child : world.worldViews())
        {
            collectWorld(child, local);
            if (paused) return;
        }
    }

    private boolean countActor()
    {
        if (++count < limit) return true;
        // Count all loaded characters, even if their category is disabled.
        // Discard both groups before any model or height work is performed.
        clear();
        paused = true;
        return false;
    }

    boolean allowed() { return !paused; }
    int playerCount() { return players.size; }
    int npcCount() { return npcs.size; }
    Actor player(int index) { return players.actors[index]; }
    Actor npc(int index) { return npcs.actors[index]; }
    void clear() { players.clear(); npcs.clear(); }

    private static final class Group
    {
        final Actor[] actors = new Actor[MAX_LIMIT];
        int size;

        void add(Actor actor)
        {
            actors[size++] = actor;
        }

        void clear()
        {
            Arrays.fill(actors, 0, size, null);
            size = 0;
        }
    }
}

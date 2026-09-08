package com.tilelayers;

import java.util.Arrays;
import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.WorldView;

/** Stops collecting a category as soon as its crowd cutoff is reached. */
final class CrowdActors
{
    static final int MAX_LIMIT = 500;
    private final Group players = new Group();
    private final Group npcs = new Group();

    void collect(WorldView world, Player local, int playerLimit, int npcLimit)
    {
        players.begin(playerLimit);
        npcs.begin(npcLimit);
        collectWorld(world, local);
    }

    private void collectWorld(WorldView world, Player local)
    {
        if (world == null || (players.paused && npcs.paused)) return;
        if (!players.paused)
        {
            for (Player player : world.players())
            {
                if (player == null || player == local) continue;
                players.add(player);
                if (players.paused) break;
            }
        }
        if (!npcs.paused)
        {
            for (NPC npc : world.npcs())
            {
                if (npc == null) continue;
                npcs.add(npc);
                if (npcs.paused) break;
            }
        }
        if (players.paused && npcs.paused) return;
        for (WorldView child : world.worldViews())
        {
            collectWorld(child, local);
            if (players.paused && npcs.paused) break;
        }
    }

    boolean playersAllowed() { return !players.paused; }
    boolean npcsAllowed() { return !npcs.paused; }
    int playerCount() { return players.size; }
    int npcCount() { return npcs.size; }
    Actor player(int index) { return players.actors[index]; }
    Actor npc(int index) { return npcs.actors[index]; }
    void clear() { players.clear(); npcs.clear(); }

    private static final class Group
    {
        final Actor[] actors = new Actor[MAX_LIMIT];
        int size;
        int limit;
        boolean paused;

        void begin(int requestedLimit)
        {
            clear();
            limit = Math.max(0, Math.min(MAX_LIMIT, requestedLimit));
            paused = limit == 0;
        }

        void add(Actor actor)
        {
            if (size + 1 >= limit)
            {
                // Discard the whole category, including actors collected from
                // earlier worlds. No actor model is touched while counting.
                clear();
                paused = true;
            }
            else actors[size++] = actor;
        }

        void clear()
        {
            Arrays.fill(actors, 0, size, null);
            size = 0;
        }
    }
}

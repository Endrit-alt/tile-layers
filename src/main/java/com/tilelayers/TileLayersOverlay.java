/*
 * Copyright (c) 2021, LeikvollE
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tilelayers;

import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;

import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TileLayersOverlay extends Overlay {
    private final Client client;
    private final TileLayersConfig config;
    private final ActorOverlayMask actorMask;
    private final CrowdActors crowdActors = new CrowdActors();

    @Inject
    private TileLayersPlugin plugin;

    @Inject
    private RenderedActors renderedActors;

    @Inject
    private TileLayersOverlay(Client client, TileLayersConfig config)
    {
        this.client = client;
        this.config = config;
        this.actorMask = new ActorOverlayMask(client);
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
        setPriority(0.6f);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {

        final Player player = client.getLocalPlayer();
        if (player == null)
        {
            return null;
        }
        final LocalPoint playerPosLocal = player.getLocalLocation();
        if (playerPosLocal == null)
        {
            return null;
        }

        if (client.isGpu())
        {
            int opacity = Math.max(0, Math.min(100, config.overlayOpacity()));
            if (opacity == 100) return null;
            boolean belowPlayer = config.overlaysBelowPlayer();
            boolean belowOtherPlayers = config.overlaysBelowOtherPlayers();
            boolean belowAllNpcs = config.overlaysBelowAllNPCs();
            boolean belowNamedNpcs = !belowAllNpcs && config.overlaysBelowNPCs()
                    && !plugin.getOnTopNpcs().isEmpty();
            boolean belowNpcs = belowAllNpcs || belowNamedNpcs;
            if (!belowPlayer && !belowOtherPlayers && !belowNpcs) return null;
            int crowdLimit = config.crowdLimit();
            crowdActors.collect(client.getTopLevelWorldView(), player, crowdLimit, belowOtherPlayers, belowNpcs);
            try
            {
                if (!belowPlayer && !crowdActors.allowed()) return null;
                actorMask.beginFrame(graphics);
                if (!actorMask.hasOverlay()) return null;
                // One union mask for every enabled actor category: overlapping
                // players and NPCs attenuate an overlay pixel only once.
                actorMask.beginPass(graphics, opacity);
                if (belowPlayer) maskActor(player);
                for (int i = 0; i < crowdActors.playerCount(); i++) maskActor(crowdActors.player(i));
                for (int i = 0; i < crowdActors.npcCount(); i++)
                {
                    Actor npc = crowdActors.npc(i);
                    if (belowAllNpcs || plugin.getOnTopNpcs().contains(npc)) maskActor(npc);
                }
                actorMask.endPass(graphics);
            }
            finally
            {
                crowdActors.clear();
            }
        }
        return null;
    }

    private void maskActor(Actor actor)
    {
        if (!renderedActors.contains(actor)) return;
        final int localZ = ActorHeight.get(client, actor);
        if (localZ != ActorHeight.UNAVAILABLE) actorMask.addActor(actor, localZ);
    }

}

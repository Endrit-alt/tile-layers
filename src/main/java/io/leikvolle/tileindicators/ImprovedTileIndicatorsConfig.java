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
package io.leikvolle.tileindicators;

import net.runelite.client.config.*;

@ConfigGroup("improvedtileindicators")
public interface ImprovedTileIndicatorsConfig extends Config
{

	@ConfigSection(
			name = "Overlay settings",
			description = "Settings for drawing overlays around players and NPCs",
			position = 0
	)
	String tileIndicatorsSection = "tileIndicatorsSection";

	@ConfigItem(
			keyName = "currentTileBelowPlayer",
			name = "Draw overlays below player",
			description = "Requires GPU. Draws overlays below the player",
			section = tileIndicatorsSection,
			position = 1
	)
	default boolean overlaysBelowPlayer()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowAllNPCs",
			name = "Draw overlays below all NPCs",
			description = "Requires GPU. Draws overlays below all NPCs, regardless of the name list. Pauses when the loaded NPC count reaches the population cutoff.",
			section = tileIndicatorsSection,
			position = 3
	)
	default boolean overlaysBelowAllNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowOtherPlayers",
			name = "Draw overlays below other players",
			description = "Requires GPU. Draws overlays below other players. Pauses when their count reaches the configured population cutoff. Your own player is controlled separately.",
			section = tileIndicatorsSection,
			position = 2
	)
	default boolean overlaysBelowOtherPlayers()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowNPCs",
			name = "Draw overlays below named NPCs",
			description = "Requires GPU. Draws overlays below NPCs matching the names below. Works with Draw overlays below all NPCs turned off and uses the same population cutoff. A blank name list affects no NPCs.",
			section = tileIndicatorsSection,
			position = 4
	)
	default boolean overlaysBelowNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "NPC names",
			description = "Comma-separated names for Draw overlays below named NPCs; * wildcards are supported. Blank affects no NPCs in named mode. Shift-right-click an NPC to add or remove its name. The all-NPC checkbox ignores this list.",
			section = tileIndicatorsSection,
			position = 5
	)
	default String getTopNPCs()
	{
		return "";
	}

	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	// Keep the stored key so existing opacity preferences carry over.
	@ConfigItem(
			keyName = "npcOverlayOpacity",
			name = "Overlay opacity",
			description = "How much of an overlay remains visible over characters enabled above: 0% hides it, 100% keeps its original opacity. Applies to your player, other players, and all or selected NPCs.",
			section = tileIndicatorsSection,
			position = 6
	)
	default int overlayOpacity()
	{
		return 10;
	}

	@Range(min = 0, max = 500)
	@ConfigItem(
			keyName = "crowdLimit",
			name = "Crowd cutoff",
			description = "Shared cutoff for other players and NPCs, counted separately. Each category pauses at this count or higher and resumes below it, including NPCs selected by name. Your own player is unaffected. 0 disables both crowd effects.",
			section = tileIndicatorsSection,
			position = 7
	)
	default int crowdLimit()
	{
		return 80;
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "",
			description = ""
	)
	void setTopNPCs(String npcsToDrawAbove);
}

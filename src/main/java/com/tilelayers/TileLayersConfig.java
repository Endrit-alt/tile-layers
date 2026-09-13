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

import net.runelite.client.config.*;

@ConfigGroup("improvedtileindicators")
public interface TileLayersConfig extends Config
{
	@Range(min = 0, max = 100)
	@Units(Units.PERCENT)
	// Keep the stored key so existing opacity preferences carry over.
	@ConfigItem(
			keyName = "npcOverlayOpacity",
			name = "Overlay opacity",
			description = "How much of an overlay remains visible over characters enabled below: 0% hides it, 100% keeps its original opacity. Applies to your player, other players, and all or selected NPCs.",
			position = 0
	)
	default int overlayOpacity()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "currentTileBelowPlayer",
			name = "Draw overlays below self",
			description = "Requires GPU. Draws overlays below the player",
			position = 1
	)
	default boolean overlaysBelowPlayer()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowAllNPCs",
			name = "Draw overlays below all NPCs",
			description = "Requires GPU. Includes all nearby NPCs, sharing the Character limit with other players. Listed NPCs, your opponents, known bosses and other NPCs in combat take priority.",
			position = 3
	)
	default boolean overlaysBelowAllNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowOtherPlayers",
			name = "Draw overlays below other players",
			description = "Requires GPU. Draws overlays below nearby other players, sharing the Character limit with NPCs. Your own player is controlled separately.",
			position = 2
	)
	default boolean overlaysBelowOtherPlayers()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowNPCs",
			name = "Draw overlays below named NPCs",
			description = "Requires GPU. Draws overlays below the named NPCs and gives them first priority within the shared Character limit, including when all NPCs are enabled. A blank name list affects no NPCs in named mode.",
			position = 4
	)
	default boolean overlaysBelowNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "NPC names",
			description = "Comma-separated NPC names given first priority when named NPCs are enabled; * wildcards are supported. Blank affects no NPCs in named mode. Shift-right-click an NPC to add or remove its name.",
			position = 5
	)
	default String getTopNPCs()
	{
		return "";
	}

	@ConfigItem(
			keyName = "keepLootAboveCharacters",
			name = "Loot above characters",
			description = "Keep Ground Items and Loot Filters overlays above players and NPCs, including their labels, timers, icons and item tile highlights. Other overlays still follow the character settings.",
			position = 8
	)
	default boolean keepLootAboveCharacters() { return true; }

    @ConfigItem(
            keyName = "onlyTileAndMarkerPlugins",
            name = "Only Tile and Marker plugins",
            description = "Only apply character masking to Ground Markers, Tile Indicators, Line Markers, Radius Markers, Brush Markers, Object Markers, Agility and Improved Tile Indicators' destination tiles. Other scene overlays stay above characters. When off, the normal overlay and loot settings apply.",
            position = 9
    )
    default boolean onlyTileAndMarkerPlugins() { return true; }

	@Range(min = 0, max = NearestActors.MAX_LIMIT)
	@ConfigItem(
			keyName = "characterLimit",
			name = "Character limit",
			description = "Maximum NPCs and other players affected together. Prioritizes listed NPCs, your opponents, known bosses, then other NPCs in combat; distance breaks ties. Your own character is separate. Set to 0 to disable both groups.",
			position = 7
	)
	default int characterLimit()
	{
		return 200;
	}

    @ConfigItem(
            keyName = "excludedBosses",
            name = "Excluded bosses",
            description = "Comma-separated boss names to keep overlays visible over. Exclusions override all NPCs, named NPCs and combat priority. Names ignore case; * wildcards are supported. Blank excludes nothing.",
            position = 6
    )
    default String excludedBosses() { return ""; }

	@ConfigItem(
			keyName = "topNPCs",
			name = "",
			description = ""
	)
	void setTopNPCs(String npcsToDrawAbove);
}

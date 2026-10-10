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
			description = "Visibility where overlays are covered: 0% hidden, 100% unchanged.",
			position = 0
	)
	default int overlayOpacity()
	{
		return 0;
	}

	@ConfigItem(
			keyName = "currentTileBelowPlayer",
			name = "Draw overlays below self",
			description = "Keep your character above overlays.",
			position = 1
	)
	default boolean overlaysBelowPlayer()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowAllNPCs",
			name = "Draw overlays below all NPCs",
			description = "Keep nearby NPCs above overlays, within the character limit.",
			position = 3
	)
	default boolean overlaysBelowAllNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowOtherPlayers",
			name = "Draw overlays below other players",
			description = "Keep nearby players above overlays, within the character limit.",
			position = 2
	)
	default boolean overlaysBelowOtherPlayers()
	{
		return true;
	}

	@ConfigItem(
			keyName = "overlaysBelowNPCs",
			name = "Draw overlays below named NPCs",
			description = "Keep listed NPCs above overlays and give them first priority.",
			position = 4
	)
	default boolean overlaysBelowNPCs()
	{
		return true;
	}

	@ConfigItem(
			keyName = "topNPCs",
			name = "NPC names",
			description = "Comma-separated names; * matches anything. Shift-right-click an NPC to edit.",
			position = 5
	)
	default String getTopNPCs()
	{
		return "";
	}

	@ConfigItem(
			// Preserve existing preferences under the original stored key.
			keyName = "keepLootAboveCharacters",
			name = "Keep labels and loot on top",
			description = "Keep labels and loot timers on top. Tile highlights stay underneath.",
			position = 8
	)
	default boolean keepLootAboveCharacters() { return true; }

    @ConfigItem(
            keyName = "onlyTileAndMarkerPlugins",
            name = "Only Tile and Marker plugins",
            description = "Only affect tile, marker and loot plugins, including Shortest Path.",
            position = 9
    )
    default boolean onlyTileAndMarkerPlugins() { return true; }

	@Range(min = 0, max = NearestActors.MAX_LIMIT)
	@ConfigItem(
			keyName = "characterLimit",
			name = "Character limit",
			description = "Maximum NPCs and other players affected. Excludes yourself; 0 disables both groups.",
			position = 7
	)
	default int characterLimit()
	{
		return 200;
	}

    @ConfigItem(
            keyName = "excludedBosses",
            name = "Excluded bosses",
            description = "Comma-separated NPC names to exclude, overriding other NPC settings. Supports *.",
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

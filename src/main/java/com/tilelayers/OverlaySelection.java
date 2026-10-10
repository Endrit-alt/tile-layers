package com.tilelayers;

/** Shared scene-overlay allowlist. Optional plugins remain optional dependencies. */
public final class OverlaySelection
{
    public static boolean selected(String name)
    {
        return name.startsWith("net.runelite.client.plugins.groundmarkers.")
            || name.startsWith("net.runelite.client.plugins.tileindicators.")
            || name.startsWith("com.linemarkers.") || name.startsWith("com.radiusmarkers.")
            || name.startsWith("com.bram91.brushmarkers.")
            || name.startsWith("net.runelite.client.plugins.objectindicators.")
            || name.startsWith("net.runelite.client.plugins.agility.")
            || name.startsWith("com.betternpchighlight.")
            || name.equals("net.runelite.client.plugins.grounditems.GroundItemsOverlay")
            || name.equals("com.lootfilters.LootFiltersOverlay")
            || name.equals("com.lootfilters.LootFiltersTileOverlay")
            || name.equals("com.lootfilters.LootFiltersTextOverlay")
            || name.equals("shortestpath.overlay.PathTileOverlay")
            || name.startsWith("net.runelite.client.plugins.npchighlight.")
            || name.equals("net.runelite.client.game.npcoverlay.NpcOverlay")
            || name.equals("io.leikvolle.tileindicators.ImprovedTileIndicatorsOverlay");
    }

    private OverlaySelection() { }
}

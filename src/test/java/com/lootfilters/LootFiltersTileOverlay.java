package com.lootfilters;

import java.awt.*;
import net.runelite.client.ui.overlay.*;

/** Test-only tile counterpart to LootFiltersTextOverlay; never shipped with Tile Layers. */
public class LootFiltersTileOverlay extends Overlay
{
    public int calls;

    public LootFiltersTileOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE); setPriority(PRIORITY_DEFAULT);
    }

    @Override public Dimension render(Graphics2D g) { calls++; drawTile(g); return null; }

    public static void drawTile(Graphics2D g)
    {
        Polygon tile = new Polygon(new int[]{10, 80, 150, 80}, new int[]{35, 8, 35, 61}, 4);
        g.setColor(new Color(255, 0, 0, 100)); g.fill(tile);
        g.setColor(Color.RED); g.draw(tile);
    }
}

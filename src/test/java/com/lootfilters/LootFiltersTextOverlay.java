package com.lootfilters;

import java.awt.*;
import java.awt.image.BufferedImage;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;

/** Test-only routing fixture for the public class name and priority in Loot Filters PR 218. */
public class LootFiltersTextOverlay extends Overlay
{
    public int calls;

    public LootFiltersTextOverlay()
    {
        setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE); setPriority(PRIORITY_HIGH);
    }

    @Override public Dimension render(Graphics2D g) { calls++; drawLabel(g); return null; }

    public static void drawLabel(Graphics2D graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setColor(new Color(30, 50, 70, 180)); g.fillRect(16, 8, 120, 30);
            g.setColor(Color.CYAN); g.drawRect(16, 8, 120, 30);
            BufferedImage icon = new BufferedImage(10, 10, BufferedImage.TYPE_INT_ARGB);
            icon.setRGB(2, 2, Color.MAGENTA.getRGB()); g.drawImage(icon, 20, 12, null);
            ProgressPieComponent pie = new ProgressPieComponent();
            pie.setPosition(new net.runelite.api.Point(8, 24)); pie.setDiameter(10);
            pie.setProgress(0.7); pie.setFill(Color.GREEN); pie.render(g);
            g.setColor(Color.WHITE); g.drawString("Loot - 30", 34, 30);
            // A later overlapping label's background must still cover earlier text.
            g.setColor(new Color(30, 70, 40, 190)); g.fillRect(55, 12, 50, 20);
            g.setColor(Color.YELLOW); g.drawString("Next", 57, 27);
        }
        finally { g.dispose(); }
    }
}

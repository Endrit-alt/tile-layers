package com.tilelayers.labels;

import com.tilelayers.TileLayersConfig;
import com.tilelayers.OverlaySelection;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import net.runelite.client.ui.overlay.components.TextComponent;
import net.runelite.client.ui.overlay.components.BackgroundComponent;
import net.runelite.client.ui.overlay.components.ProgressPieComponent;
import org.junit.Assume;
import org.junit.Test;
import static com.tilelayers.TestSupport.*;
import static org.junit.Assert.*;

public class LootOverlayRoutingTest
{
    private static final int W = 160, H = 64;

    @Test
    public void groundItemsTilesStayMaskedAcrossTextFilterAndBackendToggles() throws Exception
    {
        checkOverlay(Class.forName("net.runelite.client.plugins.grounditems.GroundItemsOverlay"));
    }

    @Test
    public void installedLootFiltersNeedsNoChangesToSeparateItsDraws() throws Exception
    {
        // Optional, read-only verification against the installed plugin. It is
        // never compiled into Tile Layers or added to its runtime dependencies.
        String jar = System.getenv("TILE_LAYERS_LOOT_FILTERS_JAR");
        Assume.assumeTrue("Set TILE_LAYERS_LOOT_FILTERS_JAR to verify the optional plugin", jar != null && Files.isRegularFile(java.nio.file.Paths.get(jar)));
        try (URLClassLoader loader = new URLClassLoader(new URL[]{java.nio.file.Paths.get(jar).toUri().toURL()}, getClass().getClassLoader()))
        {
            checkOverlay(Class.forName("com.lootfilters.LootFiltersOverlay", true, loader));
        }
    }

    private static void checkOverlay(Class<?> type) throws Exception
    {
        boolean fullLabels = type.getName().equals("net.runelite.client.plugins.grounditems.GroundItemsOverlay");
        Client client = stub(Client.class, values());
        OverlayManager manager = RoutingTestSupport.manager(client);
        Constructor<?> constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        Overlay original = (Overlay) constructor.newInstance(client, null, null);
        float priority = original.getPriority();
        manager.add(original);
        boolean[] keepText = {true}, onlyTiles = {true};
        TileLayersConfig config = new TileLayersConfig()
        {
            public void setTopNPCs(String value) { }
            public boolean keepLootAboveCharacters() { return keepText[0]; }
            public boolean onlyTileAndMarkerPlugins() { return onlyTiles[0]; }
        };
        OverlayLabelRouting labels = new OverlayLabelRouting(manager);
        // Exercise the real CPU scheduler too, without expanding production API.
        Class<?> orderClass = Class.forName("com.tilelayers.OverlayMaskOrder");
        Constructor<?> orderConstructor = orderClass.getDeclaredConstructor(); orderConstructor.setAccessible(true);
        Object order = orderConstructor.newInstance();
        Method update = orderClass.getDeclaredMethod("update", OverlayManager.class, boolean.class); update.setAccessible(true);
        Method restore = orderClass.getDeclaredMethod("restore"); restore.setAccessible(true);
        BufferedImage image = image(); Graphics2D g = image.createGraphics();
        try
        {
            labels.start(true);
            // Repeat both toggles without restarting the plugin.
            for (boolean filter : new boolean[]{true, false, true})
                    for (boolean text : new boolean[]{true, false, true, false})
                    {
                        keepText[0] = text; onlyTiles[0] = filter;
                        labels.configure(text); labels.frame();
                        update.invoke(order, manager, filter);
                        assertEquals("Original priority is never raised for loot", priority, original.getPriority(), 0f);
                        assertEquals("Only the text wrapper may delegate the original", text ? OverlayLayer.MANUAL : OverlayLayer.ABOVE_SCENE, original.getLayer());
                        assertFalse("Loot must never be replayed as a whole-overlay exception",
                            manager.anyMatch(o -> o.getName().equals("TileLayersUnmaskedOverlays")));
                        assertEquals(text, manager.anyMatch(o -> o.getName().startsWith("TileLayersLabelGraphics_")));
                        assertEquals(text, manager.anyMatch(o -> o.getName().equals("TileLayersUnmaskedLabels")));
                        Arrays.fill(pixels(image), 0);
                        // Both installed plugins use ordinary shapes and drawString
                        // (including shadows/outlines). Keep their live item state out
                        // of this fixture and exercise the shared route with those calls.
                        labels.render(original, g, graphics -> { tiles(graphics); decorations(graphics); text(graphics); return null; });
                        BufferedImage expectedEarly = image(); Graphics2D early = expectedEarly.createGraphics();
                        try { tiles(early); if (!text || !fullLabels) decorations(early); if (!text) text(early); } finally { early.dispose(); }
                        assertArrayEquals("Ground Items keeps the complete label out of masking", pixels(expectedEarly), pixels(image));
                        Arrays.fill(pixels(image), 0); // Fully blocking character mask.
                        manager.anyMatch(o -> {
                            if (o.getName().equals("TileLayersUnmaskedLabels")) o.render(g);
                            return false;
                        });
                        BufferedImage expectedLate = image(); Graphics2D late = expectedLate.createGraphics();
                        try { if (text) { if (fullLabels) decorations(late); text(late); } } finally { late.dispose(); }
                        assertArrayEquals("Labels follow every toggle; tile graphics never reappear above the mask", pixels(expectedLate), pixels(image));
                    }
        }
        finally
        {
            labels.stop(); restore.invoke(order); g.dispose();
        }
        assertEquals(OverlayLayer.ABOVE_SCENE, original.getLayer());
        assertEquals(priority, original.getPriority(), 0f);
    }

    private static void tiles(Graphics2D g)
    {
        Polygon tile = new Polygon(new int[]{10, 80, 150, 80}, new int[]{35, 8, 35, 61}, 4);
        g.setColor(new Color(255, 0, 0, 100)); g.fill(tile);
        g.setStroke(new BasicStroke(2)); g.setColor(Color.RED); g.draw(tile);
    }

    private static void decorations(Graphics2D graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.translate(1, 1); g.clipRect(0, 0, W - 2, H - 2);
            g.setStroke(new BasicStroke(2));
            g.setComposite(AlphaComposite.SrcOver.derive(0.8f));
            BackgroundComponent background = new BackgroundComponent();
            Rectangle bounds = new Rectangle(18, 14, 120, 22);
            background.setRectangle(bounds); background.render(g);
            bounds.setBounds(0, 0, 1, 1); // Deferred shapes must not retain mutable bounds.
            g.setColor(Color.RED); g.drawRect(140, 15, 8, 8);
            g.fill(new Rectangle(140, 15, 8, 8));
            g.setColor(Color.WHITE); g.drawLine(142, 19, 146, 19);
            ProgressPieComponent pie = new ProgressPieComponent();
            pie.setPosition(new net.runelite.api.Point(8, 24)); pie.setDiameter(10);
            pie.setProgress(0.7); pie.setFill(Color.GREEN); pie.render(g);
        }
        finally { g.dispose(); }
    }

    private static void text(Graphics2D g)
    {
        TextComponent label = new TextComponent();
        label.setText("Loot (5) - 30"); label.setColor(Color.WHITE); label.setOutline(true);
        label.setPosition(20, 30); label.render(g);
    }

    private static BufferedImage image() { return new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB_PRE); }
    private static int[] pixels(BufferedImage image) { return ((DataBufferInt) image.getRaster().getDataBuffer()).getData(); }
}

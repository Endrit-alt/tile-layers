package com.tilelayers.labels;

import com.lootfilters.LootFiltersTextOverlay;
import com.lootfilters.LootFiltersTileOverlay;
import com.tilelayers.TileLayersConfig;
import com.tilelayers.OverlaySelection;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import org.junit.Test;
import static com.tilelayers.TestSupport.*;
import static org.junit.Assert.*;

public class SplitLootOverlayTest
{
    private static final int W = 160, H = 64;

    @Test
    public void completeLabelsFollowCheckboxAcrossFiltersAndBackends() throws Exception
    {
        Client client = stub(Client.class, values());
        OverlayManager manager = RoutingTestSupport.manager(client);
        LootFiltersTileOverlay tiles = new LootFiltersTileOverlay();
        LootFiltersTextOverlay labels = new LootFiltersTextOverlay();
        manager.add(tiles); manager.add(labels);
        boolean[] keepLabels = {true}, onlyTiles = {true};
        TileLayersConfig config = new TileLayersConfig()
        {
            public void setTopNPCs(String value) { }
            public boolean keepLootAboveCharacters() { return keepLabels[0]; }
            public boolean onlyTileAndMarkerPlugins() { return onlyTiles[0]; }
        };
        OverlayLabelRouting routing = new OverlayLabelRouting(manager);
        Class<?> orderClass = Class.forName("com.tilelayers.OverlayMaskOrder");
        Constructor<?> ctor = orderClass.getDeclaredConstructor(); ctor.setAccessible(true);
        Object order = ctor.newInstance();
        Method update = orderClass.getDeclaredMethod("update", OverlayManager.class, boolean.class); update.setAccessible(true);
        Method restore = orderClass.getDeclaredMethod("restore"); restore.setAccessible(true);
        Method getLayer = OverlayManager.class.getDeclaredMethod("getLayer", OverlayLayer.class); getLayer.setAccessible(true);
        BufferedImage image = image(); Graphics2D g = image.createGraphics();
        try
        {
            routing.start(true);
            for (boolean filter : new boolean[]{true, false, true})
                    for (boolean keep : new boolean[]{true, false, true})
                    {
                        keepLabels[0] = keep; onlyTiles[0] = filter;
                        routing.configure(keep); routing.frame();
                        update.invoke(order, manager, filter);
                        assertEquals(OverlayLayer.ABOVE_SCENE, tiles.getLayer());
                        assertEquals(keep ? OverlayLayer.MANUAL : OverlayLayer.ABOVE_SCENE, labels.getLayer());
                        assertEquals(Overlay.PRIORITY_DEFAULT, tiles.getPriority(), 0f);
                        assertEquals(Overlay.PRIORITY_HIGH, labels.getPriority(), 0f);
                        assertFalse(manager.anyMatch(o -> o.getName().equals("TileLayersUnmaskedOverlays")));
                        @SuppressWarnings("unchecked")
                        Collection<Overlay> scene = (Collection<Overlay>) getLayer.invoke(manager, OverlayLayer.ABOVE_SCENE);
                        ArrayList<Overlay> draw = new ArrayList<>(scene);
                        int tileCalls = tiles.calls, labelCalls = labels.calls;
                        Arrays.fill(pixels(image), 0);
                        for (Overlay o : draw) if (o.getPriority() < Float.MAX_VALUE) o.render(g);
                        assertEquals(tileCalls + 1, tiles.calls);
                        assertEquals(labelCalls + (keep ? 0 : 1), labels.calls);
                        BufferedImage early = image(); Graphics2D eg = early.createGraphics();
                        try { LootFiltersTileOverlay.drawTile(eg); if (!keep) LootFiltersTextOverlay.drawLabel(eg); }
                        finally { eg.dispose(); }
                        assertArrayEquals("Tiles always mask; complete labels mask only when unchecked", pixels(early), pixels(image));
                        Arrays.fill(pixels(image), 0); // Fully blocking character mask.
                        for (Overlay o : draw) if (o.getPriority() == Float.MAX_VALUE) o.render(g);
                        BufferedImage late = image(); Graphics2D lg = late.createGraphics();
                        try { if (keep) LootFiltersTextOverlay.drawLabel(lg); } finally { lg.dispose(); }
                        assertArrayEquals("Icons, backgrounds, pies and text survive together in the original order", pixels(late), pixels(image));
                        assertEquals("Original label renderer runs once", labelCalls + 1, labels.calls);
                    }
            Overlay route = findRoute(manager);
            labels.setPriority(0.9f); routing.frame();
            assertEquals(Float.MAX_VALUE, route.getPriority(), 0f);
            manager.remove(labels); routing.frame();
            int calls = labels.calls; route.render(g);
            assertEquals("Removed label overlay cannot render through a stale route", calls, labels.calls);
            assertEquals(OverlayLayer.ABOVE_SCENE, labels.getLayer());
            manager.add(labels); routing.frame(); route = findRoute(manager);
            routing.configure(false); route.render(g);
            assertEquals("Disabled label route cannot render from a stale layer snapshot", calls, labels.calls);
            routing.configure(true); labels.setLayer(OverlayLayer.ABOVE_WIDGETS); routing.frame();
            assertEquals(OverlayLayer.ABOVE_WIDGETS, labels.getLayer());
        }
        finally { routing.stop(); restore.invoke(order); g.dispose(); }
        assertEquals(OverlayLayer.ABOVE_SCENE, tiles.getLayer());
        assertEquals(0.9f, labels.getPriority(), 0f);
    }

    private static Overlay findRoute(OverlayManager manager)
    {
        Overlay[] found = {null};
        manager.anyMatch(o -> { if (o.getName().startsWith("TileLayersLabelGraphics_")) found[0] = o; return false; });
        assertNotNull(found[0]); return found[0];
    }

    private static BufferedImage image() { return new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB_PRE); }
    private static int[] pixels(BufferedImage image) { return ((DataBufferInt) image.getRaster().getDataBuffer()).getData(); }
}

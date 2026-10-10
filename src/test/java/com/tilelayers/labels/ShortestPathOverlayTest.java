package com.tilelayers.labels;

import com.tilelayers.TileLayersConfig;
import com.tilelayers.OverlaySelection;
import java.awt.*;
import java.awt.font.GlyphVector;
import java.awt.font.TextAttribute;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.text.AttributedString;
import java.util.ArrayList;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.*;
import org.junit.Test;
import static com.tilelayers.TestSupport.*;
import static org.junit.Assert.*;

public class ShortestPathOverlayTest
{
    private static final int W = 400, H = 140;

    @Test
    public void pathGraphicsAreMaskedWhileDirectionsStayAbove() throws Exception
    {
        assertTrue(OverlaySelection.selected("shortestpath.overlay.PathTileOverlay"));
        assertFalse(OverlaySelection.selected("shortestpath.overlay.PathMinimapOverlay"));
        assertFalse(OverlaySelection.selected("shortestpath.overlay.PathMapOverlay"));
        checkPixels();
    }

    private static void checkPixels() throws Exception
    {
        Client client = stub(Client.class, values());
        OverlayManager manager = RoutingTestSupport.manager(client);
        Fixture original = new Fixture();
        manager.add(original);
        OverlayLabelRouting paths = new OverlayLabelRouting(manager, o -> o == original);
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        try
        {
            paths.start(true); paths.frame();
            assertEquals(OverlayLayer.MANUAL, original.getLayer());
            Overlay geometry = find(manager, "TileLayersLabelGraphics_");
            Overlay labels = find(manager, "TileLayersUnmaskedLabels");
            assertEquals(0f, geometry.getPriority(), 0f);
            assertTrue(labels.getPriority() > Overlay.PRIORITY_HIGHEST + 1f);
            int[] pixels = pixels(image);
            geometry.render(graphics);
            assertEquals("Original plugin renders only once", 1, original.calls);
            BufferedImage expectedGeometry = image();
            Graphics2D expected = expectedGeometry.createGraphics();
            try { shapes(expected); } finally { expected.dispose(); }
            assertArrayEquals("Shapes and sprite markers draw immediately; text is absent", pixels(expectedGeometry), pixels);
            // Simulate a fully blocking character mask before the late text pass.
            graphics.setComposite(AlphaComposite.Clear); graphics.fillRect(0, 0, W, H);
            graphics.setComposite(AlphaComposite.SrcOver);
            graphics.setColor(Color.MAGENTA); graphics.translate(100, 0); graphics.setClip(0, 0, 1, 1);
            labels.render(graphics);
            BufferedImage expectedText = image();
            expected = expectedText.createGraphics();
            try { text(expected); } finally { expected.dispose(); }
            assertArrayEquals("Directions retain their original graphics state and shadows", pixels(expectedText), pixels);
            labels.render(graphics);
            assertArrayEquals("No duplicate alpha or text on a repeated late pass", pixels(expectedText), pixels);
            assertEquals(1, original.calls);
            paths.stop();
            assertEquals(OverlayLayer.ABOVE_SCENE, original.getLayer());
            assertEquals(0f, original.getPriority(), 0f);
        }
        finally { paths.stop(); graphics.dispose(); }
    }

    @Test
    public void lifecycleDropsStaleTextAndRespectsOverlayOwnerChanges() throws Exception
    {
        Client client = stub(Client.class, values());
        OverlayManager manager = RoutingTestSupport.manager(client);
        Fixture original = new Fixture();
        OverlayLabelRouting paths = new OverlayLabelRouting(manager, o -> o == original);
        BufferedImage image = image();
        Graphics2D graphics = image.createGraphics();
        try
        {
            paths.start(true); manager.add(original); paths.frame();
            Overlay geometry = find(manager, "TileLayersLabelGraphics_");
            Overlay labels = find(manager, "TileLayersUnmaskedLabels");
            geometry.render(graphics);
            clear(image);
            paths.frame(); labels.render(graphics);
            assertArrayEquals("A skipped late pass must not leak into the next frame", new int[W * H], pixels(image));
            original.setPriority(2f); paths.frame();
            assertEquals(2f, geometry.getPriority(), 0f);
            geometry.render(graphics); clear(image); manager.remove(original);
            labels.render(graphics);
            assertArrayEquals("Removed plugin labels are discarded", new int[W * H], pixels(image));
            paths.frame();
            assertEquals(OverlayLayer.ABOVE_SCENE, original.getLayer());
            manager.add(original); paths.frame();
            geometry = find(manager, "TileLayersLabelGraphics_");
            original.fail = true;
            try { geometry.render(graphics); fail("Expected fixture render failure"); }
            catch (IllegalStateException expected) { }
            clear(image); find(manager, "TileLayersUnmaskedLabels").render(graphics);
            assertArrayEquals("Failed renders leave no queued text", new int[W * H], pixels(image));
            original.setLayer(OverlayLayer.ABOVE_WIDGETS); paths.frame(); paths.stop();
            assertEquals("Do not overwrite a layer changed by its owner", OverlayLayer.ABOVE_WIDGETS, original.getLayer());
            assertEquals(2f, original.getPriority(), 0f);
        }
        finally { paths.stop(); graphics.dispose(); }
    }

    private static Overlay find(OverlayManager manager, String prefix)
    {
        ArrayList<Overlay> found = new ArrayList<>();
        manager.anyMatch(o -> { if (o.getName().startsWith(prefix)) found.add(o); return false; });
        assertEquals(prefix, 1, found.size());
        return found.get(0);
    }

    private static final class Fixture extends Overlay
    {
        int calls;
        boolean fail;
        Fixture() { setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE); setPriority(0f); }
        @Override public Dimension render(Graphics2D graphics)
        {
            calls++; shapes(graphics); text(graphics);
            if (fail) throw new IllegalStateException("test render failure");
            return null;
        }
    }

    private static void shapes(Graphics2D g)
    {
        g.setColor(new Color(255, 0, 0, 100)); g.fillPolygon(new int[]{2, 58, 53, 8}, new int[]{3, 3, 60, 60}, 4);
        g.setStroke(new BasicStroke(3)); g.drawLine(70, 5, 70, 60);
        Path2D line = new Path2D.Double(); line.moveTo(90, 5); line.lineTo(95, 60); g.draw(line);
        g.fill(new Polygon(new int[]{90, 100, 95}, new int[]{15, 15, 5}, 3));
        g.drawOval(105, 5, 12, 12); g.fillOval(125, 5, 8, 8);
        BufferedImage sprite = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        sprite.setRGB(3, 3, Color.GREEN.getRGB()); g.drawImage(sprite, 140, 5, null);
    }

    private static void text(Graphics2D graphics)
    {
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.translate(3, 2); g.clipRect(0, 0, W - 9, H - 5);
            g.setFont(new Font(Font.DIALOG, Font.BOLD, 12));
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setComposite(AlphaComposite.SrcOver.derive(.8f));
            g.setColor(Color.BLACK); g.drawString("Max cape: Farming Guild", 11, 26);
            g.setColor(Color.WHITE); g.drawString("Max cape: Farming Guild", 10, 25);
            g.drawString("Direction", 10.5f, 44.25f);
            AttributedString label = new AttributedString("Transport"); label.addAttribute(TextAttribute.FONT, g.getFont());
            g.drawString(label.getIterator(), 10, 62);
            GlyphVector glyphs = g.getFont().createGlyphVector(g.getFontRenderContext(), "Glyphs");
            g.drawGlyphVector(glyphs, 10, 80);
            glyphs.setGlyphPosition(0, new java.awt.geom.Point2D.Float(100, 100));
            char[] chars = "Chars".toCharArray(); g.drawChars(chars, 0, chars.length, 10, 99); chars[0] = 'X';
            byte[] bytes = {66, 121, 116, 101, 115}; g.drawBytes(bytes, 0, bytes.length, 10, 118); bytes[0] = 88;
        }
        finally { g.dispose(); }
    }

    private static BufferedImage image() { return new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB_PRE); }
    private static int[] pixels(BufferedImage image) { return ((DataBufferInt) image.getRaster().getDataBuffer()).getData(); }
    private static void clear(BufferedImage image) { java.util.Arrays.fill(pixels(image), 0); }
}

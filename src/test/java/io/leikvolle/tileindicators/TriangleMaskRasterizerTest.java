package io.leikvolle.tileindicators;

import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Random;
import net.runelite.api.MainBufferProvider;
import org.junit.Test;
import static io.leikvolle.tileindicators.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class TriangleMaskRasterizerTest
{
    @Test
    public void randomizedTrianglesMatchJava2DIncludingClippedAndThinEdges()
    {
        BufferedImage actual = new BufferedImage(67, 65, BufferedImage.TYPE_INT_ARGB);
        BufferedImage expected = new BufferedImage(67, 65, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = actual.createGraphics();
        Graphics2D reference = expected.createGraphics();
        reference.setComposite(AlphaComposite.Clear);
        reference.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        MainBufferProvider buffer = buffer(actual);
        TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
        Random random = new Random(35782);
        for (int i = 0; i < 2000; i++)
        {
            Arrays.fill(pixels(actual), 0x80402010);
            Arrays.fill(pixels(expected), 0x80402010);
            int[] x = {random.nextInt(110) - 20, random.nextInt(110) - 20, random.nextInt(110) - 20};
            int[] y = {random.nextInt(110) - 20, random.nextInt(110) - 20, random.nextInt(110) - 20};
            rasterizer.begin(graphics, buffer, 67, 65, 0);
            rasterizer.triangle(x[0], y[0], x[1], y[1], x[2], y[2]);
            rasterizer.apply(graphics);
            reference.fill(new Polygon(x, y, 3));
            assertArrayEquals("triangle " + i + ": " + Arrays.toString(x) + " / " + Arrays.toString(y), pixels(expected), pixels(actual));
        }
        graphics.dispose(); reference.dispose();
    }

    @Test
    public void opacityIsAppliedOnceAcrossOverlappingTriangles()
    {
        for (int type : new int[]{BufferedImage.TYPE_INT_ARGB, BufferedImage.TYPE_INT_ARGB_PRE})
        {
            BufferedImage image = new BufferedImage(64, 64, type);
            Graphics2D graphics = image.createGraphics();
            TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
            for (int opacity : new int[]{0, 25, 50, 75, 100})
            {
                Arrays.fill(pixels(image), 0x80402010);
                rasterizer.begin(graphics, buffer(image), 64, 64, opacity);
                for (int i = 0; i < 20; i++) rasterizer.triangle(10, 10, 10, 50, 50, 10);
                rasterizer.apply(graphics);
                int alpha = (128 * opacity + 50) / 100;
                int expected = (alpha << 24) | 0x402010;
                if (type == BufferedImage.TYPE_INT_ARGB_PRE)
                {
                    expected = (alpha << 24) | (((64 * opacity + 50) / 100) << 16)
                            | (((32 * opacity + 50) / 100) << 8) | ((16 * opacity + 50) / 100);
                }
                if (opacity == 0) expected = 0;
                assertEquals(expected, pixels(image)[20 * 64 + 20]);
                assertEquals(0x80402010, pixels(image)[60 * 64 + 60]);
            }
            graphics.dispose();
        }
    }

    @Test
    public void gpuCompositeFadesSmoothlyWhenImageReportsStraightAlpha()
    {
        // Matches the live RuneLite buffer: TYPE_INT_ARGB_PRE, but false
        // isAlphaPremultiplied(). The GPU still blends with GL_ONE.
        BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB_PRE);
        image.coerceData(false);
        assertEquals(BufferedImage.TYPE_INT_ARGB_PRE, image.getType());
        assertFalse(image.isAlphaPremultiplied());
        Graphics2D graphics = image.createGraphics();
        TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
        int originalOverlay = 0xc0804020;
        int scene = 0x1f2f3f;
        for (boolean transformed : new boolean[]{false, true})
        {
            if (transformed)
            {
                graphics.translate(3, 4);
                graphics.setClip(new Ellipse2D.Double(5, 5, 48, 48));
                graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            }
            for (int opacity : new int[]{0, 1, 25, 50, 75, 99, 100})
            {
                Arrays.fill(pixels(image), originalOverlay);
                rasterizer.begin(graphics, buffer(image), 64, 64, opacity);
                for (int i = 0; i < 10; i++) rasterizer.triangle(10, 10, 10, 50, 50, 10);
                rasterizer.apply(graphics);
                int faded = pixels(image)[20 * 64 + 20];
                for (int shift : new int[]{0, 8, 16})
                {
                    double sceneChannel = (scene >>> shift) & 255;
                    double originalComposite = gpuComposite(originalOverlay, sceneChannel, shift);
                    double expected = sceneChannel + (originalComposite - sceneChannel) * opacity / 100.0;
                    assertEquals("opacity=" + opacity + ", transformed=" + transformed + ", channel=" + shift,
                            expected, gpuComposite(faded, sceneChannel, shift), 1.0);
                }
                assertEquals(originalOverlay, pixels(image)[60 * 64 + 60]);
            }
        }
        graphics.dispose();
    }

    private static double gpuComposite(int overlay, double sceneChannel, int shift)
    {
        // RuneLite's UI GPU blend: source RGB + scene RGB * (1 - source alpha).
        return ((overlay >>> shift) & 255) + sceneChannel * (1 - (overlay >>> 24) / 255.0);
    }

    @Test
    public void nonrectangularClipAndPureStrokeUseAccurateFallback()
    {
        BufferedImage actual = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        BufferedImage expected = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(pixels(actual), -1); Arrays.fill(pixels(expected), -1);
        Graphics2D graphics = actual.createGraphics();
        Graphics2D reference = expected.createGraphics();
        graphics.setClip(new Ellipse2D.Double(10, 10, 40, 40));
        reference.setClip(new Ellipse2D.Double(10, 10, 40, 40));
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        reference.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        reference.setComposite(AlphaComposite.Clear);
        reference.fill(new Polygon(new int[]{5, 5, 63}, new int[]{5, 63, 5}, 3));
        TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
        rasterizer.begin(graphics, buffer(actual), 64, 64, 0);
        rasterizer.triangle(5, 5, 5, 63, 63, 5);
        rasterizer.apply(graphics);
        assertArrayEquals(pixels(expected), pixels(actual));
        graphics.dispose(); reference.dispose();
    }

    @Test
    public void disjointAndOverlappingSpansMatchTheUnionOfTriangles()
    {
        BufferedImage actual = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        BufferedImage expected = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = actual.createGraphics(), reference = expected.createGraphics();
        TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
        Random random = new Random(918);
        reference.setComposite(AlphaComposite.Clear);
        for (int frame = 0; frame < 10; frame++)
        {
            Arrays.fill(pixels(actual), -1); Arrays.fill(pixels(expected), -1);
            rasterizer.begin(graphics, buffer(actual), 80, 80, 0);
            for (int i = 0; i < 40; i++)
            {
                int[] x = {random.nextInt(90) - 5, random.nextInt(90) - 5, random.nextInt(90) - 5};
                int[] y = {random.nextInt(90) - 5, random.nextInt(90) - 5, random.nextInt(90) - 5};
                rasterizer.triangle(x[0], y[0], x[1], y[1], x[2], y[2]);
                reference.fill(new Polygon(x, y, 3));
            }
            rasterizer.apply(graphics);
            assertArrayEquals(pixels(expected), pixels(actual));
        }
        graphics.dispose(); reference.dispose();
    }

    @Test
    public void fractionalClipAndSubimageUseTheGraphicsTarget()
    {
        BufferedImage actual = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        BufferedImage expected = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
        Arrays.fill(pixels(actual), 0x80402010); Arrays.fill(pixels(expected), 0x80402010);
        BufferedImage subimage = actual.getSubimage(20, 20, 64, 64);
        Graphics2D graphics = subimage.createGraphics(), reference = expected.getSubimage(20, 20, 64, 64).createGraphics();
        graphics.setClip(new Rectangle2D.Double(10.6, 10.6, 30.2, 30.2));
        reference.setClip(graphics.getClip());
        reference.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 0.5f));
        reference.fill(new Polygon(new int[]{10, 10, 50}, new int[]{10, 50, 10}, 3));
        TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
        rasterizer.begin(graphics, buffer(subimage), 64, 64, 50);
        for (int i = 0; i < 10; i++) rasterizer.triangle(10, 10, 10, 50, 50, 10);
        rasterizer.apply(graphics);
        assertArrayEquals(pixels(expected), pixels(actual));
        graphics.dispose(); reference.dispose();
    }

    static MainBufferProvider buffer(BufferedImage image)
    {
        return stub(MainBufferProvider.class, values("getWidth", image.getWidth(), "getHeight", image.getHeight(),
                "getPixels", pixels(image), "getImage", image));
    }
}

package com.tilelayers;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.AABB;
import net.runelite.api.Actor;
import net.runelite.api.BufferProvider;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.MainBufferProvider;
import net.runelite.api.Perspective;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;

import static org.junit.Assert.*;

public class ActorOverlayMaskTest
{
    @Test
    public void emptyOverlayDoesNotBuildAnyActorModels()
    {
        Fixture f = new Fixture(1280, 720, 80, 0);
        Graphics2D graphics = f.image.createGraphics();
        AtomicInteger builds = new AtomicInteger();
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 0);
        for (int i = 0; i < 400; i++)
            f.mask.addActor(idleActor(f, i % 2 == 0 ? Player.class : NPC.class, 1000, builds, -1, -1), 0);
        f.mask.endPass(graphics);
        assertEquals(0, builds.get());
        graphics.dispose();
    }

    @Test
    public void idleCrowdsAwayFromOverlayDoNotBuildModels()
    {
        Fixture f = new Fixture(1280, 720, 80, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 1280, 720, false);
        AtomicInteger builds = new AtomicInteger();
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 0);
        for (int i = 0; i < 400; i++)
            f.mask.addActor(idleActor(f, i % 2 == 0 ? Player.class : NPC.class, i % 2 == 0 ? 350 : 1700, builds, -1, -1), 0);
        f.mask.endPass(graphics);
        assertEquals(0, builds.get());
        // An actor reaching the marked area still gets its exact model mask.
        f.mask.addActor(idleActor(f, Player.class, 1000, builds, -1, -1), 0);
        assertEquals(1, builds.get());
        graphics.dispose();
    }

    @Test
    public void clusteredCrowdSharesOneRejectionAndRefreshesWhenMarkerMoves()
    {
        Fixture f = new Fixture(1280, 720, 80, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 1280, 720, false);
        AtomicInteger projectedPoints = new AtomicInteger(), builds = new AtomicInteger();
        Projection canvas = (Projection) Proxy.newProxyInstance(Projection.class.getClassLoader(),
                new Class<?>[]{Projection.class}, (proxy, method, args) -> {
                    projectedPoints.incrementAndGet();
                    float[] result = (float[]) args[3];
                    result[0] = (float) args[0] - 1000;
                    result[1] = (float) args[1] + 100;
                    result[2] = (float) args[2];
                    return result;
                });
        WorldView world = stub(WorldView.class, values("isTopLevel", false, "getCanvasProjection", canvas));
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 10);
        Actor[] actors = new Actor[2000];
        for (int i = 0; i < actors.length; i++)
        {
            Map<String, Object> data = values("getWorldView", world,
                    "getLocalLocation", new LocalPoint(128 + i % 128, 1280 + i % 128, 7),
                    "getAnimation", -1, "getGraphic", -1, "getModelHeight", 200, "getLogicalHeight", 200,
                    "getFootprintSize", 64, "getCurrentOrientation", 0,
                    "getPlayerComposition", stub(PlayerComposition.class, values("getTransformedNpcId", -1)));
            actors[i] = (Actor) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class}, (proxy, method, args) -> {
                if (method.getName().equals("getModel")) { builds.incrementAndGet(); return f.model; }
                return data.get(method.getName());
            });
            f.mask.addActor(actors[i], 0);
        }
        f.mask.endPass(graphics);
        assertEquals("One eight-corner bounds check for the entire cluster", 8, projectedPoints.get());
        assertEquals(0, builds.get());

        graphics.setColor(Color.CYAN);
        graphics.fillRect(325, 360, 5, 5);
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 10);
        f.mask.addActor(actors[72], 0);
        f.mask.endPass(graphics);
        assertEquals("A new marker must invalidate last frame's rejection", 1, builds.get());

        java.util.Arrays.fill(pixels(f.image), 0);
        paintOverlay(graphics, 1280, 720, false);
        projectedPoints.set(0);
        int previousVertexReads = f.vertexReads.get();
        Actor animated = stub(Player.class, values("getWorldView", world, "getModel", f.model,
                "getLocalLocation", new LocalPoint(200, 1352, 7), "getAnimation", 123));
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 10);
        for (int i = 0; i < 2000; i++) f.mask.addActor(animated, 0);
        f.mask.endPass(graphics);
        assertEquals("Animated actors share bounds from their current models", 8, projectedPoints.get());
        assertEquals(previousVertexReads, f.vertexReads.get());
        graphics.dispose();
    }

    @Test
    public void actionAnimationsAndEffectsKeepExactModelBounds()
    {
        Fixture f = new Fixture(1280, 720, 80, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 1280, 720, false);
        AtomicInteger builds = new AtomicInteger();
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 0);
        f.mask.addActor(idleActor(f, Player.class, 350, builds, 123, -1), 0);
        f.mask.addActor(idleActor(f, NPC.class, 1700, builds, -1, 456), 0);
        assertEquals(2, builds.get());
        graphics.dispose();
    }

    private static Actor idleActor(Fixture f, Class<? extends Actor> type, int x, AtomicInteger builds, int animation, int graphic)
    {
        Map<String, Object> data = values("getWorldView", f.world, "getLocalLocation", new LocalPoint(x, 1300, 0),
                "getAnimation", animation, "getGraphic", graphic, "getModelHeight", 200, "getLogicalHeight", 200,
                "getFootprintSize", 64, "getCurrentOrientation", 0,
                "getPlayerComposition", stub(PlayerComposition.class, values("getTransformedNpcId", -1)));
        return (Actor) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getModel")) { builds.incrementAndGet(); return f.model; }
            return data.get(method.getName());
        });

    }

    @Test
    public void cachedCylinderRejectsOffscreenActorsWithoutRotatingMeshBounds()
    {
        Fixture f = new Fixture(513, 385, 400, 512);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 0);
        f.mask.addActor(f.actorAt(5000, 1000), 0);
        assertEquals(0, f.boundsReads.get());
        assertEquals(0, f.vertexReads.get());
        graphics.dispose();
    }

    @Test
    public void nearbyCrowdsOutsideMarkerPixelsSkipAllFaces()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        graphics.setColor(Color.CYAN);
        // Inside the cylinder and the same 32px block as the mesh's right edge,
        // but outside the actual projected mesh.
        graphics.fillRect(315, 192, 1, 1);
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 0);
        for (int i = 0; i < 400; i++) f.mask.addActor(f.actor, 0);
        f.mask.endPass(graphics);
        assertEquals(400, f.vertexReads.get());
        assertEquals(0, f.faceReads.get());
        assertEquals(Color.CYAN.getRGB(), f.image.getRGB(315, 192));

        graphics.fillRect(256, 192, 1, 1);
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actor, 0);
        assertEquals(1, f.faceReads.get());
        assertEquals(0, f.image.getRGB(256, 192));
        graphics.dispose();
    }

    @Test
    public void exactCoverageMatchesPixelsAcrossBlockAndViewportEdges()
    {
        OverlayCoverage coverage = new OverlayCoverage();
        int width = 67, height = 35;
        int[] pixels = new int[width * height];
        Random random = new Random(7632);
        for (int i = 0; i < 70; i++) pixels[random.nextInt(pixels.length)] = 0x01000000;
        coverage.beginFrame(stub(BufferProvider.class, values("getPixels", pixels,
                "getWidth", width, "getHeight", height)), 3, 2, 64, 33);
        for (int i = 0; i < 1000; i++)
        {
            int x = random.nextInt(90) - 10, y = random.nextInt(55) - 10;
            int maxX = x + random.nextInt(35), maxY = y + random.nextInt(35);
            boolean expected = false;
            for (int py = Math.max(2, y); py <= Math.min(34, maxY); py++)
                for (int px = Math.max(3, x); px <= Math.min(66, maxX); px++)
                    expected |= (pixels[py * width + px] & 0xff000000) != 0;
            assertEquals(expected, coverage.intersectsPixels(x, y, maxX, maxY));
        }
        coverage.beginFrame(null, 0, 0, width, height);
        assertTrue(coverage.intersectsPixels(0, 0, width - 1, height - 1));
    }

    @Test
    public void matchesOriginalPixelsForSparseAndDenseOverlays()
    {
        for (int rotation : new int[]{0, 256, 512, 1536})
        {
            for (boolean dense : new boolean[]{false, true})
            {
                Fixture f = new Fixture(513, 385, 400, rotation);
                Graphics2D graphics = f.image.createGraphics();
                paintOverlay(graphics, f.image.getWidth(), f.image.getHeight(), dense);
                BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
                expected.setRGB(0, 0, 513, 385, f.image.getRGB(0, 0, 513, 385, null, 0, 513), 0, 513);
                Graphics2D reference = expected.createGraphics();
                referenceMask(f, reference, f.actor);
                graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                graphics.setComposite(AlphaComposite.SrcOver);
                graphics.setColor(Color.MAGENTA);
                f.mask.beginFrame(graphics);
                f.mask.clear(graphics, f.actor, 0);
                assertArrayEquals("rotation=" + rotation + ", dense=" + dense,
                        pixels(expected), pixels(f.image));
                assertEquals(RenderingHints.VALUE_ANTIALIAS_ON, graphics.getRenderingHint(RenderingHints.KEY_ANTIALIASING));
                assertEquals(AlphaComposite.SrcOver, graphics.getComposite());
                assertEquals(Color.MAGENTA, graphics.getColor());
                reference.dispose();
                graphics.dispose();
            }
        }
    }

    @Test
    public void emptyOverlaySkipsFullModelProjectionAndRefreshesNextFrame()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actor, 0);
        assertEquals(0, f.vertexReads.get());
        paintOverlay(graphics, 513, 385, true);
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actor, 0);
        assertTrue(f.vertexReads.get() > 0);
        graphics.dispose();
    }

    @Test
    public void skipsOffscreenModelsBeforeReadingVertices()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actorAt(5000, 1000), 0);
        assertEquals(0, f.vertexReads.get());
        graphics.dispose();
    }

    @Test
    public void nearPlaneBoundsFallBackToFullProjection()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actorAt(1000, 60), 0);
        assertTrue(f.vertexReads.get() > 0);
        graphics.dispose();
    }

    @Test
    public void nearPlaneTriangleIsClippedToTheVisibleQuadrilateral()
    {
        Fixture f = new Fixture(513, 385, 1, 0);
        Model model = stub(Model.class, values("getAABB", null, "getVerticesCount", 3,
                "getVerticesX", new float[]{-5, -5, 5}, "getVerticesY", new float[]{-105, -95, -105},
                "getVerticesZ", new float[]{-35, 40, 40}, "getFaceCount", 1,
                "getFaceIndices1", new int[]{0}, "getFaceIndices2", new int[]{1}, "getFaceIndices3", new int[]{2},
                "getFaceTransparencies", null));
        Actor actor = stub(Actor.class, values("getModel", model, "getWorldView", f.world,
                "getLocalLocation", new LocalPoint(1000, 60, 0)));
        BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = f.image.createGraphics(), reference = expected.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        paintOverlay(reference, 513, 385, true);
        // The near plane intersects C->A at (-5/3,-5,50) and A->B at (-5,-5/3,50).
        reference.setComposite(AlphaComposite.Clear);
        reference.fill(new Polygon(new int[]{239, 205, 231, 282}, new int[]{141, 175, 218, 167}, 4));
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, actor, 0);
        assertArrayEquals(pixels(expected), pixels(f.image));
        graphics.dispose(); reference.dispose();
    }

    @Test
    public void trianglesBehindTheCameraDoNotEraseOverlays()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        int[] before = pixels(f.image).clone();
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actorAt(1000, -1000), 0);
        assertArrayEquals(before, pixels(f.image));
        assertEquals("Wholly behind-camera bounds need no vertex projection", 0, f.vertexReads.get());
        graphics.dispose();
    }

    @Test
    public void overlappingNpcsKeepTheConfiguredOpacity()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        int[] before = pixels(f.image).clone();
        f.mask.beginFrame(graphics);
        f.mask.beginPass(graphics, 50);
        f.mask.addActor(f.actor, 0);
        f.mask.addActor(f.actor, 0);
        f.mask.endPass(graphics);
        int changed = 0;
        for (int i = 0; i < before.length; i++)
        {
            if (pixels(f.image)[i] != before[i])
            {
                assertEquals(64, pixels(f.image)[i] >>> 24);
                changed++;
            }
        }
        assertTrue(changed > 0);
        graphics.dispose();
    }

    @Test
    public void overlappingActorsAndClippedEdgesMatchOriginal()
    {
        Fixture f = new Fixture(513, 385, 400, 256);
        BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = f.image.createGraphics();
        Graphics2D reference = expected.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        paintOverlay(reference, 513, 385, true);
        graphics.setClip(17, 13, 479, 359);
        reference.setClip(17, 13, 479, 359);
        f.mask.beginFrame(graphics);
        for (int x : new int[]{500, 850, 950, 1000, 1050, 1150, 1500})
        {
            Actor actor = f.actorAt(x, 1000);
            referenceMask(f, reference, actor);
            f.mask.clear(graphics, actor, 0);
        }
        assertArrayEquals(pixels(expected), pixels(f.image));
        reference.dispose();
        graphics.dispose();
    }

    @Test
    public void transformedGraphicsFallBackWithoutMissingPixels()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = f.image.createGraphics();
        Graphics2D reference = expected.createGraphics();
        paintOverlay(graphics, 513, 385, true);
        paintOverlay(reference, 513, 385, true);
        graphics.translate(30, -15);
        reference.translate(30, -15);
        graphics.scale(0.75, 0.75);
        reference.scale(0.75, 0.75);
        referenceMask(f, reference, f.actor);
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, f.actor, 0);
        assertArrayEquals(pixels(expected), pixels(f.image));
        reference.dispose();
        graphics.dispose();
    }

    @Test
    public void missingModelsAreSkipped()
    {
        Fixture f = new Fixture(513, 385, 400, 0);
        Graphics2D graphics = f.image.createGraphics();
        f.mask.beginFrame(graphics);
        f.mask.clear(graphics, stub(Actor.class, values("getModel", null, "getLocalLocation", new LocalPoint(1000, 1000, 0))), 0);
        assertEquals(0, f.vertexReads.get());
        graphics.dispose();
    }

    @Test
    public void coverageHandlesViewportEdgesAndResizing()
    {
        OverlayCoverage coverage = new OverlayCoverage();
        int[] pixels = new int[67 * 35];
        pixels[34 * 67 + 66] = 0x01000000;
        BufferProvider buffer = stub(BufferProvider.class, values("getPixels", pixels, "getWidth", 67, "getHeight", 35));
        coverage.beginFrame(buffer, 3, 2, 64, 33);
        assertFalse(coverage.intersects(-10, -10, 2, 1));
        assertFalse(coverage.intersects(3, 2, 30, 30));
        assertTrue(coverage.intersects(66, 34, 66, 34));
        assertFalse(coverage.intersects(67, 35, 100, 100));
        int[] resizedPixels = new int[7 * 5];
        resizedPixels[0] = 0xff123456;
        coverage.beginFrame(stub(BufferProvider.class, values("getPixels", resizedPixels, "getWidth", 7, "getHeight", 5)), 0, 0, 7, 5);
        assertTrue(coverage.intersects(0, 0, 0, 0));
        coverage.beginFrame(null, 0, 0, 7, 5);
        assertTrue(coverage.intersects(0, 0, 6, 4));
    }

    static void paintOverlay(Graphics2D graphics, int width, int height, boolean dense)
    {
        graphics.setColor(new Color(0x8033ccff, true));
        if (dense)
        {
            graphics.fillRect(0, 0, width, height);
        }
        else
        {
            graphics.drawRect(width / 2 - 24, height / 2, 48, 28);
            graphics.fillRect(width / 2 - 12, height / 2 + 8, 24, 8);
        }
    }

    // Original per-triangle drawing is the pixel oracle for the optimized mask.
    static void referenceMask(Fixture f, Graphics2D graphics, Actor actor)
    {
        Model model = actor.getModel();
        int count = model.getVerticesCount();
        int[] x = new int[count];
        int[] y = new int[count];
        LocalPoint location = actor.getLocalLocation();
        Perspective.modelToCanvas(f.client, count, location.getX(), location.getY(), 0, actor.getCurrentOrientation(),
                model.getVerticesX(), model.getVerticesZ(), model.getVerticesY(), x, y);
        int[] a = model.getFaceIndices1();
        int[] b = model.getFaceIndices2();
        int[] c = model.getFaceIndices3();
        byte[] transparency = model.getFaceTransparencies();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setComposite(AlphaComposite.Clear);
        for (int i = 0; i < model.getFaceCount(); i++)
        {
            if ((x[b[i]] - x[a[i]]) * (y[c[i]] - y[a[i]]) - (y[b[i]] - y[a[i]]) * (x[c[i]] - x[a[i]]) >= 0)
            {
                continue;
            }
            if (transparency == null || (transparency[i] & 255) < 254)
            {
                graphics.fill(new Polygon(new int[]{x[a[i]], x[b[i]], x[c[i]]}, new int[]{y[a[i]], y[b[i]], y[c[i]]}, 3));
            }
        }
        graphics.setComposite(AlphaComposite.SrcOver);
    }

    static int[] pixels(BufferedImage image)
    {
        return ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    }

    static final class Fixture
    {
        final BufferedImage image;
        final Client client;
        final Model model;
        final Actor actor;
        final ActorOverlayMask mask;
        final WorldView world;
        final AtomicInteger vertexReads = new AtomicInteger();
        final AtomicInteger boundsReads = new AtomicInteger();
        final AtomicInteger faceReads = new AtomicInteger();
        final int rotation;

        Fixture(int width, int height, int faces, int rotation)
        {
            this.rotation = rotation;
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            BufferProvider buffer = stub(MainBufferProvider.class, values("getWidth", width, "getHeight", height, "getPixels", pixels(image), "getImage", image));
            world = stub(WorldView.class, values("isTopLevel", true));
            client = stub(Client.class, values("isGpu", true, "getTopLevelWorldView", world,
                    "getViewportWidth", width, "getViewportHeight", height, "getScale", 512,
                    "getCameraFpX", 1000f, "getCameraFpZ", -100f, "getBufferProvider", buffer));
            float[] x = new float[faces * 3];
            float[] y = new float[x.length];
            float[] z = new float[x.length];
            int[] a = new int[faces];
            int[] b = new int[faces];
            int[] c = new int[faces];
            byte[] transparency = new byte[faces];
            Random random = new Random(8349);
            for (int i = 0; i < faces; i++)
            {
                a[i] = i * 3;
                b[i] = i * 3 + 1;
                c[i] = i * 3 + 2;
                transparency[i] = i % 7 == 0 ? (byte) 255 : i % 9 == 0 ? (byte) 254 : (byte) 0;
                for (int j = i * 3; j < i * 3 + 3; j++)
                {
                    x[j] = random.nextInt(201) - 100;
                    y[j] = random.nextInt(201) - 200;
                    z[j] = random.nextInt(101) - 50;
                }
            }
            // A conservative rotated box encloses the entire generated model.
            AABB bounds = stub(AABB.class, values("getCenterY", -100, "getExtremeX", 160,
                    "getExtremeY", 100, "getExtremeZ", 160));
            Map<String, Object> modelValues = values("getVerticesCount", x.length, "getVerticesX", x,
                    "getVerticesY", y, "getVerticesZ", z, "getFaceCount", faces, "getFaceIndices1", a,
                    "getFaceIndices2", b, "getFaceIndices3", c, "getFaceTransparencies", transparency, "getAABB", bounds,
                    "getXYZMag", 160, "getModelHeight", 200, "getBottomY", 0);
            model = (Model) Proxy.newProxyInstance(Model.class.getClassLoader(), new Class<?>[]{Model.class}, (proxy, method, args) -> {
                if (method.getName().equals("getVerticesCount")) vertexReads.incrementAndGet();
                if (method.getName().equals("getAABB")) boundsReads.incrementAndGet();
                if (method.getName().equals("getFaceCount")) faceReads.incrementAndGet();
                return modelValues.get(method.getName());
            });
            actor = actorAt(1000, 1000);
            mask = new ActorOverlayMask(client);
        }

        Actor actorAt(int x, int y)
        {
            return stub(Actor.class, values("getModel", model, "getWorldView", world, "getLocalLocation", new LocalPoint(x, y, 0), "getCurrentOrientation", rotation));
        }
    }

    static Map<String, Object> values(Object... pairs)
    {
        Map<String, Object> values = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    static <T> T stub(Class<T> type, Map<String, Object> values)
    {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (values.containsKey(method.getName())) return values.get(method.getName());
            if (method.getName().equals("calculateBoundsCylinder")) return null;
            if (method.getReturnType() == int.class) return 0;
            if (method.getReturnType() == float.class) return 0f;
            if (method.getReturnType() == boolean.class) return false;
            throw new AssertionError("Unexpected call: " + method);
        }));
    }
}

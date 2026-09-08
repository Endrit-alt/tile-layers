package io.leikvolle.tileindicators;

import java.lang.reflect.Proxy;
import net.runelite.api.*;
import org.junit.Test;
import static io.leikvolle.tileindicators.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class ActorBoundsIndexTest
{
    @Test
    public void cacheSeparatesWorldsSizesHeightsAndRefreshesCamera()
    {
        Fixture f = new Fixture(400, 400, 1, 0);
        pixels(f.image)[200 * 400 + 200] = 0xffabcdef;
        OverlayCoverage coverage = new OverlayCoverage();
        coverage.beginFrame(TriangleMaskRasterizerTest.buffer(f.image), 0, 0, 400, 400);
        ActorProjection projection = new ActorProjection();
        projection.beginFrame(f.client);
        ActorBoundsIndex index = new ActorBoundsIndex(projection, coverage);
        float[] offset = {1000};
        WorldView first = world(offset), second = world(new float[]{500});
        index.beginFrame();
        assertFalse(index.mayOverlap(first, 500, 1000, -100, 40, 40, 0));
        assertTrue("A larger model must not reuse the small model's rejection",
                index.mayOverlap(first, 500, 1000, -100, 600, 40, 0));
        assertTrue("Equal coordinates in another world have their own projection",
                index.mayOverlap(second, 500, 1000, -100, 40, 40, 0));
        assertFalse(index.mayOverlap(first, 1000, 1000, 500, 40, 40, 0));
        assertTrue("Tall animated models must retain their full height",
                index.mayOverlap(first, 1000, 1000, 500, 40, 700, 0));
        offset[0] = 500;
        index.beginFrame();
        assertTrue("Camera/world movement invalidates the previous frame",
                index.mayOverlap(first, 500, 1000, -100, 40, 40, 0));
    }

    @Test
    public void overlayIndexPreservesAnswersAfterHeavyRepeatedQueriesAndResize()
    {
        OverlayCoverage coverage = new OverlayCoverage();
        int[] pixels = new int[257 * 129];
        pixels[pixels.length - 1] = 0x01000000;
        BufferProvider buffer = stub(BufferProvider.class, values("getPixels", pixels, "getWidth", 257, "getHeight", 129));
        coverage.beginFrame(buffer, 0, 0, 257, 129);
        // Repeated wide queries activate the summed-area table.
        for (int i = 0; i < 100; i++) assertFalse(coverage.intersects(0, 0, 255, 127));
        assertTrue(coverage.intersectsPixels(256, 128, 256, 128));
        assertFalse(coverage.intersectsPixels(255, 128, 255, 128));
        pixels[pixels.length - 1] = 0;
        pixels[0] = 0x01000000;
        coverage.beginFrame(buffer, 0, 0, 257, 129);
        assertFalse(coverage.intersectsPixels(256, 128, 256, 128));
        assertTrue(coverage.intersectsPixels(0, 0, 0, 0));
        coverage.beginFrame(stub(BufferProvider.class, values("getPixels", new int[]{0xff123456},
                "getWidth", 1, "getHeight", 1)), 0, 0, 1, 1);
        assertTrue(coverage.intersectsPixels(0, 0, 0, 0));
    }

    private static WorldView world(float[] offset)
    {
        Projection projection = (Projection) Proxy.newProxyInstance(Projection.class.getClassLoader(),
                new Class<?>[]{Projection.class}, (proxy, method, args) -> {
                    float[] result = (float[]) args[3];
                    result[0] = (float) args[0] - offset[0];
                    result[1] = (float) args[1] + 100;
                    result[2] = 512;
                    return result;
                });
        return stub(WorldView.class, values("isTopLevel", false, "getCanvasProjection", projection));
    }
}

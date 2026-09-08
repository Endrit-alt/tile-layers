package com.tilelayers;

import java.lang.reflect.Proxy;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import org.junit.Test;
import static com.tilelayers.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class ActorWorldViewTest
{
    @Test
    public void transformedNpcUsesItsOwnPlaneAndFootprint()
    {
        WorldView child = (WorldView) Proxy.newProxyInstance(WorldView.class.getClassLoader(), new Class<?>[]{WorldView.class}, (proxy, method, args) -> {
            switch (method.getName())
            {
                case "getId": return 7;
                case "getSizeX": case "getSizeY": return 104;
                case "getPlane": return 2;
                case "getTileSettings": return new byte[4][104][104];
                case "getTileHeight":
                    assertEquals("Use the NPC's plane", 2, args[2]);
                    return (int) args[0] > 1000 ? 50 : 100;
                default: throw new AssertionError(method);
            }
        });
        Client client = stub(Client.class, values("getWorldView", child,
                "getTopLevelWorldView", stub(WorldView.class, values("getPlane", 0))));
        NPCComposition base = stub(NPCComposition.class, values("getFootprintSize", 0));
        NPCComposition transformed = stub(NPCComposition.class, values("getFootprintSize", 128));
        NPC npc = stub(NPC.class, values("getWorldView", child, "getLocalLocation", new LocalPoint(1000, 1000, 7),
                "getComposition", base, "getTransformedComposition", transformed, "getAnimationHeightOffset", 7));
        assertEquals(43, ActorHeight.get(client, npc));
    }

    @Test
    public void absentTransformationIsNotRenderedUsingBaseDefinition()
    {
        WorldView world = stub(WorldView.class, values("getSizeX", 104, "getSizeY", 104, "getTileSettings", new byte[4][104][104]));
        Client client = stub(Client.class, values("getWorldView", world));
        NPC npc = stub(NPC.class, values("getWorldView", world, "getLocalLocation", new LocalPoint(1000, 1000, 0),
                "getTransformedComposition", null));
        assertEquals(ActorHeight.UNAVAILABLE, ActorHeight.get(client, npc));
    }

    @Test
    public void childWorldProjectionMatchesRuneLiteAndReusesResultBuffer()
    {
        Projection childProjection = (Projection) Proxy.newProxyInstance(Projection.class.getClassLoader(), new Class<?>[]{Projection.class}, (proxy, method, args) -> {
            float[] result = args.length == 4 ? (float[]) args[3] : new float[3];
            result[0] = (float) args[0] * 0.8f + (float) args[2] * 0.3f - 750;
            result[1] = (float) args[1] - 25;
            result[2] = (float) args[2] * 0.8f - (float) args[0] * 0.3f + 700;
            return result;
        });
        WorldView child = stub(WorldView.class, values("getId", 7, "isTopLevel", false, "getCanvasProjection", childProjection));
        Client client = stub(Client.class, values("getViewportWidth", 513, "getViewportHeight", 385,
                "getViewportXOffset", 13, "getViewportYOffset", 9, "getScale", 512, "isGpu", true));
        ActorProjection projection = new ActorProjection();
        projection.beginFrame(client);
        float[] x = {-90, 40, 75}, y = {-80, -40, 0}, z = {-10, 60, -30};
        int[] expectedX = new int[3], expectedY = new int[3];
        Perspective.modelToCanvas(client, child, 3, 500, 600, -60, 256, x, z, y, expectedX, expectedY);
        assertTrue(projection.project(child, 500, 600, -60, 256, x, y, z, 3));
        assertArrayEquals(expectedX, projection.x);
        assertArrayEquals(expectedY, projection.y);
        assertFalse(projection.project(stub(WorldView.class, values("isTopLevel", false, "getCanvasProjection", null)),
                0, 0, 0, 0, x, y, z, 3));
    }
}

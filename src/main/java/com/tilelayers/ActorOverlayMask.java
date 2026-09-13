/*
 * Copyright (c) 2021, LeikvollE
 * Copyright (c) 2018, Tomas Slusny <slusnucky@gmail.com>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.tilelayers;

import java.awt.Graphics2D;
import net.runelite.api.AABB;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;

/** Clears overlay pixels covered by an actor, using reusable projection buffers. */
final class ActorOverlayMask
{
    private final Client client;
    private final OverlayCoverage coverage = new OverlayCoverage();
    private final ActorProjection projection = new ActorProjection();
    private final ActorBoundsIndex boundsIndex = new ActorBoundsIndex(projection, coverage);
    private final TriangleMaskRasterizer rasterizer = new TriangleMaskRasterizer();
    private final GroundItemOcclusion itemOcclusion;
    private Graphics2D passGraphics;
    private boolean passStarted;
    private int passOpacity;
    private final float[] boundsX = new float[8];
    private final float[] boundsY = new float[8];
    private final float[] boundsZ = new float[8];
    private final int[] clippedX = new int[4];
    private final int[] clippedY = new int[4];
    private final float[] clippedDepth = new float[4];
    private final int[] face = new int[3];

    ActorOverlayMask(Client client)
    {
        this.client = client;
        this.itemOcclusion = new GroundItemOcclusion(client);
    }

    void beginFrame(Graphics2D graphics)
    {
        beginFrame(graphics, null, null);
    }

    void beginFrame(Graphics2D graphics, RenderedActors rendered, net.runelite.client.callback.RenderCallbackManager callbacks)
    {
        // ABOVE_SCENE overlays draw into this buffer in canvas coordinates.
        // Fall back to normal masking if a caller supplies transformed graphics.
        coverage.beginFrame(graphics.getTransform().isIdentity() ? client.getBufferProvider() : null,
                client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight());
        projection.beginFrame(client);
        boundsIndex.beginFrame();
        boolean normalCanvas = graphics.getTransform().isIdentity()
                && (graphics.getClip() == null || graphics.getClip() instanceof java.awt.Rectangle);
        itemOcclusion.beginFrame(normalCanvas ? rendered : null, callbacks, coverage);
    }

    void clear(Graphics2D graphics, Actor actor, int localZ)
    {
        beginPass(graphics, 0);
        try { addActor(actor, localZ); }
        finally { endPass(graphics); }
    }

    void beginPass(Graphics2D graphics, int opacity)
    {
        passOpacity = Math.max(0, Math.min(100, opacity));
        passGraphics = graphics;
        passStarted = false;
    }

    private void startRasterizer()
    {
        // Prepare the mask only once a visible triangle may cover an overlay.
        Graphics2D clipped = (Graphics2D) passGraphics.create();
        try
        {
            clipped.clipRect(client.getViewportXOffset(), client.getViewportYOffset(),
                    client.getViewportWidth(), client.getViewportHeight());
            rasterizer.begin(clipped, client.getBufferProvider(),
                    client.getViewportXOffset() + client.getViewportWidth(),
                    client.getViewportYOffset() + client.getViewportHeight(), passOpacity);
        }
        finally { clipped.dispose(); }
        passStarted = true;
    }

    void endPass(Graphics2D graphics)
    {
        try { if (passStarted) rasterizer.apply(graphics); }
        finally
        {
            passGraphics = null;
            passStarted = false;
        }
    }

    void release()
    {
        passGraphics = null;
        passStarted = false;
        rasterizer.release();
        itemOcclusion.release();
        boundsIndex.beginFrame();
        coverage.beginFrame(null, 0, 0, 0, 0);
    }

    void addActor(Actor actor, int localZ)
    {
        addActor(actor, localZ, null);
    }

    void addActor(Actor actor, int localZ, ActorMaskAdmission admission)
    {
        if (passOpacity == 100 || !hasOverlay()) return;
        LocalPoint location = actor.getLocalLocation();
        if (location == null)
        {
            return;
        }
        WorldView world = actor.getWorldView();
        if (world == null) return;
        if (admission != null && !admission.canRequest(actor)) return;
        if (!idleActorBoundsOverlap(actor, world, location, localZ)) return;
        itemOcclusion.prepare();
        Model model = actor.getModel();
        if (model == null) return;
        if (admission != null) admission.modelAvailable(actor);
        int rotation = actor.getCurrentOrientation();
        if (!boundsOverlap(model, world, location, localZ, rotation))
        {
            return;
        }

        int vertexCount = model.getVerticesCount();
        float[] verticesY = model.getVerticesY();
        if (!projection.project(world, location.getX(), location.getY(), localZ, rotation,
                model.getVerticesX(), verticesY, model.getVerticesZ(), vertexCount)) return;

        // Cylinder and block bounds deliberately overestimate coverage. Check
        // the actual projected extent against pixels before visiting thousands
        // of faces on a player standing next to a marker. Near-plane crossings
        // keep the clipping path because their projected extent is incomplete.
        if (!projection.crossesNearPlane && !coverage.intersectsPixels(
                projection.minX, projection.minY, projection.maxX, projection.maxY)) return;

        int[] faceA = model.getFaceIndices1();
        int[] faceB = model.getFaceIndices2();
        int[] faceC = model.getFaceIndices3();
        byte[] transparency = model.getFaceTransparencies();
        int faceCount = model.getFaceCount();
        for (int i = 0; i < faceCount; i++)
        {
            // Near-invisible NPC helper boxes use transparency 253.
            int faceTransparency = transparency == null ? 0 : transparency[i] & 255;
            if (faceTransparency >= 253) continue;
            int a = faceA[i], b = faceB[i], c = faceC[i];
            if (faceTransparency > 0 && isFlatGroundFace(verticesY[a], verticesY[b], verticesY[c])) continue;
            if (projection.depth[a] >= ActorProjection.NEAR && projection.depth[b] >= ActorProjection.NEAR
                    && projection.depth[c] >= ActorProjection.NEAR)
            {
                triangle(projection.x[a], projection.y[a], projection.depth[a], projection.x[b], projection.y[b], projection.depth[b],
                        projection.x[c], projection.y[c], projection.depth[c]);
            }
            else
            {
                clipFace(a, b, c);
            }
        }
    }

    boolean hasOverlay()
    {
        return coverage.hasOverlay();
    }

    private static boolean isFlatGroundFace(float a, float b, float c)
    {
        // Translucent, nearly horizontal faces at the feet are ground shadows.
        // Leave overlays visible over these decals without excluding solid feet
        // or raised/sloped translucent parts of the actor.
        float low = Math.min(a, Math.min(b, c));
        float high = Math.max(a, Math.max(b, c));
        return low >= -16f && high <= 16f && high - low <= 1f;
    }

    private boolean idleActorBoundsOverlap(Actor actor, WorldView world, LocalPoint location, int localZ)
    {
        // Reject distant idle actors before getModel(), which can rebuild their
        // equipment and animated meshes even when no overlay touches them.
        // Action animations, attached effects, and NPC-transformed players use
        // the full model path because their geometry can extend much farther.
        if (actor.getAnimation() != -1 || actor.getGraphic() != -1) return true;
        if (actor instanceof Player)
        {
            PlayerComposition appearance = ((Player) actor).getPlayerComposition();
            if (appearance == null || appearance.getTransformedNpcId() != -1) return true;
        }
        int height = Math.max(actor.getModelHeight(), actor.getLogicalHeight());
        int footprint = actor.getFootprintSize();
        if (height <= 0 || footprint <= 0 || height > 4096 || footprint > 4096) return true;
        int radius = Math.max(footprint * 2, height + 128);
        int vertical = height + 128;
        if (!boundsIndex.mayOverlap(world, location.getX(), location.getY(), localZ, radius, vertical, vertical)) return false;
        for (int i = 0; i < 8; i++)
        {
            boundsX[i] = (i & 1) == 0 ? -radius : radius;
            boundsY[i] = (i & 2) == 0 ? -vertical : vertical;
            boundsZ[i] = (i & 4) == 0 ? -radius : radius;
        }
        return projectedBoundsOverlap(world, location, localZ);
    }

    private void triangle(int ax, int ay, float az, int bx, int by, float bz, int cx, int cy, float cz)
    {
        if (((long) bx - ax) * ((long) cy - ay) - ((long) by - ay) * ((long) cx - ax) >= 0) return;
        if (coverage.intersects(Math.min(ax, Math.min(bx, cx)), Math.min(ay, Math.min(by, cy)),
                Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy))))
        {
            if (!passStarted) startRasterizer();
            rasterizer.triangle(ax, ay, az, bx, by, bz, cx, cy, cz, itemOcclusion);
        }
    }

    private void clipFace(int a, int b, int c)
    {
        face[0] = a; face[1] = b; face[2] = c;
        int count = 0;
        int previous = c;
        for (int current : face)
        {
            float previousZ = projection.depth[previous];
            float currentZ = projection.depth[current];
            if (!Float.isFinite(previousZ) || !Float.isFinite(currentZ)) return;
            boolean previousInside = previousZ >= ActorProjection.NEAR;
            boolean currentInside = currentZ >= ActorProjection.NEAR;
            if (previousInside != currentInside)
            {
                float t = (ActorProjection.NEAR - previousZ) / (currentZ - previousZ);
                float x = projection.cameraX[previous] + t * (projection.cameraX[current] - projection.cameraX[previous]);
                float y = projection.cameraY[previous] + t * (projection.cameraY[current] - projection.cameraY[previous]);
                clippedX[count] = projection.screenX(x, ActorProjection.NEAR);
                clippedY[count] = projection.screenY(y, ActorProjection.NEAR);
                clippedDepth[count++] = ActorProjection.NEAR;
            }
            if (currentInside)
            {
                clippedX[count] = projection.x[current];
                clippedY[count] = projection.y[current];
                clippedDepth[count++] = currentZ;
            }
            previous = current;
        }
        for (int i = 1; i + 1 < count; i++)
        {
            triangle(clippedX[0], clippedY[0], clippedDepth[0], clippedX[i], clippedY[i], clippedDepth[i],
                    clippedX[i + 1], clippedY[i + 1], clippedDepth[i + 1]);
        }
    }

    private boolean boundsOverlap(Model model, WorldView world, LocalPoint location, int localZ, int rotation)
    {
        // Actor models normally calculate their cylinder during construction.
        // Reuse it instead of getAABB(rotation), which scans and rotates the
        // mesh again for every actor, even when no overlay overlaps it.
        model.calculateBoundsCylinder();
        int radius = model.getXYZMag();
        int height = model.getModelHeight();
        int bottom = model.getBottomY();
        if (radius > 0 && height >= 0 && bottom >= 0)
        {
            // Active animations still build their current model, but actors
            // with similar cylinders can share the coarse projection too.
            if (!boundsIndex.mayOverlap(world, location.getX(), location.getY(), localZ, radius, height, bottom)) return false;
            for (int i = 0; i < 8; i++)
            {
                boundsX[i] = (i & 1) == 0 ? -radius : radius;
                boundsY[i] = (i & 2) == 0 ? -height : bottom;
                boundsZ[i] = (i & 4) == 0 ? -radius : radius;
            }
            return projectedBoundsOverlap(world, location, localZ);
        }
        AABB bounds = model.getAABB(rotation);
        if (bounds == null)
        {
            return true;
        }
        for (int i = 0; i < 8; i++)
        {
            boundsX[i] = bounds.getCenterX() + ((i & 1) == 0 ? -bounds.getExtremeX() : bounds.getExtremeX());
            boundsY[i] = bounds.getCenterY() + ((i & 2) == 0 ? -bounds.getExtremeY() : bounds.getExtremeY());
            boundsZ[i] = bounds.getCenterZ() + ((i & 4) == 0 ? -bounds.getExtremeZ() : bounds.getExtremeZ());
        }
        // getAABB(rotation) already includes the actor's rotation.
        return projectedBoundsOverlap(world, location, localZ);
    }

    private boolean projectedBoundsOverlap(WorldView world, LocalPoint location, int localZ)
    {
        if (!projection.project(world, location.getX(), location.getY(), localZ, 0, boundsX, boundsY, boundsZ, 8)) return false;
        if (projection.maxX == Integer.MIN_VALUE) return false;
        // A box crossing the near plane cannot be safely culled in 2D.
        return projection.crossesNearPlane || coverage.intersectsPixels(
                projection.minX, projection.minY, projection.maxX, projection.maxY);
    }
}

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
    private final float[] boundsX = new float[8];
    private final float[] boundsY = new float[8];
    private final float[] boundsZ = new float[8];
    private final int[] clippedX = new int[4];
    private final int[] clippedY = new int[4];
    private final int[] face = new int[3];

    ActorOverlayMask(Client client)
    {
        this.client = client;
    }

    void beginFrame(Graphics2D graphics)
    {
        // ABOVE_SCENE overlays draw into this buffer in canvas coordinates.
        // Fall back to normal masking if a caller supplies transformed graphics.
        coverage.beginFrame(graphics.getTransform().isIdentity() ? client.getBufferProvider() : null,
                client.getViewportXOffset(), client.getViewportYOffset(),
                client.getViewportWidth(), client.getViewportHeight());
        projection.beginFrame(client);
        boundsIndex.beginFrame();
    }

    void clear(Graphics2D graphics, Actor actor, int localZ)
    {
        beginPass(graphics, 0);
        addActor(actor, localZ);
        endPass(graphics);
    }

    void beginPass(Graphics2D graphics, int opacity)
    {
        rasterizer.begin(graphics, client.getBufferProvider(),
                client.getViewportXOffset() + client.getViewportWidth(),
                client.getViewportYOffset() + client.getViewportHeight(), opacity);
    }

    void endPass(Graphics2D graphics)
    {
        rasterizer.apply(graphics);
    }

    void addActor(Actor actor, int localZ)
    {
        if (!hasOverlay()) return;
        LocalPoint location = actor.getLocalLocation();
        if (location == null)
        {
            return;
        }
        WorldView world = actor.getWorldView();
        if (world == null) return;
        if (!idleActorBoundsOverlap(actor, world, location, localZ)) return;
        Model model = actor.getModel();
        if (model == null) return;
        int rotation = actor.getCurrentOrientation();
        if (!boundsOverlap(model, world, location, localZ, rotation))
        {
            return;
        }

        int vertexCount = model.getVerticesCount();
        if (!projection.project(world, location.getX(), location.getY(), localZ, rotation,
                model.getVerticesX(), model.getVerticesY(), model.getVerticesZ(), vertexCount)) return;

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
            if (transparency != null && (transparency[i] & 255) >= 254) continue;
            int a = faceA[i], b = faceB[i], c = faceC[i];
            if (projection.depth[a] >= ActorProjection.NEAR && projection.depth[b] >= ActorProjection.NEAR
                    && projection.depth[c] >= ActorProjection.NEAR)
            {
                triangle(projection.x[a], projection.y[a], projection.x[b], projection.y[b], projection.x[c], projection.y[c]);
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

    private void triangle(int ax, int ay, int bx, int by, int cx, int cy)
    {
        if (((long) bx - ax) * ((long) cy - ay) - ((long) by - ay) * ((long) cx - ax) >= 0) return;
        if (coverage.intersects(Math.min(ax, Math.min(bx, cx)), Math.min(ay, Math.min(by, cy)),
                Math.max(ax, Math.max(bx, cx)), Math.max(ay, Math.max(by, cy))))
        {
            rasterizer.triangle(ax, ay, bx, by, cx, cy);
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
                clippedY[count++] = projection.screenY(y, ActorProjection.NEAR);
            }
            if (currentInside)
            {
                clippedX[count] = projection.x[current];
                clippedY[count++] = projection.y[current];
            }
            previous = current;
        }
        for (int i = 1; i + 1 < count; i++)
        {
            triangle(clippedX[0], clippedY[0], clippedX[i], clippedY[i], clippedX[i + 1], clippedY[i + 1]);
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

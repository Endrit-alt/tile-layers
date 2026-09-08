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

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.DirectColorModel;
import java.awt.image.SinglePixelPackedSampleModel;
import java.util.Arrays;
import net.runelite.api.BufferProvider;
import net.runelite.api.MainBufferProvider;

/** Builds an opaque union mask, then attenuates each covered overlay pixel once. */
final class TriangleMaskRasterizer
{
    private BufferedImage mask;
    private Graphics2D maskGraphics;
    private int[] maskPixels;
    private int[] targetPixels;
    private int[] dirtyLeft;
    private int[] dirtyRight;
    private int[] solidLeft;
    private int[] solidRight;
    private int dirtyTop;
    private int dirtyBottom;
    private int width;
    private int height;
    private int opacity;
    private boolean premultiplied;
    private boolean fastTriangles;
    private boolean active;
    private Rectangle clip;
    private final Polygon polygon = new Polygon(new int[3], new int[3], 3);

    void begin(Graphics2D graphics, BufferProvider buffer, int fallbackWidth, int fallbackHeight, int opacity)
    {
        active = false;
        this.opacity = Math.max(0, Math.min(100, opacity));
        int newWidth = buffer == null ? fallbackWidth : buffer.getWidth();
        int newHeight = buffer == null ? fallbackHeight : buffer.getHeight();
        if (newWidth <= 0 || newHeight <= 0)
        {
            targetPixels = null;
            return;
        }
        if (mask == null || width != newWidth || height != newHeight)
        {
            if (maskGraphics != null) maskGraphics.dispose();
            width = newWidth;
            height = newHeight;
            mask = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            maskPixels = ((DataBufferInt) mask.getRaster().getDataBuffer()).getData();
            maskGraphics = mask.createGraphics();
            dirtyLeft = new int[height];
            dirtyRight = new int[height];
            solidLeft = new int[height];
            solidRight = new int[height];
            Arrays.fill(dirtyLeft, width);
            Arrays.fill(solidLeft, width);
        }
        else
        {
            for (int y = dirtyTop; y < dirtyBottom; y++)
            {
                if (dirtyLeft[y] < dirtyRight[y])
                {
                    Arrays.fill(maskPixels, y * width + dirtyLeft[y], y * width + dirtyRight[y], 0);
                }
                dirtyLeft[y] = width;
                dirtyRight[y] = 0;
                solidLeft[y] = width;
                solidRight[y] = 0;
            }
        }
        dirtyTop = height;
        dirtyBottom = 0;
        active = true;
        maskGraphics.setTransform(graphics.getTransform());
        maskGraphics.setClip(graphics.getClip());
        maskGraphics.setRenderingHints(graphics.getRenderingHints());
        maskGraphics.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
        maskGraphics.setComposite(AlphaComposite.Src);
        maskGraphics.setColor(Color.WHITE);

        Shape userClip = graphics.getClip();
        clip = userClip == null ? new Rectangle(0, 0, width, height)
                : userClip.getBounds().intersection(new Rectangle(0, 0, width, height));
        fastTriangles = graphics.getTransform().isIdentity()
                && (userClip == null || userClip instanceof Rectangle)
                && graphics.getRenderingHint(java.awt.RenderingHints.KEY_STROKE_CONTROL) != java.awt.RenderingHints.VALUE_STROKE_PURE;
        targetPixels = null;
        // The mask already includes the transform and clip, so the same pixel
        // compositor also handles triangles rasterized by the Java2D fallback.
        if (buffer instanceof MainBufferProvider)
        {
            java.awt.Image image = ((MainBufferProvider) buffer).getImage();
            if (image instanceof BufferedImage)
            {
                BufferedImage target = (BufferedImage) image;
                if (target.getWidth() == width && target.getHeight() == height
                        && target.getRaster().getDataBuffer() instanceof DataBufferInt
                        && target.getRaster().getSampleModel() instanceof SinglePixelPackedSampleModel
                        && ((SinglePixelPackedSampleModel) target.getRaster().getSampleModel()).getScanlineStride() == width
                        && target.getRaster().getDataBuffer().getOffset() == 0
                        && target.getRaster().getSampleModelTranslateX() == 0
                        && target.getRaster().getSampleModelTranslateY() == 0
                        && target.getColorModel() instanceof DirectColorModel)
                {
                    DirectColorModel colors = (DirectColorModel) target.getColorModel();
                    int[] pixels = ((DataBufferInt) target.getRaster().getDataBuffer()).getData();
                    if (pixels == buffer.getPixels() && (long) width * height <= pixels.length
                            && colors.getAlphaMask() == 0xff000000
                            && colors.getRedMask() == 0xff0000 && colors.getGreenMask() == 0xff00 && colors.getBlueMask() == 0xff)
                    {
                        targetPixels = pixels;
                        // RuneLite's GPU buffer remains TYPE_INT_ARGB_PRE even
                        // when its colour model reports straight alpha. The GPU
                        // uses GL_ONE blending, so RGB must fade along with alpha.
                        premultiplied = target.getType() == BufferedImage.TYPE_INT_ARGB_PRE
                                || target.isAlphaPremultiplied();
                    }
                }
            }
        }
    }

    void triangle(int ax, int ay, int bx, int by, int cx, int cy)
    {
        if (opacity == 100 || !active) return;
        // Keep Java2D's exact clipping and stroke normalization for edge cases.
        if (!fastTriangles || !clip.contains(ax, ay) || !clip.contains(bx, by) || !clip.contains(cx, cy))
        {
            polygon.xpoints[0] = ax;
            polygon.xpoints[1] = bx;
            polygon.xpoints[2] = cx;
            polygon.ypoints[0] = ay;
            polygon.ypoints[1] = by;
            polygon.ypoints[2] = cy;
            polygon.invalidate();
            maskGraphics.fill(polygon);
            // Transforms and nonrectangular clips may spread the drawn bounds.
            Rectangle bounds = maskGraphics.getTransform().createTransformedShape(polygon).getBounds();
            markDirty(bounds.x, bounds.y, (long) bounds.x + bounds.width + 1, (long) bounds.y + bounds.height + 1);
            return;
        }
        if (ay > by) { int t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        if (by > cy) { int t = bx; bx = cx; cx = t; t = by; by = cy; cy = t; }
        if (ay > by) { int t = ax; ax = bx; bx = t; t = ay; ay = by; by = t; }
        if (ay == cy) return;

        // Integer polygon fills sample integer scanlines. Ten fractional bits
        // preserve Java2D's edge stepping, including its rounding at thin edges.
        long longStep = ((long) (cx - ax) << 10) / (cy - ay);
        long longX = (long) ax << 10;
        if (ay < by)
        {
            long shortStep = ((long) (bx - ax) << 10) / (by - ay);
            long shortX = (long) ax << 10;
            for (int y = ay; y < by; y++, longX += longStep, shortX += shortStep)
            {
                span(y, longX, shortX);
            }
        }
        if (by < cy)
        {
            long shortStep = ((long) (cx - bx) << 10) / (cy - by);
            long shortX = (long) bx << 10;
            for (int y = by; y < cy; y++, longX += longStep, shortX += shortStep)
            {
                span(y, longX, shortX);
            }
        }
    }

    private void span(int y, long x1, long x2)
    {
        int left = (int) ((Math.min(x1, x2) + 1023) >> 10);
        int right = (int) ((Math.max(x1, x2) + 1023) >> 10);
        if (left >= right) return;
        if (left >= solidLeft[y] && right <= solidRight[y]) return;
        int row = y * width;
        if (left <= solidRight[y] && right >= solidLeft[y])
        {
            if (left < solidLeft[y]) Arrays.fill(maskPixels, row + left, row + solidLeft[y], -1);
            if (right > solidRight[y]) Arrays.fill(maskPixels, row + solidRight[y], row + right, -1);
            solidLeft[y] = Math.min(solidLeft[y], left);
            solidRight[y] = Math.max(solidRight[y], right);
        }
        else
        {
            Arrays.fill(maskPixels, row + left, row + right, -1);
            if (right - left > solidRight[y] - solidLeft[y])
            {
                solidLeft[y] = left;
                solidRight[y] = right;
            }
        }
        dirtyLeft[y] = Math.min(dirtyLeft[y], left);
        dirtyRight[y] = Math.max(dirtyRight[y], right);
        dirtyTop = Math.min(dirtyTop, y);
        dirtyBottom = Math.max(dirtyBottom, y + 1);
    }

    private void markDirty(int x, int y, long right, long bottom)
    {
        int left = Math.max(0, x);
        int top = Math.max(0, y);
        int endX = (int) Math.min(width, right);
        int endY = (int) Math.min(height, bottom);
        if (left >= endX || top >= endY) return;
        for (int row = top; row < endY; row++)
        {
            dirtyLeft[row] = Math.min(dirtyLeft[row], left);
            dirtyRight[row] = Math.max(dirtyRight[row], endX);
        }
        dirtyTop = Math.min(dirtyTop, top);
        dirtyBottom = Math.max(dirtyBottom, endY);
    }

    void apply(Graphics2D graphics)
    {
        if (opacity == 100 || dirtyTop >= dirtyBottom || !active) return;
        if (targetPixels != null)
        {
            for (int y = dirtyTop; y < dirtyBottom; y++)
            {
                int end = y * width + dirtyRight[y];
                for (int i = y * width + dirtyLeft[y]; i < end; i++)
                {
                    if (maskPixels[i] == 0) continue;
                    int pixel = targetPixels[i];
                    int alpha = ((pixel >>> 24) * opacity + 50) / 100;
                    if (alpha == 0) targetPixels[i] = 0;
                    else if (!premultiplied) targetPixels[i] = (pixel & 0xffffff) | (alpha << 24);
                    else
                    {
                        int red = (((pixel >>> 16) & 255) * opacity + 50) / 100;
                        int green = (((pixel >>> 8) & 255) * opacity + 50) / 100;
                        int blue = ((pixel & 255) * opacity + 50) / 100;
                        targetPixels[i] = (alpha << 24) | (red << 16) | (green << 8) | blue;
                    }
                }
            }
        }
        else
        {
            Graphics2D output = (Graphics2D) graphics.create();
            try
            {
                // The mask is already in device coordinates and already clipped.
                output.setTransform(new AffineTransform());
                output.setClip(null);
                output.setComposite(AlphaComposite.getInstance(AlphaComposite.DST_OUT, 1f - opacity / 100f));
                output.drawImage(mask, 0, 0, null);
            }
            finally { output.dispose(); }
        }
    }
}

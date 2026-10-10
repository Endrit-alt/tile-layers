package com.tilelayers.labels;

import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Saves label draw calls with their font, color, transform, composite, clip and hints. */
final class DeferredOverlayText
{
    private final ArrayList<Draw> draws = new ArrayList<>();
    boolean isEmpty() { return draws.isEmpty(); }

    void add(Graphics2D graphics, Consumer<Graphics2D> operation)
    {
        draws.add(new Draw((Graphics2D) graphics.create(), operation));
    }

    void render()
    {
        try { for (Draw draw : draws) draw.operation.accept(draw.graphics); }
        finally { clear(); }
    }

    void clear()
    {
        for (Draw draw : draws) draw.graphics.dispose();
        draws.clear();
    }

    private static final class Draw
    {
        final Graphics2D graphics;
        final Consumer<Graphics2D> operation;
        Draw(Graphics2D graphics, Consumer<Graphics2D> operation) { this.graphics = graphics; this.operation = operation; }
    }
}

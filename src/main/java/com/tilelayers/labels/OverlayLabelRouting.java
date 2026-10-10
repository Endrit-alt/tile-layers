package com.tilelayers.labels;

import com.tilelayers.OverlaySelection;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Function;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Preserves selected labels after character masking, using public overlay APIs. */
@Singleton
public final class OverlayLabelRouting
{
    private final OverlayManager manager;
    private final Predicate<Overlay> selected;
    private final Map<Overlay, Route> routes = new IdentityHashMap<>();
    private final ArrayList<Batch> pending = new ArrayList<>();
    private boolean running, enabled;
    private int seen;
    private final Predicate<Overlay> changed = overlay -> {
        Route route = routes.get(overlay);
        if (route != null)
        {
            seen++;
            return overlay.getLayer() != OverlayLayer.MANUAL || !supported(overlay)
                || Float.compare(route.getPriority(), route.priority()) != 0;
        }
        return wanted(overlay);
    };
    private final Overlay labels = new Overlay()
    {
        { setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE); setPriority(Float.MAX_VALUE); }
        @Override public String getName() { return "TileLayersUnmaskedLabels"; }
        @Override public Dimension render(Graphics2D graphics)
        {
            try
            {
                for (Batch batch : pending)
                    if (running && enabled && manager.anyMatch(o -> o == batch.original)) batch.text.render();
            }
            finally { clearText(); }
            return null;
        }
    };

    @Inject
    public OverlayLabelRouting(OverlayManager manager)
    {
        this(manager, overlay -> OverlaySelection.selected(overlay.getClass().getName())
            && !overlay.getClass().getName().equals("com.lootfilters.LootFiltersTileOverlay"));
    }

    OverlayLabelRouting(OverlayManager manager, Predicate<Overlay> selected)
    {
        this.manager = manager;
        this.selected = selected;
    }

    public void start(boolean enabled) { running = true; configure(enabled); }

    public void configure(boolean enabled)
    {
        this.enabled = enabled;
        clearText();
        refresh();
    }

    public void frame()
    {
        if (!running) return;
        // A skipped late pass, logout or render failure must never replay old labels.
        clearText();
        seen = 0;
        if (manager.anyMatch(changed) || seen != routes.size()) refresh();
    }

    private boolean wanted(Overlay overlay)
    {
        return eligible(overlay) && overlay.getLayer() == OverlayLayer.ABOVE_SCENE && supported(overlay);
    }

    private boolean eligible(Overlay overlay)
    {
        return running && enabled && selected.test(overlay);
    }

    private static boolean supported(Overlay overlay)
    {
        OverlayPosition position = overlay.getPreferredPosition() == null ? overlay.getPosition() : overlay.getPreferredPosition();
        return position == OverlayPosition.DYNAMIC && overlay.getPreferredLocation() == null && overlay.getDrawHooks().isEmpty();
    }

    void refresh()
    {
        synchronized (manager)
        {
            ArrayList<Overlay> present = new ArrayList<>();
            manager.anyMatch(overlay -> { present.add(overlay); return false; });
            Iterator<Map.Entry<Overlay, Route>> iterator = routes.entrySet().iterator();
            while (iterator.hasNext())
            {
                Route route = iterator.next().getValue();
                if (!eligible(route.original) || !present.contains(route.original) || route.original.getLayer() != OverlayLayer.MANUAL
                    || !supported(route.original))
                {
                    release(route);
                    iterator.remove();
                }
                else if (Float.compare(route.getPriority(), route.priority()) != 0)
                {
                    manager.remove(route);
                    route.setPriority(route.priority());
                    manager.add(route);
                }
            }
            if (running)
                for (Overlay original : present)
                    if (wanted(original) && !routes.containsKey(original))
                    {
                        Route route = new Route(original);
                        original.setLayer(OverlayLayer.MANUAL);
                        if (manager.add(route)) routes.put(original, route);
                        else original.setLayer(OverlayLayer.ABOVE_SCENE);
                    }
            if (!running || !enabled || routes.isEmpty()) manager.remove(labels);
            else manager.add(labels);
        }
    }

    public void stop() { running = false; clearText(); refresh(); }

    private void release(Route route)
    {
        if (route.original.getLayer() == OverlayLayer.MANUAL) route.original.setLayer(OverlayLayer.ABOVE_SCENE);
        manager.remove(route);
    }

    private void clearText()
    {
        for (Batch batch : pending) batch.text.clear();
        pending.clear();
    }

    Dimension render(Overlay original, Graphics2D graphics, Function<Graphics2D, Dimension> draw)
    {
        if (!running || !enabled) return draw.apply(graphics);
        Batch batch = new Batch(original);
        boolean complete = false;
        try
        {
            boolean groundItems = original.getClass().getName().equals("net.runelite.client.plugins.grounditems.GroundItemsOverlay");
            Dimension size = draw.apply(new DeferredTextGraphics(graphics, batch.text, groundItems));
            complete = true;
            if (!batch.text.isEmpty()) pending.add(batch);
            return size;
        }
        finally { if (!complete) batch.text.clear(); }
    }

    private static final class Batch
    {
        final Overlay original;
        final DeferredOverlayText text = new DeferredOverlayText();
        Batch(Overlay original) { this.original = original; }
    }

    private final class Route extends Overlay
    {
        final Overlay original;
        final boolean completeLabel;

        Route(Overlay original)
        {
            this.original = original;
            completeLabel = original.getClass().getName().equals("com.lootfilters.LootFiltersTextOverlay");
            setPosition(OverlayPosition.DYNAMIC); setLayer(OverlayLayer.ABOVE_SCENE);
            setPriority(priority());
        }

        @Override public String getName() { return "TileLayersLabelGraphics_" + original.getName(); }

        float priority() { return completeLabel ? Float.MAX_VALUE : original.getPriority(); }

        boolean present() { return running && enabled && original.getLayer() == OverlayLayer.MANUAL && manager.anyMatch(o -> o == original); }

        @Override public Dimension render(Graphics2D graphics)
        {
            if (!present()) return null;
            // The split Loot Filters overlay contains labels only. Render it
            // once, intact, after masking so icons, timers and controls stay
            // with their text. No Graphics2D interception is needed here.
            if (completeLabel) return original.render(graphics);
            return OverlayLabelRouting.this.render(original, graphics, original::render);
        }
    }
}

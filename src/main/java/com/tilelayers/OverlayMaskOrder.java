package com.tilelayers;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Temporarily schedules overlay exceptions after character masking. */
final class OverlayMaskOrder
{
    static final float MASK_PRIORITY = Overlay.PRIORITY_HIGHEST + 1f;
    private final Map<Overlay, PriorityChange> changes = new IdentityHashMap<>();
    private boolean keepLootAbove;
    private boolean onlyTiles;
    private int orderedOverlaysSeen;
    private final Predicate<Overlay> needsOrder = overlay -> {
        if (!isExceptionOverlay(overlay)) return false;
        PriorityChange change = changes.get(overlay);
        // Loot already above the mask needs no override in loot-only mode.
        if (change == null && !onlyTiles && !(overlay.getPriority() <= MASK_PRIORITY)) return false;
        orderedOverlaysSeen++;
        return change == null || Float.compare(overlay.getPriority(), change.applied) != 0;
    };

    // Detect overlays added or changed during gameplay without sorting or
    // allocating a new collection on every frame. The manager owns the list.
    boolean needsUpdate(OverlayManager manager, boolean keepLootAbove, boolean onlyTiles)
    {
        if (this.keepLootAbove != keepLootAbove || this.onlyTiles != onlyTiles) return true;
        orderedOverlaysSeen = 0;
        return manager.anyMatch(needsOrder) || orderedOverlaysSeen != changes.size();
    }

    // Called under the overlay-manager lock when settings or overlays change.
    boolean update(OverlayManager manager, boolean keepLootAbove, boolean onlyTiles)
    {
        this.keepLootAbove = keepLootAbove;
        this.onlyTiles = onlyTiles;
        if (!keepLootAbove && !onlyTiles) return restore();
        Set<Overlay> current = Collections.newSetFromMap(new IdentityHashMap<>());
        manager.anyMatch(overlay -> {
            if (isExceptionOverlay(overlay)) current.add(overlay);
            return false;
        });
        boolean changed = false;
        Iterator<Map.Entry<Overlay, PriorityChange>> iterator = changes.entrySet().iterator();
        while (iterator.hasNext())
        {
            Map.Entry<Overlay, PriorityChange> entry = iterator.next();
            if (!current.contains(entry.getKey()))
            {
                changed |= restore(entry.getKey(), entry.getValue());
                iterator.remove();
            }
        }
        if (onlyTiles)
        {
            // Preserve other overlays' relative order, including custom negative
            // or very high priorities. Equal priorities retain manager order.
            List<Overlay> ordered = new ArrayList<>(current);
            ordered.sort((a, b) -> Float.compare(originalPriority(a), originalPriority(b)));
            float applied = MASK_PRIORITY + 1f;
            float previous = 0f;
            boolean first = true;
            for (Overlay overlay : ordered)
            {
                float original = originalPriority(overlay);
                if (!first && Float.compare(previous, original) != 0) applied += 1f;
                changed |= apply(overlay, original, applied);
                previous = original;
                first = false;
            }
        }
        else
        {
            for (Overlay overlay : current)
            {
                float original = originalPriority(overlay);
                if (Float.isNaN(original) || original > MASK_PRIORITY)
                {
                    PriorityChange change = changes.remove(overlay);
                    if (change != null) changed |= restore(overlay, change);
                    continue;
                }
                changed |= apply(overlay, original, MASK_PRIORITY + 1f + Math.max(0f, original));
            }
        }
        return changed;
    }

    private float originalPriority(Overlay overlay)
    {
        PriorityChange change = changes.get(overlay);
        float priority = overlay.getPriority();
        // Respect priorities changed by the overlay's owner in the meantime.
        return change != null && Float.compare(priority, change.applied) == 0 ? change.original : priority;
    }

    private boolean apply(Overlay overlay, float original, float applied)
    {
        PriorityChange existing = changes.get(overlay);
        if (existing == null || Float.compare(existing.original, original) != 0
                || Float.compare(existing.applied, applied) != 0)
            changes.put(overlay, new PriorityChange(original, applied));
        if (Float.compare(overlay.getPriority(), applied) == 0) return false;
        overlay.setPriority(applied);
        return true;
    }

    boolean restore()
    {
        keepLootAbove = false;
        onlyTiles = false;
        boolean changed = false;
        for (Map.Entry<Overlay, PriorityChange> entry : changes.entrySet())
            changed |= restore(entry.getKey(), entry.getValue());
        changes.clear();
        return changed;
    }

    private static boolean restore(Overlay overlay, PriorityChange change)
    {
        if (Float.compare(overlay.getPriority(), change.applied) != 0
                || Float.compare(change.original, change.applied) == 0) return false;
        overlay.setPriority(change.original);
        return true;
    }

    private static boolean isItemOverlay(Overlay overlay)
    {
        String name = overlay.getClass().getName();
        // Match the exact public overlay classes; Loot Filters stays optional.
        return name.equals("net.runelite.client.plugins.grounditems.GroundItemsOverlay")
                || name.equals("com.lootfilters.LootFiltersOverlay");
    }

    private boolean isExceptionOverlay(Overlay overlay)
    {
        return isDynamicSceneOverlay(overlay)
                && (onlyTiles ? !isSelectedOverlay(overlay) : keepLootAbove && isItemOverlay(overlay));
    }

    private static boolean isSelectedOverlay(Overlay overlay)
    {
        String name = overlay.getClass().getName();
        // Optional marker plugins are identified by their own namespaces, so
        // their scene previews are included without adding plugin dependencies.
        return name.startsWith("net.runelite.client.plugins.groundmarkers.")
                || name.startsWith("net.runelite.client.plugins.tileindicators.")
                || name.startsWith("com.linemarkers.")
                || name.startsWith("com.radiusmarkers.")
                || name.startsWith("com.bram91.brushmarkers.")
                || name.startsWith("net.runelite.client.plugins.objectindicators.")
                || name.startsWith("net.runelite.client.plugins.agility.")
                // Our own overlay draws the Improved destination tile and/or
                // applies the mask. It must never be moved after itself.
                || name.equals("com.tilelayers.TileLayersOverlay")
                || name.equals("io.leikvolle.tileindicators.ImprovedTileIndicatorsOverlay");
    }

    private static boolean isDynamicSceneOverlay(Overlay overlay)
    {
        OverlayPosition position = overlay.getPreferredPosition();
        if (position == null) position = overlay.getPosition();
        return overlay.getLayer() == OverlayLayer.ABOVE_SCENE
                && position == OverlayPosition.DYNAMIC;
    }

    private static final class PriorityChange
    {
        private final float original;
        private final float applied;

        private PriorityChange(float original, float applied)
        {
            this.original = original;
            this.applied = applied;
        }
    }
}

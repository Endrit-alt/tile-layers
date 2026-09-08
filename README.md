# Best Tile Indicators
Draw overlays beneath players and NPCs, with adjustable opacity and a crowd cutoff.

Maintained by **Endrit**, based on [Improved Tile Indicators by LeikvollE](https://github.com/LeikvollE/tileindicators).

All four overlay checkboxes are enabled by default: your player, other players, all NPCs, and named NPCs.

## Features
### Draw behind player
Draws overlays behind the player

![](truetile.png)
![](truetilegpu.png)

### Draw behind NPC
Enable **Draw overlays below all NPCs**, below the other-player checkbox, to draw overlays behind nearby NPCs without tagging them. Requires GPU.

Set **Overlay opacity** to control how much of an overlay remains visible over enabled characters: **0%** hides it completely, **50%** retains half its original opacity, and **100%** leaves it unchanged. The default is **10%**. This setting applies to your player, other players, and both all-NPC and selected-NPC modes. Overlapping characters do not fade the overlay repeatedly.

**Crowd cutoff** defaults to **80** and applies to players and NPCs separately. At **80 or more loaded other players**, all other-player masking pauses. At **80 or more loaded NPCs**, all NPC masking pauses, including NPCs matched by typed names. Each category automatically resumes when its own count falls below this shared cutoff. Your own player is excluded from the count and remains controlled by its checkbox. Set the cutoff from **0–500**; 0 disables both crowd effects.

Counts include actors in child world views. Counting stops as soon as a category reaches its threshold, before any height, model, or mask processing for that category. RuneLite still has its normal cost to render the crowd.

Only actors submitted to the scene renderer contribute silhouettes. When stacked players or NPCs are skipped by the scene, their invisible models do not cut holes in overlays. Visibility refreshes every frame, including for your own player.

The renderer skips all masking when the overlay is empty or opacity is 100%. Padded footprint and height bounds reject distant idle actors before building their models; action animations and attached effects retain the exact model path. Cached cylinder bounds avoid recalculating rotated model bounds, and actual projected extents skip triangle processing when they miss the overlay pixels. Actor silhouettes share a reusable mask that fades each covered pixel once. Camera near-plane clipping keeps nearby silhouettes intact, and transformed NPC sizes and world-view heights are respected.

Nearby characters share conservative bounds checks within each frame, and stacked characters reuse identical pixel searches. Busy frames build an overlay index for constant-time overlap queries; light frames keep the simpler search. These caches refresh every frame so camera movement, moving markers, and changing character models take effect immediately. The same optimizations apply to players and NPCs.

For named NPCs only, turn off **Draw overlays below all NPCs** and enable **Draw overlays below named NPCs**. This checkbox and the **NPC names** box sit directly below the three main checkboxes in **Overlay settings**. Enter comma-separated names, using `*` for wildcards, or shift-right-click NPCs and choose **Add NPC name** or **Remove NPC name**. A blank list affects no NPCs in named mode. If both NPC checkboxes are enabled, all NPCs receive the effect. Both modes use the shared population cutoff of 80, counting every loaded NPC even if its name does not match; there is no separate selected-NPC limit.

![](menu.png)
![](Hunllef.png)

### Draw behind other players
Enable **Draw overlays below other players**, the second checkbox, to draw overlays behind other players while their loaded count stays below **Crowd cutoff**. It uses the same optimized masking as the NPC option, including players in child world views. Your own player is controlled separately by **Draw overlays below player**. Requires GPU.

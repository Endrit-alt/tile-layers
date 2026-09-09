package com.tilelayers;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.junit.Test;
import static com.tilelayers.ActorOverlayMaskTest.*;
import static org.junit.Assert.*;

public class OtherPlayerOverlayTest
{
    @Test
    public void localAndOtherPlayerSwitchesAreIndependent() throws Exception
    {
        for (boolean local : new boolean[]{false, true})
        {
            for (boolean others : new boolean[]{false, true}) verify(local, others, false, true);
        }
    }

    @Test
    public void includesOtherPlayersInChildWorldViews() throws Exception
    {
        verify(false, true, true, true);
    }

    @Test
    public void gpuIsRequiredForPlayerMasking() throws Exception
    {
        verify(true, true, true, false);
    }

    @Test
    public void sharedOpacityAppliesOnceToSelfOtherPlayersAndNpcs() throws Exception
    {
        for (int opacity : new int[]{0, 25, 50, 75, 100})
            verify(true, true, true, true, opacity, true);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void groundTextIsMaskedAfterDrawingRegardlessOfPluginLoadOrder() throws Exception
    {
        Fixture f = new Fixture(513, 385, 80, 0);
        Map<String, Object> worldData = worldValues(true);
        WorldView world = stub(WorldView.class, worldData);
        Player local = player(f, world, 1000, 0);
        worldData.put("players", indexed(local));
        worldData.put("npcs", indexed());
        Client client = stub(Client.class, values("isGpu", true, "getTopLevelWorldView", world,
                "getWorldView", world, "getLocalPlayer", local, "getViewportWidth", 513, "getViewportHeight", 385,
                "getScale", 512, "getCameraFpX", 1000f, "getCameraFpZ", -100f,
                "getBufferProvider", TriangleMaskRasterizerTest.buffer(f.image)));
        Map<String, Object> settings = values("overlaysBelowPlayer", true, "crowdLimit", 80);
        Constructor<TileLayersOverlay> constructor = TileLayersOverlay.class
                .getDeclaredConstructor(Client.class, TileLayersConfig.class);
        constructor.setAccessible(true);
        TileLayersOverlay mask = constructor.newInstance(client, stub(TileLayersConfig.class, settings));
        recordFrame(attachRenderedActors(mask), local, world);
        // Use RuneLite's real ordering rather than reproducing its comparator.
        Field comparatorField = OverlayManager.class.getDeclaredField("OVERLAY_COMPARATOR");
        comparatorField.setAccessible(true);
        Comparator<Overlay> comparator = (Comparator<Overlay>) comparatorField.get(null);
        assertEquals("The mask stays in the scene layer, before UI overlays", OverlayLayer.ABOVE_SCENE, mask.getLayer());
        // Ground Items uses DEFAULT; Loot Filters uses HIGH.
        for (float priority : new float[]{Overlay.PRIORITY_DEFAULT, Overlay.PRIORITY_HIGH, Overlay.PRIORITY_HIGHEST})
        for (boolean boxed : new boolean[]{false, true})
        {
            Overlay groundText = new Overlay()
            {
                @Override
                public Dimension render(Graphics2D graphics)
                {
                    if (boxed)
                    {
                        graphics.setColor(new Color(20, 30, 40, 180));
                        graphics.fillRoundRect(184, 164, 144, 50, 6, 6);
                    }
                    graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                    graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
                    graphics.setColor(Color.WHITE);
                    graphics.drawString("Dragon bones", 205, 184);
                    graphics.drawString("Coins (10,000)", 205, 203);
                    return null;
                }
            };
            groundText.setPosition(OverlayPosition.DYNAMIC);
            groundText.setLayer(OverlayLayer.ABOVE_SCENE);
            groundText.setPriority(priority);
            for (int opacity : new int[]{0, 50, 100})
            for (boolean maskLoadedFirst : new boolean[]{false, true})
            {
                settings.put("overlayOpacity", opacity);
                Arrays.fill(pixels(f.image), 0);
                BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
                Graphics2D reference = expected.createGraphics();
                groundText.render(reference);
                int[] original = pixels(expected).clone();
                referenceMask(f, reference, local);
                reference.dispose();
                assertFalse("Test text must intersect the character silhouette", Arrays.equals(original, pixels(expected)));
                for (int i = 0; i < original.length; i++)
                {
                    if (pixels(expected)[i] == 0 && original[i] != 0 && opacity > 0)
                    {
                        int alpha = ((original[i] >>> 24) * opacity + 50) / 100;
                        pixels(expected)[i] = (original[i] & 0xffffff) | (alpha << 24);
                    }
                }
                Overlay[] overlays = maskLoadedFirst ? new Overlay[]{mask, groundText} : new Overlay[]{groundText, mask};
                Arrays.sort(overlays, comparator);
                for (Overlay overlay : overlays)
                {
                    Graphics2D graphics = f.image.createGraphics();
                    overlay.render(graphics);
                    graphics.dispose();
                }
                assertArrayEquals("Ground text priority=" + priority + ", opacity=" + opacity
                        + ", boxed=" + boxed + ", mask loaded first=" + maskLoadedFirst, pixels(expected), pixels(f.image));
            }
        }
    }

    @Test
    public void combinedCrowdCutoffPausesBothCategoriesAndResumesBelowTheLimit() throws Exception
    {
        Fixture f = new Fixture(513, 385, 80, 0);
        Map<String, Object> worldData = worldValues(true);
        WorldView world = stub(WorldView.class, worldData);
        int[] models = new int[2001], heights = new int[2001];
        Player local = trackedActor(Player.class, f, world, 0, 2000, models, heights);
        Player[] players = new Player[1000];
        NPC[] npcs = new NPC[1000];
        for (int i = 0; i < 1000; i++)
        {
            npcs[i] = trackedActor(NPC.class, f, world, i + 1, i * 2, models, heights);
            players[i] = trackedActor(Player.class, f, world, i + 1, i * 2 + 1, models, heights);
        }
        Client client = stub(Client.class, values("isGpu", true, "getTopLevelWorldView", world,
                "getWorldView", world, "getLocalPlayer", local, "getGameState", GameState.LOGGED_IN, "getViewportWidth", 513,
                "getViewportHeight", 385, "getScale", 512, "getCameraFpX", 1000f, "getCameraFpZ", -100f,
                "getBufferProvider", TriangleMaskRasterizerTest.buffer(f.image)));
        Map<String, Object> settings = values("overlaysBelowPlayer", true, "overlaysBelowOtherPlayers", true,
                "overlaysBelowAllNPCs", true, "overlayOpacity", 10, "crowdLimit", 80);
        Constructor<TileLayersOverlay> constructor = TileLayersOverlay.class
                .getDeclaredConstructor(Client.class, TileLayersConfig.class);
        constructor.setAccessible(true);
        TileLayersOverlay overlay = constructor.newInstance(client, stub(TileLayersConfig.class, settings));
        RenderedActors rendered = attachRenderedActors(overlay);
        Graphics2D graphics = f.image.createGraphics();
        int[][] cases = {{39, 40, 80}, {40, 40, 80}, {41, 40, 80}, {79, 79, 80}, {39, 40, 80},
                {79, 0, 80}, {0, 79, 80}, {80, 0, 80}, {0, 80, 80}, {81, 1, 80}, {1, 81, 80},
                {24, 25, 50}, {25, 25, 50}, {26, 25, 50}, {49, 49, 50}, {1000, 1000, 50}, {24, 25, 50},
                {1, 1, 0}, {0, 0, 0}, {4, 5, 10}, {5, 5, 10}};
        for (boolean self : new boolean[]{false, true})
        {
            settings.put("overlaysBelowPlayer", self);
            for (int[] scenario : cases)
            {
                int playerCount = scenario[0], npcCount = scenario[1];
                settings.put("crowdLimit", scenario[2]);
                Player[] loadedPlayers = Arrays.copyOf(players, playerCount + 1);
                loadedPlayers[playerCount] = local;
                worldData.put("players", indexed(loadedPlayers));
                worldData.put("npcs", indexed(Arrays.copyOf(npcs, npcCount)));
                recordFrame(rendered, local, world);
                Arrays.fill(models, 0); Arrays.fill(heights, 0); Arrays.fill(pixels(f.image), 0);
                paintOverlay(graphics, 513, 385, true);
                overlay.render(graphics);
                for (int i = 0; i < 1000; i++)
                {
                    boolean crowdAllowed = playerCount + npcCount < scenario[2];
                    int expectedPlayer = crowdAllowed && i < playerCount ? 1 : 0;
                    int expectedNpc = crowdAllowed && i < npcCount ? 1 : 0;
                    assertEquals("Player model work at crowd " + playerCount, expectedPlayer, models[i * 2 + 1]);
                    assertEquals("Player height work at crowd " + playerCount, expectedPlayer, heights[i * 2 + 1]);
                    assertEquals("NPC model work at crowd " + npcCount, expectedNpc, models[i * 2]);
                    assertEquals("NPC height work at crowd " + npcCount, expectedNpc, heights[i * 2]);
                }
                assertEquals("The combined cutoff does not affect the self checkbox", self ? 1 : 0, models[2000]);
                assertEquals("The combined cutoff does not affect self height work", self ? 1 : 0, heights[2000]);
            }
        }

        TileLayersPlugin plugin = new TileLayersPlugin();
        plugin.config = stub(TileLayersConfig.class, settings);
        Field clientField = TileLayersPlugin.class.getDeclaredField("client");
        clientField.setAccessible(true); clientField.set(plugin, client);
        Field pluginField = TileLayersOverlay.class.getDeclaredField("plugin");
        pluginField.setAccessible(true); pluginField.set(overlay, plugin);
        settings.put("overlaysBelowPlayer", false);
        settings.put("crowdLimit", 80);
        worldData.put("players", indexed(local, players[0], players[1], players[2]));
        Object[][] nameCases = {{"", 0}, {" , , ", 0}, {"Gemstone crab", 38},
                {"Gemstone crab, Rat", 76}, {" gemSTONE* , ", 38}, {"Rat", 38}, {"Unlisted NPC", 0}};
        for (Object[] nameCase : nameCases)
        {
            String names = (String) nameCase[0];
            settings.put("getTopNPCs", names);
            for (int mode = 0; mode < 4; mode++)
            {
                boolean allNpcs = (mode & 1) != 0, namedNpcs = (mode & 2) != 0;
                settings.put("overlaysBelowAllNPCs", allNpcs);
                settings.put("overlaysBelowNPCs", namedNpcs);
                for (int npcCount : new int[]{76, 77, 76})
                {
                    worldData.put("npcs", indexed(Arrays.copyOf(npcs, npcCount)));
                    plugin.rebuild();
                    recordFrame(rendered, local, world);
                    Arrays.fill(models, 0); Arrays.fill(heights, 0); Arrays.fill(pixels(f.image), 0);
                    paintOverlay(graphics, 513, 385, true);
                    overlay.render(graphics);
                    int npcModels = 0, npcHeights = 0, playerModels = 0;
                    for (int i = 0; i < 1000; i++)
                    {
                        npcModels += models[i * 2]; npcHeights += heights[i * 2]; playerModels += models[i * 2 + 1];
                    }
                    boolean crowdAllowed = npcCount + 3 < 80;
                    int expected = !crowdAllowed ? 0 : allNpcs ? npcCount : namedNpcs ? (int) nameCase[1] : 0;
                    String scenario = "NPC count=" + npcCount + ", all=" + allNpcs + ", named=" + namedNpcs + ", names=" + names;
                    assertEquals(scenario, expected, npcModels);
                    assertEquals(scenario, expected, npcHeights);
                    assertEquals("All loaded NPCs contribute to the combined player cutoff", crowdAllowed ? 3 : 0, playerModels);
                }
            }
        }
        graphics.dispose();
    }
    private static <T extends Actor> T trackedActor(Class<T> type, Fixture f, WorldView world,
            int distance, int id, int[] models, int[] heights)
    {
        Actor base = stub(type, values("getWorldView", world, "getLocalLocation", new LocalPoint(1000 + distance, 1000, 0),
                "getAnimation", 123, "getFootprintSize", 64, "getId", id, "getIndex", id,
                "getName", id % 4 == 0 ? "Gemstone crab" : "Rat",
                "getTransformedComposition", stub(NPCComposition.class, values("getFootprintSize", 64))));
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            if (method.getName().equals("getModel")) { models[id]++; return f.model; }
            if (method.getName().equals("getAnimationHeightOffset")) { heights[id]++; return 0; }
            if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
            if (method.getName().equals("equals")) return proxy == args[0];
            return method.invoke(base, args);
        }));
    }

    private void verify(boolean belowLocal, boolean belowOthers, boolean includeChild, boolean gpu) throws Exception
    {
        verify(belowLocal, belowOthers, includeChild, gpu, 0, false);
    }

    private void verify(boolean belowLocal, boolean belowOthers, boolean includeChild, boolean gpu, int opacity, boolean includeNpc) throws Exception
    {
        Fixture f = new Fixture(513, 385, 80, 0);
        Map<String, Object> topValues = worldValues(true), childValues = worldValues(false);
        WorldView top = stub(WorldView.class, topValues), child = stub(WorldView.class, childValues);
        Player local = player(f, top, 800, 0), other = player(f, top, 1100, 0), childPlayer = player(f, child, 1400, 7);
        topValues.put("players", indexed(local, other));
        NPC npc = stub(NPC.class, values("getModel", f.model, "getWorldView", top,
                "getLocalLocation", new LocalPoint(1100, 1000, 0),
                "getTransformedComposition", stub(NPCComposition.class, values("getFootprintSize", 64))));
        topValues.put("npcs", indexed(npc));
        childValues.put("npcs", indexed());
        topValues.put("worldViews", includeChild ? indexed(child) : indexed());
        childValues.put("players", indexed(childPlayer));
        childValues.put("getMainWorldProjection", Proxy.newProxyInstance(Projection.class.getClassLoader(), new Class<?>[]{Projection.class}, (proxy, method, args) -> {
            float[] result = (float[]) args[3];
            result[0] = (float) args[0]; result[1] = (float) args[1]; result[2] = (float) args[2];
            return result;
        }));
        childValues.put("getCanvasProjection", Proxy.newProxyInstance(Projection.class.getClassLoader(), new Class<?>[]{Projection.class}, (proxy, method, args) -> {
            float[] result = args.length == 4 ? (float[]) args[3] : new float[3];
            result[0] = (float) args[0] - 1000;
            result[1] = (float) args[1] + 100;
            result[2] = (float) args[2];
            return result;
        }));
        Map<String, Object> clientValues = values("isGpu", gpu, "getTopLevelWorldView", top, "getLocalPlayer", local,
                "getViewportWidth", 513, "getViewportHeight", 385, "getScale", 512,
                "getCameraFpX", 1000f, "getCameraFpZ", -100f,
                "getBufferProvider", TriangleMaskRasterizerTest.buffer(f.image));
        Client base = stub(Client.class, clientValues);
        Client client = (Client) Proxy.newProxyInstance(Client.class.getClassLoader(), new Class<?>[]{Client.class}, (proxy, method, args) -> {
            if (method.getName().equals("getWorldView")) return (int) args[0] == 7 ? child : top;
            return method.invoke(base, args);
        });
        TileLayersConfig config = stub(TileLayersConfig.class,
                values("overlaysBelowPlayer", belowLocal, "overlaysBelowOtherPlayers", belowOthers,
                        "overlaysBelowAllNPCs", includeNpc, "overlayOpacity", opacity, "crowdLimit", 80));
        Constructor<TileLayersOverlay> constructor = TileLayersOverlay.class
                .getDeclaredConstructor(Client.class, TileLayersConfig.class);
        constructor.setAccessible(true);
        TileLayersOverlay overlay = constructor.newInstance(client, config);
        recordFrame(attachRenderedActors(overlay), local, top);

        BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = f.image.createGraphics(), reference = expected.createGraphics();
        paintOverlay(graphics, 513, 385, true); paintOverlay(reference, 513, 385, true);
        int[] original = pixels(expected).clone();
        if (gpu && belowLocal) referenceMask(f, reference, local);
        if (gpu && belowOthers)
        {
            referenceMask(f, reference, other);
            if (includeChild) referenceMask(f, reference, childPlayer);
        }
        if (gpu && includeNpc) referenceMask(f, reference, npc);
        for (int i = 0; i < original.length; i++)
        {
            if (pixels(expected)[i] == 0 && original[i] != 0 && opacity > 0)
            {
                int alpha = ((original[i] >>> 24) * opacity + 50) / 100;
                pixels(expected)[i] = (original[i] & 0xffffff) | (alpha << 24);
            }
        }
        overlay.render(graphics);
        assertArrayEquals("local=" + belowLocal + ", others=" + belowOthers + ", child=" + includeChild + ", gpu=" + gpu,
                pixels(expected), pixels(f.image));
        graphics.dispose(); reference.dispose();
    }

    @Test
    public void stackedActorsOnlyMaskSilhouettesDrawnInTheCurrentFrame() throws Exception
    {
        Fixture f = new Fixture(513, 385, 80, 0);
        Fixture otherShape = new Fixture(513, 385, 8, 0), npcShape = new Fixture(513, 385, 24, 0);
        Map<String, Object> worldData = worldValues(true);
        WorldView world = stub(WorldView.class, worldData);
        int[] models = new int[3], heights = new int[3];
        Player local = trackedActor(Player.class, f, world, 0, 0, models, heights);
        Player other = trackedActor(Player.class, otherShape, world, 0, 1, models, heights);
        NPC npc = trackedActor(NPC.class, npcShape, world, 0, 2, models, heights);
        Actor[] stack = {local, other, npc};
        worldData.put("players", indexed(local, other)); worldData.put("npcs", indexed(npc));
        Client client = stub(Client.class, values("isGpu", true, "getTopLevelWorldView", world,
                "getWorldView", world, "getLocalPlayer", local, "getViewportWidth", 513,
                "getViewportHeight", 385, "getScale", 512, "getCameraFpX", 1000f, "getCameraFpZ", -100f,
                "getBufferProvider", TriangleMaskRasterizerTest.buffer(f.image)));
        Map<String, Object> settings = values("overlaysBelowPlayer", true, "overlaysBelowOtherPlayers", true,
                "overlaysBelowAllNPCs", true, "crowdLimit", 80);
        Constructor<TileLayersOverlay> constructor = TileLayersOverlay.class
                .getDeclaredConstructor(Client.class, TileLayersConfig.class);
        constructor.setAccessible(true);
        TileLayersOverlay overlay = constructor.newInstance(client, stub(TileLayersConfig.class, settings));
        RenderedActors rendered = attachRenderedActors(overlay);
        BufferedImage expected = new BufferedImage(513, 385, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = f.image.createGraphics(), reference = expected.createGraphics();
        Actor[][] frames = {{other}, {local}, {npc}, {other, npc}, {}};
        for (int opacity : new int[]{0, 10, 50, 100})
        {
            settings.put("overlayOpacity", opacity);
            for (Actor[] visible : frames)
            {
                rendered.beginFrame(local);
                // Admission and overhead/UI callbacks can include actors that
                // lose the stack. Only actual scene-object draws count.
                for (Actor actor : stack)
                {
                    rendered.addEntity(actor, false); rendered.addEntity(actor, true);
                }
                Arrays.fill(pixels(f.image), 0); Arrays.fill(pixels(expected), 0);
                paintOverlay(graphics, 513, 385, true); paintOverlay(reference, 513, 385, true);
                int[] original = pixels(expected).clone();
                for (Actor actor : visible)
                {
                    recordDraw(rendered, actor);
                    if (opacity < 100) referenceMask(f, reference, actor);
                }
                for (int i = 0; i < original.length; i++)
                {
                    if (pixels(expected)[i] == 0 && original[i] != 0 && opacity > 0)
                    {
                        int alpha = ((original[i] >>> 24) * opacity + 50) / 100;
                        pixels(expected)[i] = (original[i] & 0xffffff) | (alpha << 24);
                    }
                }
                Arrays.fill(models, 0); Arrays.fill(heights, 0);
                overlay.render(graphics);
                assertArrayEquals("Only current visible silhouettes affect opacity " + opacity, pixels(expected), pixels(f.image));
                for (int i = 0; i < stack.length; i++)
                {
                    boolean drawn = false;
                    for (Actor actor : visible) if (actor == stack[i]) drawn = true;
                    int work = drawn && opacity < 100 ? 1 : 0;
                    assertEquals("Hidden stacked actors do no model work", work, models[i]);
                    assertEquals("Hidden stacked actors do no height work", work, heights[i]);
                }
            }
        }
        graphics.dispose(); reference.dispose();
    }

    private static RenderedActors attachRenderedActors(TileLayersOverlay overlay) throws Exception
    {
        RenderedActors rendered = new RenderedActors();
        Field field = TileLayersOverlay.class.getDeclaredField("renderedActors");
        field.setAccessible(true); field.set(overlay, rendered);
        return rendered;
    }

    private static void recordFrame(RenderedActors rendered, Player local, WorldView world)
    {
        rendered.beginFrame(local);
        recordWorld(rendered, world);
    }

    private static void recordWorld(RenderedActors rendered, WorldView world)
    {
        for (Player player : world.players()) recordDraw(rendered, player);
        for (NPC npc : world.npcs()) recordDraw(rendered, npc);
        for (WorldView child : world.worldViews()) recordWorld(rendered, child);
    }

    private static void recordDraw(RenderedActors rendered, Actor actor)
    {
        assertTrue(rendered.drawObject(null, stub(GameObject.class, values("getRenderable", actor))));
    }

    private static Player player(Fixture f, WorldView world, int x, int worldId)
    {
        return stub(Player.class, values("getModel", f.model, "getWorldView", world,
                "getLocalLocation", new LocalPoint(x, 1000, worldId), "getFootprintSize", 64));
    }

    private static Map<String, Object> worldValues(boolean topLevel)
    {
        return values("isTopLevel", topLevel, "getSizeX", 104, "getSizeY", 104,
                "getTileSettings", new byte[4][104][104], "worldViews", indexed());
    }

    @SafeVarargs
    private static <T> IndexedObjectSet<T> indexed(T... values)
    {
        return new IndexedObjectSet<T>()
        {
            @Override public T byIndex(int index) { return values[index]; }
            @Override public Iterator<T> iterator() { return Arrays.asList(values).iterator(); }
        };
    }
}

package com.tilelayers.labels;

import com.google.gson.Gson;
import com.tilelayers.TileLayersConfig;
import com.tilelayers.TileLayersOverlay;
import com.tilelayers.TileLayersPlugin;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayManager;
import org.junit.Test;
import static org.junit.Assert.*;
import static com.tilelayers.TestSupport.*;

final class RoutingTestSupport
{
    // Isolated client infrastructure for a routing test; no normal profile is loaded or written.
    static OverlayManager manager(Client client) throws Exception
    {
        ScheduledExecutorService executor = stub(ScheduledExecutorService.class, Collections.emptyMap());
        Constructor<?> configConstructor = ConfigManager.class.getDeclaredConstructors()[0];
        configConstructor.setAccessible(true);
        Object config = configConstructor.newInstance("tile-layers-test", executor, new EventBus(), client, new Gson(), null, null, null);
        Constructor<?> dataConstructor = Class.forName("net.runelite.client.config.ConfigData").getDeclaredConstructor(File.class);
        dataConstructor.setAccessible(true);
        File file = File.createTempFile("tile-layers-routing-", ".properties");
        file.deleteOnExit();
        set(config, "configProfile", dataConstructor.newInstance(file));
        Constructor<?> managerConstructor = OverlayManager.class.getDeclaredConstructors()[0];
        managerConstructor.setAccessible(true);
        return (OverlayManager) managerConstructor.newInstance(config, null, client, null, null, null, null);
    }
}

package com.tilelayers;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import net.runelite.client.config.ConfigItem;
import org.junit.Test;
import static org.junit.Assert.*;

public class ReleaseConfigTest
{
    @Test
    public void releasePreservesSavedKeysAndLabelDefaultsWithoutExperimentalOptions() throws Exception
    {
        TileLayersConfig config = new TileLayersConfig() { public void setTopNPCs(String value) { } };
        assertTrue(config.keepLootAboveCharacters()); assertTrue(config.onlyTileAndMarkerPlugins());
        assertEquals(200, config.characterLimit());
        ConfigItem self = TileLayersConfig.class.getMethod("overlaysBelowPlayer").getAnnotation(ConfigItem.class);
        assertEquals("currentTileBelowPlayer", self.keyName()); assertEquals("Draw overlays below self", self.name());
        ConfigItem labels = TileLayersConfig.class.getMethod("keepLootAboveCharacters").getAnnotation(ConfigItem.class);
        assertEquals("keepLootAboveCharacters", labels.keyName()); assertEquals("Keep labels and loot on top", labels.name());
        Set<Integer> positions = new HashSet<>();
        for (Method method : TileLayersConfig.class.getMethods())
        {
            ConfigItem item = method.getAnnotation(ConfigItem.class);
            if (item == null || method.getParameterCount() != 0) continue;
            assertTrue("Unique setting positions", positions.add(item.position()));
            assertFalse(item.keyName().startsWith("floorAllowance"));
            assertNotEquals("objectMasking", item.keyName());
            assertNotEquals("overlaysBelowProjectiles", item.keyName());
        }
    }
}

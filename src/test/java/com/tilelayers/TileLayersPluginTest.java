package com.tilelayers;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class TileLayersPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(TileLayersPlugin.class);
		RuneLite.main(args);
	}
}
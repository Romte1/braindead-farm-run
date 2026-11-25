package com.romte.farmrunplanner;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FarmRunPlannerPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(FarmRunPlannerPlugin.class);
		RuneLite.main(args);
	}
}

package com.romte.braindeadfarmrun;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BraindeadFarmRunPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BraindeadFarmRunPlugin.class);
		RuneLite.main(args);
	}
}

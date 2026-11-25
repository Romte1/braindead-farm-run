package com.romte.farmrunplanner;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Farm Run Planner",
	description = "Plans allotment, herb, tree and fruit tree runs: what to bring, where to go and when to return",
	tags = {"farming", "farm run", "herb run", "tree run", "fruit tree", "allotment", "timers", "planner"}
)
public class FarmRunPlannerPlugin extends Plugin
{
	@Inject
	private FarmRunPlannerConfig config;

	@Override
	protected void startUp()
	{
		log.debug("Farm Run Planner started (debug={})", config.debug());
	}

	@Override
	protected void shutDown()
	{
		log.debug("Farm Run Planner stopped");
	}

	@Provides
	FarmRunPlannerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(FarmRunPlannerConfig.class);
	}
}

package com.romte.farmrunplanner;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(FarmRunPlannerConfig.GROUP)
public interface FarmRunPlannerConfig extends Config
{
	String GROUP = "farmrunplanner";

	@ConfigSection(
		name = "Advanced",
		description = "Diagnostics and troubleshooting",
		position = 0
	)
	String advancedSection = "advanced";

	@ConfigItem(
		keyName = "debug",
		name = "Debug logging",
		description = "Write detailed diagnostics to the RuneLite log",
		section = advancedSection
	)
	default boolean debug()
	{
		return false;
	}
}

package com.romte.braindeadfarmrun;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BraindeadFarmRunConfig.GROUP)
public interface BraindeadFarmRunConfig extends Config
{
	String GROUP = "braindeadfarmrun";

	@ConfigSection(
		name = "Timers",
		description = "Where patch timers come from",
		position = 0
	)
	String timersSection = "timers";

	@ConfigItem(
		keyName = "importTimeTracking",
		name = "Import Time Tracking timers",
		description = "Read the timers RuneLite's Time Tracking plugin already saved when you log in or enable this plugin",
		section = timersSection
	)
	default boolean importTimeTracking()
	{
		return true;
	}

	@ConfigSection(
		name = "Advanced",
		description = "Diagnostics and troubleshooting",
		position = 1
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

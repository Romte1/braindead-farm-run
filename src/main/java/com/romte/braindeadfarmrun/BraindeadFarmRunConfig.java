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
		name = "Bank",
		description = "What the bank supply tab uses",
		position = 1
	)
	String bankSection = "bank";

	@ConfigItem(
		keyName = "bottomlessCompostBucket",
		name = "Bottomless compost bucket",
		description = "Use the filled bottomless compost bucket. If disabled, the tab uses the best compost available in your bank (ultracompost, supercompost or compost)",
		section = bankSection
	)
	default boolean bottomlessCompostBucket()
	{
		return true;
	}

	@ConfigSection(
		name = "Advanced",
		description = "Diagnostics and troubleshooting",
		position = 2
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

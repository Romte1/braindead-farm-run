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

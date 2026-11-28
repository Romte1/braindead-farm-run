package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.SeedSelection;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(BraindeadFarmRunConfig.GROUP)
public interface BraindeadFarmRunConfig extends Config
{
	String GROUP = "braindeadfarmrun";
	String RUN_TYPES_KEY = "runTypes";
	String HERB_SEED_KEY = "herbSeed";
	String TREE_SEED_KEY = "treeSeed";
	String FRUIT_TREE_SEED_KEY = "fruitTreeSeed";
	String ALLOTMENT_SEED_KEY = "allotmentSeed";

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

	@ConfigItem(
		keyName = RUN_TYPES_KEY,
		name = "Last run types",
		description = "Patch types that were selected the last time the panel was used",
		hidden = true
	)
	default String runTypes()
	{
		return "HERB,TREE,FRUIT_TREE,ALLOTMENT";
	}

	@ConfigItem(
		keyName = HERB_SEED_KEY,
		name = "Herbs seed",
		description = "Seed used for herb patches",
		hidden = true
	)
	default String herbSeed()
	{
		return SeedSelection.BEST;
	}

	@ConfigItem(
		keyName = TREE_SEED_KEY,
		name = "Trees sapling",
		description = "Sapling used for tree patches",
		hidden = true
	)
	default String treeSeed()
	{
		return SeedSelection.BEST;
	}

	@ConfigItem(
		keyName = FRUIT_TREE_SEED_KEY,
		name = "Fruit trees sapling",
		description = "Sapling used for fruit tree patches",
		hidden = true
	)
	default String fruitTreeSeed()
	{
		return SeedSelection.BEST;
	}

	@ConfigItem(
		keyName = ALLOTMENT_SEED_KEY,
		name = "Allotments seed",
		description = "Seed used for allotment patches",
		hidden = true
	)
	default String allotmentSeed()
	{
		return SeedSelection.BEST;
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

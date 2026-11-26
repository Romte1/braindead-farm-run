package com.romte.braindeadfarmrun.domain.patch;

/**
 * The patch types supported by the first release. More types can be added later without
 * changing the planner contract.
 */
public enum PatchType
{
	HERB("Herbs", 20, Clear.HARVEST),
	ALLOTMENT("Allotments", 10, Clear.HARVEST),
	TREE("Trees", 40, Clear.GARDENER),
	FRUIT_TREE("Fruit trees", 160, Clear.GARDENER);

	/** How a grown crop is removed before replanting. */
	public enum Clear
	{
		HARVEST,
		GARDENER
	}

	private final String displayName;
	private final int tickMinutes;
	private final Clear clear;

	PatchType(String displayName, int tickMinutes, Clear clear)
	{
		this.displayName = displayName;
		this.tickMinutes = tickMinutes;
		this.clear = clear;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	/** Minutes between growth ticks for this patch type. */
	public int getTickMinutes()
	{
		return tickMinutes;
	}

	public Clear getClear()
	{
		return clear;
	}
}

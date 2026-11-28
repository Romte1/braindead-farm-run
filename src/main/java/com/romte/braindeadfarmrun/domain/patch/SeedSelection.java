package com.romte.braindeadfarmrun.domain.patch;

/**
 * Resolves the seed or sapling the player wants to plant for a patch type. {@link #BEST} keeps
 * the previous automatic behaviour: the highest-level crop the player can plant at their current
 * Farming level.
 */
public final class SeedSelection
{
	public static final String BEST = "BEST";

	private SeedSelection()
	{
	}

	/** Highest-level crop of this type that the given Farming level allows, or null. */
	public static Crop best(PatchType type, int farmingLevel)
	{
		Crop best = null;
		for (Crop crop : Crop.forType(type))
		{
			if (crop.getLevel() <= farmingLevel)
			{
				best = crop;
			}
		}
		return best;
	}

	/**
	 * Resolves a saved config value. Unknown or mismatched values fall back to {@link #best},
	 * so an old selection can never plant a crop from another patch type.
	 */
	public static Crop resolve(PatchType type, String selected, int farmingLevel)
	{
		if (selected == null || selected.isEmpty() || BEST.equalsIgnoreCase(selected))
		{
			return best(type, farmingLevel);
		}
		try
		{
			Crop crop = Crop.valueOf(selected);
			return crop.getType() == type ? crop : best(type, farmingLevel);
		}
		catch (IllegalArgumentException e)
		{
			return best(type, farmingLevel);
		}
	}

	/** Label used by the panel's custom-seed dropdowns. */
	public static String label(Crop crop)
	{
		return crop.getPlantedItemName() + " (" + crop.getLevel() + ")";
	}
}

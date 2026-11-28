package com.romte.braindeadfarmrun.domain.patch;

/**
 * Unlocks that make a farmer watch a patch without payment.
 */
public final class FreeProtection
{
	public static final FreeProtection NONE = new FreeProtection(false, false, false);

	private final boolean hosidius;
	private final boolean faladorTree;
	private final boolean farmingGuildAllotments;

	public FreeProtection(boolean hosidius, boolean faladorTree, boolean farmingGuildAllotments)
	{
		this.hosidius = hosidius;
		this.faladorTree = faladorTree;
		this.farmingGuildAllotments = farmingGuildAllotments;
	}

	public boolean appliesTo(PatchDefinition patch)
	{
		if (patch == null)
		{
			return false;
		}
		if (patch.getType() == PatchType.ALLOTMENT && patch.isFarmingGuild())
		{
			return farmingGuildAllotments;
		}
		if (patch.isHosidius() && (patch.getType() == PatchType.ALLOTMENT
			|| patch.getType() == PatchType.HERB))
		{
			return hosidius;
		}
		return faladorTree && "falador_tree".equals(patch.getId());
	}

	public boolean isHosidius()
	{
		return hosidius;
	}

	public boolean isFaladorTree()
	{
		return faladorTree;
	}

	public boolean isFarmingGuildAllotments()
	{
		return farmingGuildAllotments;
	}
}

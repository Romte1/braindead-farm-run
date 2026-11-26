package com.romte.braindeadfarmrun.data;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;

/**
 * Maps a loaded patch object (region, varbit and tile) to its static definition. When several
 * patches share a varbit, the nearest tile wins.
 */
public final class PatchLocator
{
	private PatchLocator()
	{
	}

	public static PatchDefinition find(int region, int varbit, int x, int y)
	{
		PatchDefinition best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (PatchDefinition patch : Patches.all())
		{
			if (patch.getVarbit() != varbit || !patch.isInRegion(region))
			{
				continue;
			}
			int distance = Math.max(Math.abs(patch.getX() - x), Math.abs(patch.getY() - y));
			if (distance < bestDistance)
			{
				best = patch;
				bestDistance = distance;
			}
		}
		return best;
	}
}

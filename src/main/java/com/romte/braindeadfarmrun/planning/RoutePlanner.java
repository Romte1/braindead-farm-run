package com.romte.braindeadfarmrun.planning;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.ArrayList;
import java.util.List;

/**
 * Greedy nearest-neighbour route over the selected patches. Shortest Path still works out the
 * real walking/teleport route for each stop; this only decides the visit order.
 */
public final class RoutePlanner
{
	private RoutePlanner()
	{
	}

	public static List<PatchDefinition> plan(List<PatchDefinition> patches, int startX, int startY)
	{
		List<PatchDefinition> remaining = new ArrayList<>(patches);
		List<PatchDefinition> route = new ArrayList<>(patches.size());
		int x = startX;
		int y = startY;

		while (!remaining.isEmpty())
		{
			int bestIndex = 0;
			int bestDistance = Integer.MAX_VALUE;
			for (int i = 0; i < remaining.size(); i++)
			{
				PatchDefinition patch = remaining.get(i);
				int distance = distance(x, y, patch.getX(), patch.getY());
				if (distance < bestDistance)
				{
					bestDistance = distance;
					bestIndex = i;
				}
			}
			PatchDefinition next = remaining.remove(bestIndex);
			route.add(next);
			x = next.getX();
			y = next.getY();
		}
		return guildAndSeaweedLast(route);
	}

	/**
	 * Farming Guild patches are saved for the end of the run, and the underwater seaweed patches
	 * stay after them because Shortest Path cannot use them as an intermediate leg.
	 */
	private static List<PatchDefinition> guildAndSeaweedLast(List<PatchDefinition> route)
	{
		List<PatchDefinition> ordered = new ArrayList<>(route.size());
		List<PatchDefinition> guild = new ArrayList<>();
		List<PatchDefinition> seaweed = new ArrayList<>();
		for (PatchDefinition patch : route)
		{
			if (patch.getType() == PatchType.SEAWEED)
			{
				seaweed.add(patch);
			}
			else if (patch.isFarmingGuild())
			{
				guild.add(patch);
			}
			else
			{
				ordered.add(patch);
			}
		}
		ordered.addAll(guild);
		ordered.addAll(seaweed);
		return ordered;
	}

	private static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}
}

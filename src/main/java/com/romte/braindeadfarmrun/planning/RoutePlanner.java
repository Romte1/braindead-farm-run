package com.romte.braindeadfarmrun.planning;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
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
		return route;
	}

	private static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}
}

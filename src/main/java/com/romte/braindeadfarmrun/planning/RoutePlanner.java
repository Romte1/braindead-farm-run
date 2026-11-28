package com.romte.braindeadfarmrun.planning;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Groups patches that share a location, then walks the groups with a greedy nearest-neighbour
 * order. Shortest Path still works out the real walking/teleport route for each stop; this only
 * decides the visit order.
 */
public final class RoutePlanner
{
	/** Patches this close to each other are treated as one stop area. */
	private static final int SITE_RADIUS = 60;

	private RoutePlanner()
	{
	}

	public static List<PatchDefinition> plan(List<PatchDefinition> patches, int startX, int startY)
	{
		List<PatchDefinition> normal = new ArrayList<>();
		List<PatchDefinition> guild = new ArrayList<>();
		List<PatchDefinition> seaweed = new ArrayList<>();
		for (PatchDefinition patch : patches)
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
				normal.add(patch);
			}
		}

		List<PatchDefinition> route = routeClusters(normal, startX, startY);
		PatchDefinition last = route.isEmpty() ? null : route.get(route.size() - 1);
		int x = last == null ? startX : last.getX();
		int y = last == null ? startY : last.getY();
		for (List<PatchDefinition> group : List.of(guild, seaweed))
		{
			List<PatchDefinition> next = routeClusters(group, x, y);
			route.addAll(next);
			if (!next.isEmpty())
			{
				PatchDefinition end = next.get(next.size() - 1);
				x = end.getX();
				y = end.getY();
			}
		}
		return route;
	}

	/**
	 * Groups nearby patches (for example a herb patch and the two allotments at the same bank)
	 * so they are done together, then orders the groups by nearest entry point.
	 */
	private static List<PatchDefinition> routeClusters(List<PatchDefinition> patches, int startX,
		int startY)
	{
		List<List<PatchDefinition>> groups = clusters(patches);
		List<PatchDefinition> route = new ArrayList<>(patches.size());
		int x = startX;
		int y = startY;
		while (!groups.isEmpty())
		{
			int bestGroup = 0;
			int bestDistance = Integer.MAX_VALUE;
			for (int i = 0; i < groups.size(); i++)
			{
				for (PatchDefinition patch : groups.get(i))
				{
					int distance = distance(x, y, patch.getX(), patch.getY());
					if (distance < bestDistance)
					{
						bestDistance = distance;
						bestGroup = i;
					}
				}
			}
			List<PatchDefinition> group = groups.remove(bestGroup);
			List<PatchDefinition> ordered = nearestOrder(group, x, y);
			route.addAll(ordered);
			PatchDefinition last = ordered.get(ordered.size() - 1);
			x = last.getX();
			y = last.getY();
		}
		return route;
	}

	private static List<List<PatchDefinition>> clusters(List<PatchDefinition> patches)
	{
		int size = patches.size();
		int[] parent = new int[size];
		for (int i = 0; i < size; i++)
		{
			parent[i] = i;
		}
		for (int i = 0; i < size; i++)
		{
			for (int j = i + 1; j < size; j++)
			{
				PatchDefinition a = patches.get(i);
				PatchDefinition b = patches.get(j);
				if (distance(a.getX(), a.getY(), b.getX(), b.getY()) <= SITE_RADIUS)
				{
					union(parent, i, j);
				}
			}
		}
		Map<Integer, List<PatchDefinition>> grouped = new LinkedHashMap<>();
		for (int i = 0; i < size; i++)
		{
			int root = find(parent, i);
			grouped.computeIfAbsent(root, key -> new ArrayList<>()).add(patches.get(i));
		}
		return new ArrayList<>(grouped.values());
	}

	private static List<PatchDefinition> nearestOrder(List<PatchDefinition> patches, int startX,
		int startY)
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

	private static int find(int[] parent, int value)
	{
		while (parent[value] != value)
		{
			parent[value] = parent[parent[value]];
			value = parent[value];
		}
		return value;
	}

	private static void union(int[] parent, int a, int b)
	{
		int rootA = find(parent, a);
		int rootB = find(parent, b);
		if (rootA != rootB)
		{
			parent[rootB] = rootA;
		}
	}

	private static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}
}

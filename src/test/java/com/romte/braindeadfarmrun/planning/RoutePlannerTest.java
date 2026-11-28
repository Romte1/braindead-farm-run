package com.romte.braindeadfarmrun.planning;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class RoutePlannerTest
{
	@Test
	public void nearestNeighbourFromStart()
	{
		PatchDefinition far = patch("far", 10, 0);
		PatchDefinition near = patch("near", 1, 0);
		PatchDefinition middle = patch("middle", 2, 0);

		List<PatchDefinition> route = RoutePlanner.plan(Arrays.asList(far, near, middle), 0, 0);

		assertEquals(3, route.size());
		assertEquals("near", route.get(0).getId());
		assertEquals("middle", route.get(1).getId());
		assertEquals("far", route.get(2).getId());
	}

	@Test
	public void nearestNeighbourUsesBothAxes()
	{
		PatchDefinition diagonal = patch("diagonal", 5, 5);
		PatchDefinition close = patch("close", 1, 1);
		PatchDefinition next = patch("next", 2, 2);

		List<PatchDefinition> route = RoutePlanner.plan(Arrays.asList(diagonal, close, next), 0, 0);

		assertEquals("close", route.get(0).getId());
		assertEquals("next", route.get(1).getId());
		assertEquals("diagonal", route.get(2).getId());
	}

	@Test
	public void emptyAndSinglePatch()
	{
		assertTrue(RoutePlanner.plan(Collections.emptyList(), 0, 0).isEmpty());

		PatchDefinition only = patch("only", 3, 4);
		List<PatchDefinition> route = RoutePlanner.plan(Collections.singletonList(only), 0, 0);
		assertEquals(1, route.size());
		assertEquals("only", route.get(0).getId());
	}

	@Test
	public void seaweedIsAlwaysLast()
	{
		PatchDefinition herb = patch("herb", 20, 20);
		PatchDefinition seaweed = patch("seaweed", 1, 1, PatchType.SEAWEED);

		List<PatchDefinition> route = RoutePlanner.plan(Arrays.asList(herb, seaweed), 0, 0);

		assertEquals("herb", route.get(0).getId());
		assertEquals("seaweed", route.get(1).getId());
	}

	private static PatchDefinition patch(String id, int x, int y)
	{
		return patch(id, x, y, PatchType.HERB);
	}

	private static PatchDefinition patch(String id, int x, int y, PatchType type)
	{
		return new PatchDefinition(id, id, type, x, y, 0, new int[]{1}, 1, null, null);
	}
}

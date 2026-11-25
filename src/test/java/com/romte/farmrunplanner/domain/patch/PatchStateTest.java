package com.romte.farmrunplanner.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public class PatchStateTest
{
	private static final Instant PLANTED = Instant.parse("2026-01-01T00:00:00Z");
	private static final String PATCH = "falador_herb";

	@Test
	public void emptyPatch()
	{
		PatchState state = PatchState.empty(PATCH);
		assertEquals(PatchState.Status.EMPTY, state.statusAt(PLANTED));
		assertNull(state.getCrop());
	}

	@Test
	public void growingAndReady()
	{
		PatchState state = PatchState.planted(PATCH, Crop.GUAM, PLANTED, false);
		assertEquals(PatchState.Status.GROWING, state.statusAt(PLANTED));
		assertEquals(PatchState.Status.GROWING,
			state.statusAt(PLANTED.plus(Duration.ofMinutes(79))));
		assertEquals(PatchState.Status.READY,
			state.statusAt(PLANTED.plus(Duration.ofMinutes(80))));
	}

	@Test
	public void diseaseAndDeathOverrideGrowth()
	{
		PatchState state = PatchState.planted(PATCH, Crop.GUAM, PLANTED, false);

		PatchState diseased = state.withDiseased(true);
		assertEquals(PatchState.Status.DISEASED, diseased.statusAt(PLANTED));
		assertEquals(PatchState.Status.GROWING, state.statusAt(PLANTED));

		PatchState dead = diseased.withDead(true);
		assertEquals(PatchState.Status.DEAD, dead.statusAt(PLANTED));
	}

	@Test
	public void clearingResetsThePatch()
	{
		PatchState state = PatchState.planted(PATCH, Crop.GUAM, PLANTED, true).cleared();
		assertEquals(PatchState.Status.EMPTY, state.statusAt(PLANTED));
		assertNull(state.getCrop());
		assertTrue(state.getPatchId().equals(PATCH));
	}
}

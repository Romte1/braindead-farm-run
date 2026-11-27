package com.romte.braindeadfarmrun.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public class PatchStateMapperTest
{
	private static final String PATCH = "falador_herb";
	private static final Instant NOW = Instant.parse("2026-01-01T12:00:00Z");
	private final PatchStates states = PatchStates.get();

	@Test
	public void growingUsesStageToEstimatePlantingTime()
	{
		PatchState state = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 6), null, NOW);
		assertEquals(Crop.GUAM, state.getCrop());
		assertEquals(PatchState.Status.GROWING, state.statusAt(NOW));
		assertEquals(NOW.minus(Duration.ofMinutes(40)), state.getPlantedAt());
	}

	@Test
	public void readyUsesFullGrowthTime()
	{
		PatchState state = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 8), null, NOW);
		assertEquals(Crop.GUAM, state.getCrop());
		assertEquals(PatchState.Status.READY, state.statusAt(NOW));
		assertEquals(NOW.minus(Duration.ofMinutes(80)), state.getPlantedAt());
	}

	@Test
	public void previousPlantingTimeIsKeptForTheSameCrop()
	{
		Instant plantedAt = NOW.minus(Duration.ofMinutes(10));
		PatchState previous = PatchState.planted(PATCH, Crop.GUAM, plantedAt, true);
		PatchState state = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 4), previous, NOW);
		assertSame(plantedAt, state.getPlantedAt());
		assertTrue(state.isPaid());
	}

	@Test
	public void emptyClearsThePatch()
	{
		PatchState previous = PatchState.planted(PATCH, Crop.GUAM, NOW.minus(Duration.ofMinutes(10)), true);
		PatchState state = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 0), previous, NOW);
		assertEquals(PatchState.Status.EMPTY, state.statusAt(NOW));
		assertNull(state.getCrop());
	}

	@Test
	public void unknownKeepsPreviousState()
	{
		PatchState previous = PatchState.planted(PATCH, Crop.GUAM, NOW.minus(Duration.ofMinutes(10)), true);
		assertSame(previous, PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 99999), previous, NOW));
		assertEquals(PatchState.Status.EMPTY,
			PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 99999), null, NOW).statusAt(NOW));
	}

	@Test
	public void diseaseDeathAndStump()
	{
		PatchState previous = PatchState.planted("apple", Crop.APPLE, NOW.minus(Duration.ofMinutes(60)), false);
		PatchState diseased = PatchStateMapper.apply("apple", states.decode(PatchType.FRUIT_TREE, 21), previous, NOW);
		assertEquals(PatchState.Status.DISEASED, diseased.statusAt(NOW));
		assertEquals(Crop.APPLE, diseased.getCrop());

		PatchState dead = PatchStateMapper.apply("apple", states.decode(PatchType.FRUIT_TREE, 27), previous, NOW);
		assertEquals(PatchState.Status.DEAD, dead.statusAt(NOW));

		PatchState stump = PatchStateMapper.apply("oak", states.decode(PatchType.TREE, 14), null, NOW);
		assertEquals(PatchState.Status.STUMP, stump.statusAt(NOW));
		assertEquals(Crop.OAK, stump.getCrop());
	}

	@Test
	public void checkHealthIsReady()
	{
		PatchState state = PatchStateMapper.apply("oak", states.decode(PatchType.TREE, 12), null, NOW);
		assertEquals(PatchState.Status.READY, state.statusAt(NOW));
		assertTrue(state.isCheckHealth());
		assertFalse(state.isFell());
	}

	@Test
	public void unknownCropKeepsPreviousCropWhenPossible()
	{
		PatchState previous = PatchState.planted(PATCH, Crop.GUAM, NOW.minus(Duration.ofMinutes(30)), false);
		PatchState dead = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 170), previous, NOW);
		assertEquals(Crop.GUAM, dead.getCrop());
		assertEquals(PatchState.Status.DEAD, dead.statusAt(NOW));
	}

	@Test
	public void timeTrackingObservationUsesTheChangeTime()
	{
		Instant changedAt = NOW.minus(Duration.ofHours(2));

		PatchState growing = PatchStateMapper.applyAt(PATCH, states.decode(PatchType.HERB, 6), null, changedAt);
		assertEquals(changedAt.minus(Duration.ofMinutes(40)), growing.getPlantedAt());
		assertEquals(PatchState.Status.READY, growing.statusAt(NOW));

		PatchState ready = PatchStateMapper.applyAt(PATCH, states.decode(PatchType.HERB, 8), null, changedAt);
		assertEquals(changedAt.minus(Duration.ofMinutes(80)), ready.getPlantedAt());
		assertEquals(PatchState.Status.READY, ready.statusAt(NOW));
	}

	@Test
	public void changingCropClearsPaidFlag()
	{
		PatchState previous = PatchState.planted(PATCH, Crop.GUAM, NOW.minus(Duration.ofMinutes(30)), true);
		PatchState state = PatchStateMapper.apply(PATCH, states.decode(PatchType.HERB, 11), previous, NOW);
		assertEquals(Crop.MARRENTILL, state.getCrop());
		assertFalse(state.isPaid());
	}
}

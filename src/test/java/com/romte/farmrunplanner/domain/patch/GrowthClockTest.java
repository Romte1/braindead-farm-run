package com.romte.farmrunplanner.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.Test;

public class GrowthClockTest
{
	private static final Instant PLANTED = Instant.parse("2026-01-01T00:00:00Z");

	@Test
	public void readyAtUsesGrowthDuration()
	{
		assertEquals(PLANTED.plus(Duration.ofMinutes(80)), GrowthClock.readyAt(Crop.GUAM, PLANTED));
		assertEquals(PLANTED.plus(Duration.ofMinutes(40)), GrowthClock.readyAt(Crop.POTATO, PLANTED));
		assertEquals(PLANTED.plus(Duration.ofMinutes(160)), GrowthClock.readyAt(Crop.OAK, PLANTED));
		assertEquals(PLANTED.plus(Duration.ofMinutes(960)), GrowthClock.readyAt(Crop.APPLE, PLANTED));
	}

	@Test
	public void readyBoundary()
	{
		Instant justBefore = PLANTED.plus(Duration.ofMinutes(80)).minusSeconds(1);
		Instant ready = PLANTED.plus(Duration.ofMinutes(80));

		assertFalse(GrowthClock.isReady(Crop.GUAM, PLANTED, justBefore));
		assertTrue(GrowthClock.isReady(Crop.GUAM, PLANTED, ready));
		assertTrue(GrowthClock.isReady(Crop.GUAM, PLANTED, ready.plusSeconds(1)));
	}

	@Test
	public void remainingIsClampedToZero()
	{
		Instant halfway = PLANTED.plus(Duration.ofMinutes(30));
		assertEquals(Duration.ofMinutes(50), GrowthClock.remaining(Crop.GUAM, PLANTED, halfway));
		assertEquals(Duration.ZERO, GrowthClock.remaining(Crop.GUAM, PLANTED,
			PLANTED.plus(Duration.ofMinutes(80))));
		assertEquals(Duration.ZERO, GrowthClock.remaining(Crop.GUAM, PLANTED,
			PLANTED.plus(Duration.ofDays(2))));
	}
}

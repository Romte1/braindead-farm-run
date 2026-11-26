package com.romte.braindeadfarmrun.domain.patch;

import java.time.Duration;
import java.time.Instant;

/**
 * Pure growth-time calculations. No RuneLite types, so it is fully unit-testable.
 */
public final class GrowthClock
{
	private GrowthClock()
	{
	}

	public static Duration growthDuration(Crop crop)
	{
		return Duration.ofMinutes(crop.getGrowthMinutes());
	}

	public static Instant readyAt(Crop crop, Instant plantedAt)
	{
		return plantedAt.plus(growthDuration(crop));
	}

	public static boolean isReady(Crop crop, Instant plantedAt, Instant now)
	{
		return !now.isBefore(readyAt(crop, plantedAt));
	}

	/** Time left until ready, or zero once the crop is ready. */
	public static Duration remaining(Crop crop, Instant plantedAt, Instant now)
	{
		Duration remaining = Duration.between(now, readyAt(crop, plantedAt));
		return remaining.isNegative() ? Duration.ZERO : remaining;
	}
}

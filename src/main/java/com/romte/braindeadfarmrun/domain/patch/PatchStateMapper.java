package com.romte.braindeadfarmrun.domain.patch;

import java.time.Duration;
import java.time.Instant;

/**
 * Converts a decoded varbit state into an immutable {@link PatchState}. The previous state is
 * used to keep the original planting time and payment flag when the crop has not changed.
 */
public final class PatchStateMapper
{
	private PatchStateMapper()
	{
	}

	public static PatchState apply(String patchId, PatchStates.Decoded decoded, PatchState previous,
		Instant now)
	{
		switch (decoded.getState())
		{
			case EMPTY:
				return PatchState.empty(patchId);
			case UNKNOWN:
				return previous != null ? previous : PatchState.empty(patchId);
			case GROWING:
			case READY:
			case DISEASED:
			case DEAD:
			case STUMP:
				Crop crop = decoded.getCrop() != null ? decoded.getCrop()
					: previous != null ? previous.getCrop() : null;
				if (crop == null)
				{
					return previous != null ? previous : PatchState.empty(patchId);
				}
				Instant plantedAt = plantedAt(crop, decoded, previous, now);
				boolean paid = previous != null && previous.getCrop() == crop && previous.isPaid();
				return PatchState.planted(patchId, crop, plantedAt, paid)
					.withDiseased(decoded.getState() == PatchStates.State.DISEASED)
					.withDead(decoded.getState() == PatchStates.State.DEAD)
					.withStump(decoded.getState() == PatchStates.State.STUMP)
					.withCheckHealth(decoded.isCheckHealth())
					.withFell(decoded.isFell());
			default:
				return previous != null ? previous : PatchState.empty(patchId);
		}
	}

	private static Instant plantedAt(Crop crop, PatchStates.Decoded decoded, PatchState previous,
		Instant now)
	{
		if (previous != null && previous.getCrop() == crop && previous.getPlantedAt() != null)
		{
			return previous.getPlantedAt();
		}

		int elapsedMinutes;
		switch (decoded.getState())
		{
			case READY:
			case STUMP:
				elapsedMinutes = crop.getGrowthMinutes();
				break;
			default:
				elapsedMinutes = Math.max(0, decoded.getStage() * crop.getType().getTickMinutes());
		}
		return now.minus(Duration.ofMinutes(elapsedMinutes));
	}
}

package com.romte.braindeadfarmrun.domain.patch;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable state of one patch. The status is derived from the crop, timestamps and disease
 * flags so it can never go stale.
 */
public final class PatchState
{
	public enum Status
	{
		EMPTY,
		GROWING,
		READY,
		DISEASED,
		DEAD
	}

	private final String patchId;
	private final Crop crop;
	private final Instant plantedAt;
	private final boolean paid;
	private final boolean diseased;
	private final boolean dead;

	private PatchState(String patchId, Crop crop, Instant plantedAt, boolean paid,
		boolean diseased, boolean dead)
	{
		this.patchId = Objects.requireNonNull(patchId, "patchId");
		this.crop = crop;
		this.plantedAt = plantedAt;
		this.paid = paid;
		this.diseased = diseased;
		this.dead = dead;
	}

	public static PatchState empty(String patchId)
	{
		return new PatchState(patchId, null, null, false, false, false);
	}

	public static PatchState planted(String patchId, Crop crop, Instant plantedAt, boolean paid)
	{
		return new PatchState(patchId, Objects.requireNonNull(crop, "crop"),
			Objects.requireNonNull(plantedAt, "plantedAt"), paid, false, false);
	}

	public PatchState withDiseased(boolean diseased)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead);
	}

	public PatchState withDead(boolean dead)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead);
	}

	public PatchState cleared()
	{
		return empty(patchId);
	}

	public Status statusAt(Instant now)
	{
		if (crop == null)
		{
			return Status.EMPTY;
		}
		if (dead)
		{
			return Status.DEAD;
		}
		if (diseased)
		{
			return Status.DISEASED;
		}
		return GrowthClock.isReady(crop, plantedAt, now) ? Status.READY : Status.GROWING;
	}

	public String getPatchId()
	{
		return patchId;
	}

	public Crop getCrop()
	{
		return crop;
	}

	public Instant getPlantedAt()
	{
		return plantedAt;
	}

	public boolean isPaid()
	{
		return paid;
	}

	public boolean isDiseased()
	{
		return diseased;
	}

	public boolean isDead()
	{
		return dead;
	}
}

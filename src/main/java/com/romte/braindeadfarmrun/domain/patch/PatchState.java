package com.romte.braindeadfarmrun.domain.patch;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable state of one patch. The status is derived from the crop, timestamps and flags so it
 * can never go stale.
 */
public final class PatchState
{
	public enum Status
	{
		EMPTY,
		GROWING,
		READY,
		STUMP,
		DISEASED,
		DEAD
	}

	private final String patchId;
	private final Crop crop;
	private final Instant plantedAt;
	private final boolean paid;
	private final boolean diseased;
	private final boolean dead;
	private final boolean stump;
	private final boolean checkHealth;
	private final boolean fell;

	private PatchState(String patchId, Crop crop, Instant plantedAt, boolean paid,
		boolean diseased, boolean dead, boolean stump, boolean checkHealth, boolean fell)
	{
		this.patchId = Objects.requireNonNull(patchId, "patchId");
		this.crop = crop;
		this.plantedAt = plantedAt;
		this.paid = paid;
		this.diseased = diseased;
		this.dead = dead;
		this.stump = stump;
		this.checkHealth = checkHealth;
		this.fell = fell;
	}

	public static PatchState empty(String patchId)
	{
		return new PatchState(patchId, null, null, false, false, false, false, false, false);
	}

	public static PatchState planted(String patchId, Crop crop, Instant plantedAt, boolean paid)
	{
		return new PatchState(patchId, Objects.requireNonNull(crop, "crop"),
			Objects.requireNonNull(plantedAt, "plantedAt"), paid, false, false, false, false, false);
	}

	public PatchState withDiseased(boolean diseased)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead, stump, checkHealth, fell);
	}

	public PatchState withDead(boolean dead)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead, stump, checkHealth, fell);
	}

	public PatchState withStump(boolean stump)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead, stump, checkHealth, fell);
	}

	public PatchState withCheckHealth(boolean checkHealth)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead, stump, checkHealth, fell);
	}

	public PatchState withFell(boolean fell)
	{
		return new PatchState(patchId, crop, plantedAt, paid, diseased, dead, stump, checkHealth, fell);
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
		if (stump)
		{
			return Status.STUMP;
		}
		if (checkHealth)
		{
			return Status.READY;
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

	public boolean isStump()
	{
		return stump;
	}

	public boolean isCheckHealth()
	{
		return checkHealth;
	}

	/** Grown tree that must be chopped down before replanting. */
	public boolean isFell()
	{
		return fell;
	}
}

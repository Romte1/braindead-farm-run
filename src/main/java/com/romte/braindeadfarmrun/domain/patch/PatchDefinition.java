package com.romte.braindeadfarmrun.domain.patch;

import java.util.Objects;

/**
 * Static definition of one farming patch. Instances are immutable and contain no RuneLite
 * types, so they can be used from tests and background planners.
 */
public final class PatchDefinition
{
	private final String id;
	private final String displayName;
	private final PatchType type;
	private final int x;
	private final int y;
	private final int plane;
	private final int[] regions;
	private final int varbit;
	private final String nearbyRing;
	private final String bank;

	public PatchDefinition(String id, String displayName, PatchType type, int x, int y, int plane,
		int[] regions, int varbit, String nearbyRing, String bank)
	{
		this.id = Objects.requireNonNull(id, "id");
		this.displayName = Objects.requireNonNull(displayName, "displayName");
		this.type = Objects.requireNonNull(type, "type");
		this.x = x;
		this.y = y;
		this.plane = plane;
		this.regions = Objects.requireNonNull(regions, "regions").clone();
		this.varbit = varbit;
		this.nearbyRing = nearbyRing;
		this.bank = bank;
	}

	public String getId()
	{
		return id;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public PatchType getType()
	{
		return type;
	}

	public int getX()
	{
		return x;
	}

	public int getY()
	{
		return y;
	}

	public int getPlane()
	{
		return plane;
	}

	/** Defensive copy; use {@link #isInRegion(int)} in hot paths. */
	public int[] getRegions()
	{
		return regions.clone();
	}

	/** Main region, used as the persistence key. */
	public int getRegionId()
	{
		return regions[0];
	}

	public int getVarbit()
	{
		return varbit;
	}

	public String getNearbyRing()
	{
		return nearbyRing;
	}

	public String getBank()
	{
		return bank;
	}

	/** True for the patches inside the Farming Guild. */
	public boolean isFarmingGuild()
	{
		return bank != null && bank.startsWith("Farming Guild");
	}

	public boolean isInRegion(int region)
	{
		for (int candidate : regions)
		{
			if (candidate == region)
			{
				return true;
			}
		}
		return false;
	}
}

package com.romte.braindeadfarmrun.bank;

import java.util.Arrays;

/** One row of the bank supply tab. */
public final class BankSupplyLine
{
	enum Status
	{
		CARRIED,
		IN_STORAGE,
		MISSING
	}

	final SupplyGroup group;
	final String name;
	final int need;
	final int have;
	final int carried;
	final int[] itemIds;
	final String where;
	/** Equipment slot this row occupies, or null when it cannot be equipped. */
	final String equipmentSlot;
	final boolean wear;
	final boolean noted;

	BankSupplyLine(SupplyGroup group, String name, int need, int have, int carried, int[] itemIds,
		String where, String equipmentSlot, boolean wear, boolean noted)
	{
		this.group = group;
		this.name = name;
		this.need = need;
		this.have = have;
		this.carried = carried;
		this.itemIds = itemIds;
		this.where = where;
		this.equipmentSlot = equipmentSlot;
		this.wear = wear;
		this.noted = noted;
	}

	Status status()
	{
		if (carried >= need)
		{
			return Status.CARRIED;
		}
		return have >= need ? Status.IN_STORAGE : Status.MISSING;
	}

	@Override
	public String toString()
	{
		return "BankSupplyLine(group=" + group + ", name=" + name + ", need=" + need + ", have=" + have
			+ ", carried=" + carried + ", itemIds=" + Arrays.toString(itemIds)
			+ ", equipmentSlot=" + equipmentSlot + ", wear=" + wear + ", noted=" + noted + ")";
	}
}

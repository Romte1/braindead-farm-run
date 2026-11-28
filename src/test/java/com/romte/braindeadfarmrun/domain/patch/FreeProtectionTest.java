package com.romte.braindeadfarmrun.domain.patch;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.romte.braindeadfarmrun.data.Patches;
import org.junit.Test;

public class FreeProtectionTest
{
	@Test
	public void unlockedPatchesAreProtected()
	{
		FreeProtection free = new FreeProtection(true, true, true);

		assertTrue(free.appliesTo(Patches.byId("hosidius_allotment_a")));
		assertTrue(free.appliesTo(Patches.byId("hosidius_herb")));
		assertTrue(free.appliesTo(Patches.byId("falador_tree")));
		assertTrue(free.appliesTo(Patches.byId("guild_allotment_a")));
		assertFalse(free.appliesTo(Patches.byId("falador_herb")));
		assertFalse(free.appliesTo(Patches.byId("farming_guild_tree")));
	}

	@Test
	public void lockedPatchesStillNeedPayment()
	{
		FreeProtection free = new FreeProtection(false, false, false);

		assertFalse(free.appliesTo(Patches.byId("hosidius_allotment_a")));
		assertFalse(free.appliesTo(Patches.byId("falador_tree")));
		assertFalse(free.appliesTo(Patches.byId("guild_allotment_a")));
	}
}

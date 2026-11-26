package com.romte.braindeadfarmrun.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import org.junit.Test;

public class PatchLocatorTest
{
	@Test
	public void findsExactPatch()
	{
		PatchDefinition falador = PatchLocator.find(12083, 4774, 3058, 3311);
		assertNotNull(falador);
		assertEquals("falador_herb", falador.getId());

		PatchDefinition guildTree = PatchLocator.find(4922, 7905, 1232, 3736);
		assertNotNull(guildTree);
		assertEquals("farming_guild_tree", guildTree.getId());
	}

	@Test
	public void findsAllotmentPair()
	{
		PatchDefinition north = PatchLocator.find(12083, 4771, 3049, 3312);
		assertNotNull(north);
		assertEquals("falador_allotment_a", north.getId());

		PatchDefinition south = PatchLocator.find(12083, 4772, 3055, 3306);
		assertNotNull(south);
		assertEquals("falador_allotment_b", south.getId());
	}

	@Test
	public void unknownRegionOrVarbitReturnsNull()
	{
		assertNull(PatchLocator.find(1, 4774, 3058, 3311));
		assertNull(PatchLocator.find(12083, 99999, 3058, 3311));
	}
}

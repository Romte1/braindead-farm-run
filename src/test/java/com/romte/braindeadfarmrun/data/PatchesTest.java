package com.romte.braindeadfarmrun.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class PatchesTest
{
	@Test
	public void countsByType()
	{
		Map<PatchType, Integer> expected = new EnumMap<>(PatchType.class);
		expected.put(PatchType.HERB, 10);
		expected.put(PatchType.ALLOTMENT, 14);
		expected.put(PatchType.TREE, 7);
		expected.put(PatchType.FRUIT_TREE, 7);
		expected.put(PatchType.CALQUAT, 3);
		expected.put(PatchType.HARDWOOD_TREE, 4);
		expected.put(PatchType.SEAWEED, 2);

		assertEquals(47, Patches.all().size());
		int total = 0;
		for (Map.Entry<PatchType, Integer> entry : expected.entrySet())
		{
			assertEquals(entry.getKey().name(), entry.getValue().intValue(),
				Patches.forType(entry.getKey()).size());
			total += Patches.forType(entry.getKey()).size();
		}
		assertEquals(Patches.all().size(), total);
	}

	@Test
	public void idsAreUnique()
	{
		Set<String> ids = new HashSet<>();
		for (PatchDefinition patch : Patches.all())
		{
			assertTrue(patch.getId(), ids.add(patch.getId()));
		}
	}

	@Test
	public void allPatchesHaveSaneData()
	{
		for (PatchDefinition patch : Patches.all())
		{
			assertFalse(patch.getId(), patch.getId().isEmpty());
			assertFalse(patch.getDisplayName(), patch.getDisplayName().isEmpty());
			assertNotNull(patch.getType());
			assertTrue(patch.getId(), patch.getX() != 0);
			assertTrue(patch.getId(), patch.getY() != 0);
			assertTrue(patch.getId(), patch.getRegions().length > 0);
			assertTrue(patch.getId(), patch.getVarbit() > 0);
			assertTrue(patch.getId(), patch.isInRegion(patch.getRegionId()));
		}
	}

	@Test
	public void lookupById()
	{
		PatchDefinition falador = Patches.byId("falador_herb");
		assertNotNull(falador);
		assertEquals("Falador herb", falador.getDisplayName());
		assertEquals(PatchType.HERB, falador.getType());
		assertEquals(3058, falador.getX());
		assertEquals(3311, falador.getY());
		assertEquals(4774, falador.getVarbit());
		assertTrue(falador.isInRegion(12083));
		assertFalse(falador.isInRegion(12854));
		assertNull(Patches.byId("not_a_patch"));
	}

	@Test
	public void regionLookup()
	{
		PatchDefinition guild = Patches.byId("farming_guild_tree");
		assertNotNull(guild);
		assertTrue(guild.isInRegion(4922));
		assertTrue(guild.isInRegion(5179));
		assertFalse(guild.isInRegion(12083));
		assertEquals(4922, guild.getRegionId());
	}
}

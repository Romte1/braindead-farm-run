package com.romte.farmrunplanner.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class CropTest
{
	@Test
	public void growthTimesMatchKnownCrops()
	{
		assertEquals(80, Crop.GUAM.getGrowthMinutes());
		assertEquals(40, Crop.POTATO.getGrowthMinutes());
		assertEquals(160, Crop.OAK.getGrowthMinutes());
		assertEquals(960, Crop.APPLE.getGrowthMinutes());
	}

	@Test
	public void allCropsHaveSaneData()
	{
		assertEquals(36, Crop.values().length);
		for (Crop crop : Crop.values())
		{
			assertTrue(crop.getDisplayName(), crop.getLevel() > 0);
			assertTrue(crop.getDisplayName(), crop.getStages() >= 2);
			assertTrue(crop.getDisplayName(), crop.getGrowthMinutes() > 0);
			assertFalse(crop.getDisplayName(), crop.getPlantedItemName().isEmpty());
			assertTrue(crop.getDisplayName(), crop.getPaymentQuantity() >= 0);
			if (crop.needsPayment())
			{
				assertFalse(crop.getDisplayName(), crop.getPaymentItem().isEmpty());
			}
		}
	}

	@Test
	public void cropsAreGroupedByType()
	{
		Map<PatchType, Integer> expected = new EnumMap<>(PatchType.class);
		expected.put(PatchType.HERB, 15);
		expected.put(PatchType.ALLOTMENT, 8);
		expected.put(PatchType.TREE, 5);
		expected.put(PatchType.FRUIT_TREE, 8);

		int total = 0;
		for (Map.Entry<PatchType, Integer> entry : expected.entrySet())
		{
			List<Crop> crops = Crop.forType(entry.getKey());
			assertEquals(entry.getKey().name(), entry.getValue().intValue(), crops.size());
			for (Crop crop : crops)
			{
				assertSame(entry.getKey(), crop.getType());
			}
			total += crops.size();
		}
		assertEquals(Crop.values().length, total);
	}

	@Test
	public void seedsPerPatch()
	{
		assertEquals(3, Crop.POTATO.getSeedsPerPatch());
		assertEquals(1, Crop.GUAM.getSeedsPerPatch());
		assertEquals(1, Crop.OAK.getSeedsPerPatch());
		assertEquals(1, Crop.APPLE.getSeedsPerPatch());
	}

	@Test
	public void paymentRules()
	{
		assertFalse(Crop.GUAM.needsPayment());
		assertTrue(Crop.POTATO.needsPayment());
		assertTrue(Crop.OAK.needsPayment());
		assertTrue(Crop.APPLE.needsPayment());

		for (Crop crop : Crop.values())
		{
			if (crop.getType() == PatchType.HERB)
			{
				assertFalse(crop.getDisplayName(), crop.needsPayment());
			}
			else
			{
				assertTrue(crop.getDisplayName(), crop.needsPayment());
			}
		}
	}

	@Test
	public void lookupByDisplayName()
	{
		assertSame(Crop.GUAM, Crop.byDisplayName("Guam"));
		assertSame(Crop.GUAM, Crop.byDisplayName("guam"));
		assertSame(Crop.DWARF_WEED, Crop.byDisplayName("Dwarf weed"));
		assertNull(Crop.byDisplayName("Not a crop"));
		assertNull(Crop.byDisplayName(null));
	}
}

package com.romte.braindeadfarmrun.bank;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import net.runelite.api.gameval.ItemID;
import org.junit.Test;

public class TravelItemTest
{
	@Test
	public void explorerRingIncludesEveryDiaryTier()
	{
		int[] ids = SupplyPlanner.explicitIds("Explorer's ring");
		assertEquals(4, ids.length);
		assertTrue(contains(ids, ItemID.LUMBRIDGE_RING_EASY));
		assertTrue(contains(ids, ItemID.LUMBRIDGE_RING_MEDIUM));
		assertTrue(contains(ids, ItemID.LUMBRIDGE_RING_HARD));
		assertTrue(contains(ids, ItemID.LUMBRIDGE_RING_ELITE));
	}

	@Test
	public void ardougneCloakIncludesEveryDiaryTier()
	{
		int[] ids = SupplyPlanner.explicitIds("Ardougne cloak");
		assertEquals(4, ids.length);
		assertTrue(contains(ids, ItemID.ARDY_CAPE_EASY));
		assertTrue(contains(ids, ItemID.ARDY_CAPE_MEDIUM));
		assertTrue(contains(ids, ItemID.ARDY_CAPE_HARD));
		assertTrue(contains(ids, ItemID.ARDY_CAPE_ELITE));
	}

	@Test
	public void xericsTalismanUsesTheChargedItemOnly()
	{
		int[] ids = SupplyPlanner.explicitIds("Xeric's talisman");
		assertEquals(1, ids.length);
		assertEquals(ItemID.XERIC_TALISMAN, ids[0]);
		assertFalse(contains(ids, ItemID.XERIC_TALISMAN_EMPTY));
	}

	@Test
	public void inertAndEmptyVariantsScoreBelowUsefulOnes()
	{
		int charged = BankTab.variantScoreForName("Xeric's talisman");
		int inert = BankTab.variantScoreForName("Xeric's talisman (inert)");
		assertTrue(charged > inert);
		assertTrue(BankTab.variantScoreForName("Ring of wealth (5)")
			> BankTab.variantScoreForName("Ring of wealth (uncharged)"));
		assertTrue(BankTab.variantScoreForName("Explorer's ring 4")
			> BankTab.variantScoreForName("Explorer's ring 1"));
	}

	@Test
	public void explicitTravelNamesAlwaysResolve()
	{
		for (String name : new String[]{
			"Explorer's ring", "Ardougne cloak", "Xeric's talisman", "Pendant of ates",
			"Divine rune pouch"
		})
		{
			assertNotNull(name, SupplyPlanner.explicitIds(name));
			assertTrue(name, SupplyPlanner.explicitIds(name).length > 0);
		}
	}

	private static boolean contains(int[] values, int target)
	{
		for (int value : values)
		{
			if (value == target)
			{
				return true;
			}
		}
		return false;
	}
}

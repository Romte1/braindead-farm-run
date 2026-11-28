package com.romte.braindeadfarmrun.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class SeedSelectionTest
{
	@Test
	public void bestUsesFarmingLevel()
	{
		assertNull(SeedSelection.best(PatchType.TREE, 14));
		assertEquals(Crop.OAK, SeedSelection.best(PatchType.TREE, 15));
		assertEquals(Crop.YEW, SeedSelection.best(PatchType.TREE, 74));
		assertEquals(Crop.MAGIC, SeedSelection.best(PatchType.TREE, 75));

		assertNull(SeedSelection.best(PatchType.HERB, 8));
		assertEquals(Crop.GUAM, SeedSelection.best(PatchType.HERB, 9));
		assertEquals(Crop.TORSTOL, SeedSelection.best(PatchType.HERB, 99));

		assertEquals(Crop.POTATO, SeedSelection.best(PatchType.ALLOTMENT, 1));
		assertEquals(Crop.SNAPE_GRASS, SeedSelection.best(PatchType.ALLOTMENT, 99));
		assertEquals(Crop.DRAGONFRUIT, SeedSelection.best(PatchType.FRUIT_TREE, 99));

		assertNull(SeedSelection.best(PatchType.HARDWOOD_TREE, 34));
		assertEquals(Crop.TEAK, SeedSelection.best(PatchType.HARDWOOD_TREE, 35));
		assertEquals(Crop.MAHOGANY, SeedSelection.best(PatchType.HARDWOOD_TREE, 55));
		assertEquals(Crop.CAMPHOR, SeedSelection.best(PatchType.HARDWOOD_TREE, 66));
		assertEquals(Crop.IRONWOOD, SeedSelection.best(PatchType.HARDWOOD_TREE, 80));
		assertEquals(Crop.ROSEWOOD, SeedSelection.best(PatchType.HARDWOOD_TREE, 99));
		assertEquals(Crop.CALQUAT, SeedSelection.best(PatchType.CALQUAT, 72));
		assertEquals(Crop.SEAWEED, SeedSelection.best(PatchType.SEAWEED, 23));
	}

	@Test
	public void explicitSelectionWins()
	{
		assertEquals(Crop.OAK, SeedSelection.resolve(PatchType.TREE, "OAK", 99));
		assertEquals(Crop.GUAM, SeedSelection.resolve(PatchType.HERB, "GUAM", 99));
	}

	@Test
	public void invalidOrMismatchedSelectionFallsBackToBest()
	{
		assertEquals(Crop.MAGIC, SeedSelection.resolve(PatchType.TREE, "GUAM", 99));
		assertEquals(Crop.MAGIC, SeedSelection.resolve(PatchType.TREE, "NOPE", 99));
		assertEquals(Crop.WILLOW, SeedSelection.resolve(PatchType.TREE, SeedSelection.BEST, 30));
		assertEquals(Crop.MAGIC, SeedSelection.resolve(PatchType.TREE, null, 99));
	}
}

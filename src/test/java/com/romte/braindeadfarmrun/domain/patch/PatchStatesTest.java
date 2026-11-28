package com.romte.braindeadfarmrun.domain.patch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class PatchStatesTest
{
	private final PatchStates states = PatchStates.get();

	@Test
	public void herbs()
	{
		PatchStates.Decoded weeds = states.decode(PatchType.HERB, 0);
		assertEquals(PatchStates.State.EMPTY, weeds.getState());
		assertNull(weeds.getCrop());

		PatchStates.Decoded guamGrowing = states.decode(PatchType.HERB, 4);
		assertEquals(PatchStates.State.GROWING, guamGrowing.getState());
		assertEquals(Crop.GUAM, guamGrowing.getCrop());
		assertEquals(0, guamGrowing.getStage());

		PatchStates.Decoded guamReady = states.decode(PatchType.HERB, 8);
		assertEquals(PatchStates.State.READY, guamReady.getState());
		assertEquals(Crop.GUAM, guamReady.getCrop());
		assertEquals(2, guamReady.getStage());

		PatchStates.Decoded anyHerbDead = states.decode(PatchType.HERB, 170);
		assertEquals(PatchStates.State.DEAD, anyHerbDead.getState());
		assertNull(anyHerbDead.getCrop());
	}

	@Test
	public void allotments()
	{
		PatchStates.Decoded potato = states.decode(PatchType.ALLOTMENT, 6);
		assertEquals(PatchStates.State.GROWING, potato.getState());
		assertEquals(Crop.POTATO, potato.getCrop());
		assertEquals(0, potato.getStage());

		PatchStates.Decoded potatoReady = states.decode(PatchType.ALLOTMENT, 10);
		assertEquals(PatchStates.State.READY, potatoReady.getState());
		assertEquals(Crop.POTATO, potatoReady.getCrop());
	}

	@Test
	public void trees()
	{
		PatchStates.Decoded oak = states.decode(PatchType.TREE, 8);
		assertEquals(PatchStates.State.GROWING, oak.getState());
		assertEquals(Crop.OAK, oak.getCrop());

		PatchStates.Decoded checkHealth = states.decode(PatchType.TREE, 12);
		assertEquals(PatchStates.State.READY, checkHealth.getState());
		assertTrue(checkHealth.isCheckHealth());
		assertFalse(checkHealth.isFell());

		PatchStates.Decoded chop = states.decode(PatchType.TREE, 13);
		assertEquals(PatchStates.State.READY, chop.getState());
		assertTrue(chop.isFell());

		PatchStates.Decoded stump = states.decode(PatchType.TREE, 14);
		assertEquals(PatchStates.State.STUMP, stump.getState());
	}

	@Test
	public void fruitTrees()
	{
		PatchStates.Decoded apple = states.decode(PatchType.FRUIT_TREE, 8);
		assertEquals(PatchStates.State.GROWING, apple.getState());
		assertEquals(Crop.APPLE, apple.getCrop());

		PatchStates.Decoded ready = states.decode(PatchType.FRUIT_TREE, 14);
		assertEquals(PatchStates.State.READY, ready.getState());
		assertEquals(Crop.APPLE, ready.getCrop());

		PatchStates.Decoded diseased = states.decode(PatchType.FRUIT_TREE, 21);
		assertEquals(PatchStates.State.DISEASED, diseased.getState());
		assertEquals(Crop.APPLE, diseased.getCrop());

		PatchStates.Decoded dead = states.decode(PatchType.FRUIT_TREE, 27);
		assertEquals(PatchStates.State.DEAD, dead.getState());

		PatchStates.Decoded checkHealth = states.decode(PatchType.FRUIT_TREE, 34);
		assertEquals(PatchStates.State.READY, checkHealth.getState());
		assertTrue(checkHealth.isCheckHealth());
	}

	@Test
	public void calquats()
	{
		PatchStates.Decoded weeds = states.decode(PatchType.CALQUAT, 0);
		assertEquals(PatchStates.State.EMPTY, weeds.getState());

		PatchStates.Decoded growing = states.decode(PatchType.CALQUAT, 4);
		assertEquals(PatchStates.State.GROWING, growing.getState());
		assertEquals(Crop.CALQUAT, growing.getCrop());

		PatchStates.Decoded check = states.decode(PatchType.CALQUAT, 34);
		assertEquals(PatchStates.State.READY, check.getState());
		assertTrue(check.isCheckHealth());

		PatchStates.Decoded ready = states.decode(PatchType.CALQUAT, 12);
		assertEquals(PatchStates.State.READY, ready.getState());
		assertFalse(ready.isCheckHealth());
	}

	@Test
	public void hardwoodTrees()
	{
		PatchStates.Decoded growing = states.decode(PatchType.HARDWOOD_TREE, 8);
		assertEquals(PatchStates.State.GROWING, growing.getState());
		assertEquals(Crop.TEAK, growing.getCrop());

		PatchStates.Decoded check = states.decode(PatchType.HARDWOOD_TREE, 15);
		assertEquals(PatchStates.State.READY, check.getState());
		assertTrue(check.isCheckHealth());

		PatchStates.Decoded chop = states.decode(PatchType.HARDWOOD_TREE, 16);
		assertEquals(PatchStates.State.READY, chop.getState());
		assertTrue(chop.isFell());

		PatchStates.Decoded stump = states.decode(PatchType.HARDWOOD_TREE, 17);
		assertEquals(PatchStates.State.STUMP, stump.getState());

		PatchStates.Decoded rosewood = states.decode(PatchType.HARDWOOD_TREE, 105);
		assertEquals(Crop.ROSEWOOD, rosewood.getCrop());
		assertEquals(PatchStates.State.GROWING, rosewood.getState());
	}

	@Test
	public void seaweed()
	{
		PatchStates.Decoded growing = states.decode(PatchType.SEAWEED, 4);
		assertEquals(PatchStates.State.GROWING, growing.getState());
		assertEquals(Crop.SEAWEED, growing.getCrop());

		PatchStates.Decoded ready = states.decode(PatchType.SEAWEED, 8);
		assertEquals(PatchStates.State.READY, ready.getState());

		PatchStates.Decoded diseased = states.decode(PatchType.SEAWEED, 11);
		assertEquals(PatchStates.State.DISEASED, diseased.getState());

		PatchStates.Decoded dead = states.decode(PatchType.SEAWEED, 14);
		assertEquals(PatchStates.State.DEAD, dead.getState());
	}

	@Test
	public void unknownValues()
	{
		assertEquals(PatchStates.State.UNKNOWN, states.decode(PatchType.HERB, 99999).getState());
		assertNull(states.decode(PatchType.HERB, 99999).getCrop());
	}
}

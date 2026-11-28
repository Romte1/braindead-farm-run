package com.romte.braindeadfarmrun.planning;

import com.romte.braindeadfarmrun.BraindeadFarmRunConfig;
import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.domain.patch.SeedSelection;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.Skill;

/** Reads the custom seed config and resolves the crop that should be planted. */
@Singleton
public class SeedSelector
{
	private final Client client;
	private final BraindeadFarmRunConfig config;

	@Inject
	SeedSelector(Client client, BraindeadFarmRunConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/** Client thread only: resolving Best reads the player's Farming level. */
	public Crop cropFor(PatchType type)
	{
		return SeedSelection.resolve(type, configured(type),
			client.getRealSkillLevel(Skill.FARMING));
	}

	private String configured(PatchType type)
	{
		switch (type)
		{
			case HERB:
				return config.herbSeed();
			case TREE:
				return config.treeSeed();
			case FRUIT_TREE:
				return config.fruitTreeSeed();
			case ALLOTMENT:
				return config.allotmentSeed();
			case HARDWOOD_TREE:
				return config.hardwoodSeed();
			default:
				return SeedSelection.BEST;
		}
	}
}

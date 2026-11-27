package com.romte.braindeadfarmrun.runtime;

import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Human-readable actions for the patch the player is currently standing at. */
final class StopTasks
{
	private StopTasks()
	{
	}

	static List<String> forPatch(PatchDefinition patch, PatchState state)
	{
		if (state == null)
		{
			return Collections.singletonList("Check the patch.");
		}
		List<String> tasks = new ArrayList<>();
		PatchState.Status status = state.statusAt(Instant.now());
		Crop crop = state.getCrop();
		PatchType type = patch.getType();

		if (status == PatchState.Status.GROWING)
		{
			if (crop != null && !state.isPaid() && crop.needsPayment())
			{
				tasks.add("Pay the farmer to protect the crop.");
			}
			else
			{
				tasks.add("This patch is still growing.");
			}
			return tasks;
		}

		if (type == PatchType.TREE || type == PatchType.FRUIT_TREE)
		{
			if (status == PatchState.Status.READY)
			{
				if (type == PatchType.TREE && !state.isCheckHealth())
				{
					tasks.add("Check-health the tree.");
				}
				else if (type == PatchType.FRUIT_TREE)
				{
					tasks.add("Harvest the fruit.");
				}
				else
				{
					tasks.add("Chop down the tree.");
				}
				tasks.add("Pay the farmer to remove the tree.");
			}
			else if (status == PatchState.Status.STUMP || state.isFell())
			{
				tasks.add("Dig up the stump with a spade.");
			}
			else if (status == PatchState.Status.DISEASED || status == PatchState.Status.DEAD)
			{
				tasks.add("Clear the " + (status == PatchState.Status.DEAD ? "dead" : "diseased")
					+ " tree with a spade.");
			}
			else
			{
				tasks.add("Rake the patch if it is weedy.");
			}
			tasks.add("Plant " + (crop == null ? "a new sapling" : crop.getPlantedItemName()) + ".");
			tasks.add("Pay the farmer to protect the new tree.");
			return tasks;
		}

		if (status == PatchState.Status.READY)
		{
			tasks.add("Harvest the crop.");
		}
		else if (status == PatchState.Status.DISEASED || status == PatchState.Status.DEAD)
		{
			tasks.add("Clear the " + (status == PatchState.Status.DEAD ? "dead" : "diseased")
				+ " crop with a spade.");
		}
		else
		{
			tasks.add("Rake the patch if it is weedy.");
		}
		if (status == PatchState.Status.READY || status == PatchState.Status.DISEASED
			|| status == PatchState.Status.DEAD)
		{
			tasks.add("Clear the patch with a spade.");
		}
		tasks.add("Apply compost to the patch.");
		int seeds = crop == null ? 1 : crop.getSeedsPerPatch();
		tasks.add("Plant " + seeds + " "
			+ (crop == null ? "seed" : crop.getPlantedItemName()) + ".");
		if (crop == null || crop.needsPayment())
		{
			tasks.add("Pay the farmer to protect the crop.");
		}
		return tasks;
	}
}

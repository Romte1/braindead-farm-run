package com.romte.braindeadfarmrun.runtime;

import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Follows the player's actions at one patch and exposes the current checklist. Check-health,
 * clearing and planting are read from the patch varbits; farmer protection is confirmed by the
 * payment dialogue or the Time Tracking protected flag.
 */
final class StopGuide
{
	enum Phase
	{
		CHECK_HEALTH,
		REMOVE,
		PLANT,
		PAY,
		COMPLETE
	}

	private static final class Step
	{
		private final Phase phase;
		private final String label;

		private Step(Phase phase, String label)
		{
			this.phase = phase;
			this.label = label;
		}
	}

	private final PatchType type;
	private final Crop initialCrop;
	private final boolean paymentNeeded;
	private final boolean farmerRemoval;
	private final List<Step> steps = new ArrayList<>();
	private Phase phase;
	private boolean paid;
	private PatchState state;

	StopGuide(PatchDefinition patch, PatchState initialState, boolean protectedNow)
	{
		this.type = patch.getType();
		this.state = initialState;
		PatchState.Status status = status();
		this.initialCrop = initialState == null ? null : initialState.getCrop();
		this.paymentNeeded = initialCrop != null
			? initialCrop.needsPayment()
			: type != PatchType.HERB;
		this.farmerRemoval = type == PatchType.TREE && status == PatchState.Status.READY;
		boolean checkHealth = type == PatchType.TREE && status == PatchState.Status.READY
			&& initialState != null && initialState.isCheckHealth();

		if (checkHealth)
		{
			steps.add(new Step(Phase.CHECK_HEALTH,
				"Check-health the tree (right-click it)."));
		}
		if (status == PatchState.Status.READY)
		{
			if (type == PatchType.TREE)
			{
				steps.add(new Step(Phase.REMOVE,
					"Pay the farmer to remove the tree (or use the Pay option)."));
			}
			else if (type == PatchType.FRUIT_TREE)
			{
				steps.add(new Step(Phase.REMOVE,
					"Harvest the fruit, then pay the farmer to remove the tree."));
			}
			else
			{
				steps.add(new Step(Phase.REMOVE,
					"Harvest the crop, then clear the patch with a spade."));
			}
		}
		else if (status == PatchState.Status.STUMP || (initialState != null && initialState.isFell()))
		{
			steps.add(new Step(Phase.REMOVE, "Dig up the stump with a spade."));
		}
		else if (status == PatchState.Status.DISEASED || status == PatchState.Status.DEAD)
		{
			steps.add(new Step(Phase.REMOVE,
				"Clear the " + (status == PatchState.Status.DEAD ? "dead" : "diseased")
					+ " crop with a spade."));
		}

		if (status != PatchState.Status.GROWING)
		{
			String plant = initialCrop == null ? "your sapling or seeds"
				: initialCrop.getPlantedItemName();
			steps.add(new Step(Phase.PLANT, "Plant " + plant + "."));
		}
		if (paymentNeeded && !(status == PatchState.Status.GROWING && protectedNow))
		{
			steps.add(new Step(Phase.PAY,
				"Pay the farmer to protect it (choose 'Yes, please')."));
		}

		if (status == PatchState.Status.GROWING)
		{
			phase = paymentNeeded && !protectedNow ? Phase.PAY : Phase.COMPLETE;
		}
		else if (checkHealth)
		{
			phase = Phase.CHECK_HEALTH;
		}
		else if (hasStep(Phase.REMOVE))
		{
			phase = Phase.REMOVE;
		}
		else
		{
			phase = Phase.PLANT;
		}
	}

	void update(PatchState next, boolean protectedNow)
	{
		if (next != null)
		{
			state = next;
		}
		PatchState.Status status = status();
		if (phase == Phase.CHECK_HEALTH && state != null && !state.isCheckHealth())
		{
			phase = Phase.REMOVE;
		}
		if (phase == Phase.REMOVE && isCleared())
		{
			phase = Phase.PLANT;
		}
		if (phase == Phase.PLANT && isNewlyPlanted(status))
		{
			phase = paymentNeeded ? Phase.PAY : Phase.COMPLETE;
		}
		if (phase == Phase.PAY && (paid || protectedNow))
		{
			phase = Phase.COMPLETE;
		}
	}

	void onPayment()
	{
		if (phase == Phase.PAY || (phase == Phase.PLANT && isNewlyPlanted(status())))
		{
			paid = true;
			phase = Phase.COMPLETE;
		}
	}

	boolean isComplete()
	{
		return phase == Phase.COMPLETE;
	}

	Phase phase()
	{
		return phase;
	}

	/** True when the next click belongs on the farmer rather than on the patch itself. */
	boolean isFarmerTarget()
	{
		return phase == Phase.PAY || (phase == Phase.REMOVE && farmerRemoval);
	}

	List<String> stepLines()
	{
		if (phase == Phase.COMPLETE && steps.isEmpty())
		{
			return Collections.singletonList("\u2713 Ready for the next stop.");
		}
		List<String> lines = new ArrayList<>(steps.size());
		int current = rank(phase);
		for (Step step : steps)
		{
			int stepRank = rank(step.phase);
			String marker = phase == Phase.COMPLETE || stepRank < current
				? "\u2713 " : stepRank == current ? "\u25B6 " : "\u25CB ";
			lines.add(marker + step.label);
		}
		return lines;
	}

	private boolean hasStep(Phase target)
	{
		for (Step step : steps)
		{
			if (step.phase == target)
			{
				return true;
			}
		}
		return false;
	}

	private PatchState.Status status()
	{
		return state == null ? PatchState.Status.EMPTY : state.statusAt(Instant.now());
	}

	private boolean isCleared()
	{
		return status() == PatchState.Status.EMPTY
			|| (state != null && state.getCrop() == null);
	}

	private boolean isNewlyPlanted(PatchState.Status status)
	{
		return state != null && state.getCrop() != null && state.getPlantedAt() != null
			&& status == PatchState.Status.GROWING;
	}

	private static int rank(Phase phase)
	{
		switch (phase)
		{
			case CHECK_HEALTH:
				return 0;
			case REMOVE:
				return 1;
			case PLANT:
				return 2;
			case PAY:
				return 3;
			default:
				return 4;
		}
	}
}

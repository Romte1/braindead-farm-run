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
		COMPOST,
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
	private final Crop plannedCrop;
	private final boolean paymentNeeded;
	private final boolean farmerRemoval;
	private final boolean rakeNeeded;
	private final String compostItemName;
	private final List<Step> steps = new ArrayList<>();
	private Phase phase;
	private boolean paid;
	private PatchState state;
	private String plantItemName;

	StopGuide(PatchDefinition patch, PatchState initialState, Crop plannedCrop,
		String compostItemName, boolean composted)
	{
		this.type = patch.getType();
		this.state = initialState;
		this.plannedCrop = plannedCrop;
		this.compostItemName = compostItemName;
		PatchState.Status status = status();
		this.initialCrop = initialState == null ? null : initialState.getCrop();
		Crop paymentCrop = paymentCrop();
		this.paymentNeeded = paymentCrop != null
			? paymentCrop.needsPayment()
			: type != PatchType.HERB;
		this.farmerRemoval = type == PatchType.TREE && status == PatchState.Status.READY;
		this.rakeNeeded = initialState != null && initialState.isWeedy();
		this.plantItemName = plannedCrop == null ? null : plannedCrop.getPlantedItemName();
		boolean checkHealth = hasCheckHealth(type) && status == PatchState.Status.READY
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
			else if (type == PatchType.HARDWOOD_TREE)
			{
				steps.add(new Step(Phase.REMOVE,
					"Chop down the tree, then dig up the stump with a spade."));
			}
			else if (type == PatchType.CALQUAT)
			{
				steps.add(new Step(Phase.REMOVE,
					"Harvest the fruit, then clear the patch with a spade."));
			}
			else if (type == PatchType.SEAWEED)
			{
				steps.add(new Step(Phase.REMOVE,
					"Harvest the seaweed, then clear the patch with a spade."));
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

		if (rakeNeeded)
		{
			steps.add(new Step(Phase.REMOVE, "Rake the weeds."));
		}
		if (!composted && status != PatchState.Status.GROWING && compostItemName != null
			&& !compostItemName.isEmpty())
		{
			steps.add(new Step(Phase.COMPOST,
				"Use " + compostItemName + " on the patch."));
		}
		if (status != PatchState.Status.GROWING)
		{
			String plant = plantItemName != null ? plantItemName
				: initialCrop == null ? "your sapling or seeds"
				: initialCrop.getPlantedItemName();
			steps.add(new Step(Phase.PLANT, "Plant " + plant + "."));
		}
		// A patch that is already growing was planted before this run. Do not send the
		// player back to pay for protection again; that decision belongs to planting time.
		if (paymentNeeded && status != PatchState.Status.GROWING)
		{
			steps.add(new Step(Phase.PAY,
				"Pay the farmer to protect it (choose 'Yes, please')."));
		}

		if (status == PatchState.Status.GROWING)
		{
			phase = Phase.COMPLETE;
		}
		else if (checkHealth)
		{
			phase = Phase.CHECK_HEALTH;
		}
		else if (hasStep(Phase.REMOVE))
		{
			phase = Phase.REMOVE;
		}
		else if (hasStep(Phase.COMPOST))
		{
			phase = Phase.COMPOST;
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
			phase = hasStep(Phase.COMPOST) ? Phase.COMPOST : Phase.PLANT;
		}
		if ((phase == Phase.COMPOST || phase == Phase.PLANT) && isNewlyPlanted(status))
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
		paid = true;
		if (phase == Phase.PAY || (phase == Phase.PLANT && isNewlyPlanted(status())))
		{
			phase = Phase.COMPLETE;
		}
	}

	void onCompost()
	{
		if (phase == Phase.COMPOST)
		{
			phase = Phase.PLANT;
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

	/** Item the current step needs the player to use, or null when the step is not item-based. */
	String currentItemName()
	{
		switch (phase)
		{
			case PLANT:
				return plantItemName != null ? plantItemName
					: initialCrop == null ? null : initialCrop.getPlantedItemName();
			case COMPOST:
				return compostItemName;
			case PAY:
				Crop crop = paymentCrop();
				return crop != null && crop.needsPayment() ? crop.getPaymentItem() : null;
			case REMOVE:
				if (farmerRemoval)
				{
					return null;
				}
				if (rakeNeeded)
				{
					return "Rake";
				}
				return type == PatchType.HARDWOOD_TREE
					&& status() == PatchState.Status.READY ? "Axe" : "Spade";
			default:
				return null;
		}
	}

	void setPlantItemName(String name)
	{
		if (name != null && !name.isEmpty())
		{
			plantItemName = name;
		}
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
			String label = step.phase == Phase.PLANT && plantItemName != null
				? "Plant " + plantItemName + "." : step.label;
			lines.add(marker + label);
		}
		return lines;
	}

	private Crop paymentCrop()
	{
		return plannedCrop != null ? plannedCrop : initialCrop;
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
		if (state != null && state.isWeedy())
		{
			return false;
		}
		return status() == PatchState.Status.EMPTY
			|| (state != null && state.getCrop() == null);
	}

	private boolean isNewlyPlanted(PatchState.Status status)
	{
		return state != null && state.getCrop() != null && state.getPlantedAt() != null
			&& status == PatchState.Status.GROWING;
	}

	private static boolean hasCheckHealth(PatchType type)
	{
		return type == PatchType.TREE || type == PatchType.HARDWOOD_TREE
			|| type == PatchType.CALQUAT;
	}

	private static int rank(Phase phase)
	{
		switch (phase)
		{
			case CHECK_HEALTH:
				return 0;
			case REMOVE:
				return 1;
			case COMPOST:
				return 2;
			case PLANT:
				return 3;
			case PAY:
				return 4;
			default:
				return 5;
		}
	}
}

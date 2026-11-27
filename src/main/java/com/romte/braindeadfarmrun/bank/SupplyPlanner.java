package com.romte.braindeadfarmrun.bank;

import com.romte.braindeadfarmrun.BraindeadFarmRunConfig;
import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Builds the list of items needed for the selected run types: tools, seeds/saplings, payments
 * and the best compost choice. Counts are compared against the bank, inventory and worn
 * equipment.
 */
@Singleton
public class SupplyPlanner
{
	private final Client client;
	private final ItemManager itemManager;
	private final BraindeadFarmRunConfig config;

	@Inject
	SupplyPlanner(Client client, ItemManager itemManager, BraindeadFarmRunConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	public List<BankSupplyLine> plan(Set<PatchType> types, Map<String, PatchState> states)
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);

		Map<String, MutableLine> lines = new LinkedHashMap<>();

		boolean herbs = types.contains(PatchType.HERB);
		boolean allotments = types.contains(PatchType.ALLOTMENT);
		boolean trees = types.contains(PatchType.TREE);
		boolean fruitTrees = types.contains(PatchType.FRUIT_TREE);

		if (herbs || allotments)
		{
			add(lines, SupplyGroup.TOOLS, "Rake", 1, new int[]{ItemID.RAKE}, false);
			add(lines, SupplyGroup.TOOLS, "Seed dibber", 1, new int[]{ItemID.DIBBER}, false);
		}
		if (herbs || allotments || trees || fruitTrees)
		{
			add(lines, SupplyGroup.TOOLS, "Spade", 1, new int[]{ItemID.SPADE}, false);
		}
		if (trees || fruitTrees)
		{
			boolean magicSecateurs = Quest.FAIRYTALE_I__GROWING_PAINS.getState(client)
				== QuestState.FINISHED;
			add(lines, SupplyGroup.TOOLS,
				magicSecateurs ? "Magic secateurs" : "Secateurs", 1,
				new int[]{magicSecateurs ? ItemID.FAIRY_ENCHANTED_SECATEURS : ItemID.SECATEURS},
				false);
		}
		addCompost(lines, bank, inventory, worn);

		for (PatchType type : types)
		{
			for (PatchDefinition patch : Patches.forType(type))
			{
				PatchState state = states.get(patch.getId());
				if (state == null || state.getCrop() == null)
				{
					continue;
				}
				Crop crop = state.getCrop();
				add(lines, SupplyGroup.SEEDS, crop.getPlantedItemName(),
					crop.getSeedsPerPatch(), resolve(crop.getPlantedItemName()), false);
				if (crop.needsPayment())
				{
					add(lines, SupplyGroup.PAYMENTS, crop.getPaymentItem(),
						crop.getPaymentQuantity(), resolve(crop.getPaymentItem()), true);
				}
			}
		}

		List<BankSupplyLine> result = new ArrayList<>();
		for (MutableLine line : lines.values())
		{
			int inBank = count(bank, line.itemIds);
			int carried = count(inventory, line.itemIds) + count(worn, line.itemIds);
			int have = inBank + carried;
			String where = carried >= line.need ? "carried"
				: have >= line.need ? "in your bank" : null;
			result.add(new BankSupplyLine(line.group, line.name, line.need, have, carried,
				line.itemIds, where, false, line.noted));
		}
		return result;
	}

	private void addCompost(Map<String, MutableLine> lines, ItemContainer bank,
		ItemContainer inventory, ItemContainer worn)
	{
		if (config.bottomlessCompostBucket())
		{
			add(lines, SupplyGroup.TOOLS, "Bottomless compost bucket", 1,
				new int[]{ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED}, false);
			return;
		}

		int[] itemIds = {
			ItemID.BUCKET_ULTRACOMPOST,
			ItemID.BUCKET_SUPERCOMPOST,
			ItemID.BUCKET_COMPOST
		};
		String[] names = {"Ultracompost", "Supercompost", "Compost"};
		int chosen = 0;
		for (int i = 0; i < itemIds.length; i++)
		{
			if (count(bank, itemIds[i]) + count(inventory, itemIds[i])
				+ count(worn, itemIds[i]) > 0)
			{
				chosen = i;
				break;
			}
		}
		add(lines, SupplyGroup.TOOLS, names[chosen], 1, new int[]{itemIds[chosen]}, false);
	}

	private int[] resolve(String name)
	{
		List<ItemPrice> prices = itemManager.search(name);
		if (prices == null || prices.isEmpty())
		{
			return new int[0];
		}
		int[] unnoted = prices.stream()
			.mapToInt(ItemPrice::getId)
			.filter(id -> itemManager.getItemComposition(id).getNote() == 0)
			.toArray();
		if (unnoted.length > 0)
		{
			return unnoted;
		}
		return prices.stream().mapToInt(ItemPrice::getId).toArray();
	}

	private static void add(Map<String, MutableLine> lines, SupplyGroup group, String name, int need,
		int[] itemIds, boolean noted)
	{
		if (itemIds == null || itemIds.length == 0)
		{
			return;
		}
		String key = group + ":" + name;
		MutableLine line = lines.get(key);
		if (line == null)
		{
			lines.put(key, new MutableLine(group, name, need, itemIds, noted));
		}
		else
		{
			line.need += need;
		}
	}

	private static int count(ItemContainer container, int[] itemIds)
	{
		if (container == null)
		{
			return 0;
		}
		int total = 0;
		for (int itemId : itemIds)
		{
			total += container.count(itemId);
		}
		return total;
	}

	private static int count(ItemContainer container, int itemId)
	{
		return container == null ? 0 : container.count(itemId);
	}

	private static final class MutableLine
	{
		private final SupplyGroup group;
		private final String name;
		private int need;
		private final int[] itemIds;
		private final boolean noted;

		private MutableLine(SupplyGroup group, String name, int need, int[] itemIds, boolean noted)
		{
			this.group = group;
			this.name = name;
			this.need = need;
			this.itemIds = itemIds;
			this.noted = noted;
		}
	}
}

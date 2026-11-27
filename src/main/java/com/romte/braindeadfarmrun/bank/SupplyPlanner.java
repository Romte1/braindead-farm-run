package com.romte.braindeadfarmrun.bank;

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
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Builds the list of items needed for the selected run types: tools, seeds/saplings, payments
 * and the bottomless compost bucket. Counts are compared against the bank, inventory and worn
 * equipment.
 */
@Singleton
public class SupplyPlanner
{
	private final Client client;
	private final ItemManager itemManager;

	@Inject
	SupplyPlanner(Client client, ItemManager itemManager)
	{
		this.client = client;
		this.itemManager = itemManager;
	}

	public List<BankSupplyLine> plan(Set<PatchType> types, Map<String, PatchState> states)
	{
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
			add(lines, SupplyGroup.TOOLS, "Secateurs", 1, new int[]{ItemID.SECATEURS}, false);
		}
		add(lines, SupplyGroup.TOOLS, "Bottomless compost bucket", 1,
			new int[]{ItemID.BOTTOMLESS_COMPOST_BUCKET}, false);

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

		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);

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

	private int[] resolve(String name)
	{
		List<ItemPrice> prices = itemManager.search(name);
		if (prices == null || prices.isEmpty())
		{
			return new int[0];
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

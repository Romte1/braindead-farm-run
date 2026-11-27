package com.romte.braindeadfarmrun.bank;

import com.romte.braindeadfarmrun.BraindeadFarmRunConfig;
import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
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
	private volatile boolean magicSecateurs;

	@Inject
	SupplyPlanner(Client client, ItemManager itemManager, BraindeadFarmRunConfig config)
	{
		this.client = client;
		this.itemManager = itemManager;
		this.config = config;
	}

	/** Must be called on the client thread outside a script (Quest.getState runs a script). */
	public void refreshQuestState()
	{
		magicSecateurs = Quest.FAIRYTALE_I__GROWING_PAINS.getState(client) == QuestState.FINISHED;
	}

	public List<BankSupplyLine> plan(Set<PatchType> types, Map<String, PatchState> states,
		List<String> transports, List<Integer> requiredItems)
	{		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
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
		if (magicSecateurs && (herbs || trees || fruitTrees))
		{
			add(lines, SupplyGroup.TOOLS, "Magic secateurs", 1,
				new int[]{ItemID.FAIRY_ENCHANTED_SECATEURS}, false);
		}
		else if (trees || fruitTrees)
		{
			add(lines, SupplyGroup.TOOLS, "Secateurs", 1,
				new int[]{ItemID.SECATEURS}, false);
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

		for (String transport : transports)
		{
			addTransport(transport, lines, bank, inventory, worn);
		}
		for (int itemId : requiredItems)
		{
			addRequiredItem(lines, itemId);
		}

		List<BankSupplyLine> result = new ArrayList<>();
		Set<String> wornSlots = new HashSet<>();
		for (MutableLine line : lines.values())
		{
			int inBank = count(bank, line.itemIds);
			int carried = count(inventory, line.itemIds) + count(worn, line.itemIds);
			int have = inBank + carried;
			String where = carried >= line.need ? "carried"
				: have >= line.need ? "in your bank" : null;
			boolean wear = line.equipmentSlot != null && wornSlots.add(line.equipmentSlot);
			result.add(new BankSupplyLine(line.group, line.name, line.need, have, carried,
				line.itemIds, where, line.equipmentSlot, wear, line.noted));
		}
		return result;
	}

	/** Adds an item Shortest Path explicitly says the route needs to pick up. */
	private void addRequiredItem(Map<String, MutableLine> lines, int itemId)
	{
		ItemComposition def = itemManager.getItemComposition(itemId);
		if (def == null)
		{
			return;
		}
		int id = itemId;
		if (def.getNote() != 0 && def.getLinkedNoteId() > 0)
		{
			id = def.getLinkedNoteId();
			def = itemManager.getItemComposition(id);
		}
		for (MutableLine line : lines.values())
		{
			for (int existing : line.itemIds)
			{
				if (existing == id)
				{
					return;
				}
			}
		}
		String name = def.getName();
		String lower = name.toLowerCase(Locale.ROOT);
		SupplyGroup group = lower.endsWith("rune") || lower.contains(" rune")
			? SupplyGroup.RUNES : SupplyGroup.TRAVEL;
		add(lines, group, name, 1, new int[]{id}, false);
	}

	private void addTransport(String display, Map<String, MutableLine> lines, ItemContainer bank,
		ItemContainer inventory, ItemContainer worn)
	{
		String lower = display.toLowerCase(Locale.ROOT);
		if (lower.contains("soul wars") || lower.contains("minigame")
			|| lower.contains("fairy ring") || lower.contains("spirit tree")
			|| lower.contains("tree gnome village") || lower.contains("walk")
			|| lower.contains("gnome glider") || lower.contains("canoe")
			|| lower.contains("charter") || lower.contains("boat")
			|| lower.contains("magic carpet"))
		{
			return;
		}

		if (lower.contains("ardougne cloak"))
		{
			addTravel(lines, "Ardougne cloak");
		}
		else if (lower.contains("explorer's ring"))
		{
			addTravel(lines, "Explorer's ring");
		}
		else if (lower.contains("ring of wealth"))
		{
			addChargedTravel(lines, "Ring of wealth");
		}
		else if (lower.contains("skills necklace"))
		{
			addChargedTravel(lines, "Skills necklace");
		}
		else if (lower.contains("xeric's talisman"))
		{
			addChargedTravel(lines, "Xeric's talisman");
		}
		else if (lower.contains("eternal teleport crystal"))
		{
			addTravel(lines, "Eternal teleport crystal");
		}
		else if (lower.contains("pendant of ates"))
		{
			addChargedTravel(lines, "Pendant of ates");
		}
		else if (lower.contains("quetzal"))
		{
			addChargedTravel(lines, "Quetzal whistle");
		}
		else if (lower.contains("farming cape"))
		{
			addTravel(lines, "Farming cape");
		}
		else if (lower.contains("royal seed pod"))
		{
			addTravel(lines, "Royal seed pod");
		}
		else if (lower.contains("ectophial"))
		{
			addTravel(lines, "Ectophial");
		}
		else if (lower.contains("icy basalt"))
		{
			addTravel(lines, "Icy basalt");
		}
		else if (lower.contains("stony basalt"))
		{
			addTravel(lines, "Stony basalt");
		}
		else if (lower.contains("teleport to house") || lower.contains("house teleport"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Teleport to house");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 1, 1, 0, 0);
			}
		}
		else if (lower.contains("crystal teleport seed"))
		{
			addTravel(lines, "Crystal teleport seed");
		}
		else if (lower.contains("teleport crystal"))
		{
			addChargedTravel(lines, "Teleport crystal");
		}
		else if (lower.contains("civitas"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Civitas illa Fortis teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 1, 1, 0);
			}
		}
		else if (lower.contains("kourend") || lower.contains("hosidius"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Kourend castle teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 0, 1, 1);
			}
		}
		else if (lower.contains("watchtower"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Watchtower teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 2, 0, 0);
			}
		}
		else if (lower.contains("kharyrll"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Kharyrll teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 0, 0, 0);
				addRune(lines, "Blood rune", 1);
			}
		}
		else if (lower.contains("fishing guild"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Fishing guild teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 3, 0, 0, 0, 10);
				addRune(lines, "Astral rune", 3);
			}
		}
		else if (lower.contains("varrock"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Varrock teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 3, 0, 1, 0);
			}
		}
		else if (lower.contains("lumbridge"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Lumbridge teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 3, 1, 0, 0);
			}
		}
		else if (lower.contains("falador"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Falador teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 3, 0, 0, 1);
			}
		}
		else if (lower.contains("camelot"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Camelot teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 5, 0, 0, 0);
			}
		}
		else if (lower.contains("ardougne"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Ardougne teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 0, 0, 2);
			}
		}
		else if (lower.contains("taverley"))
		{
			addTravel(lines, "Taverley teleport");
		}
		else if (lower.contains("catherby"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Catherby teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 3, 0, 0, 0, 10);
				addRune(lines, "Astral rune", 3);
			}
		}
		else if (lower.contains("brimhaven"))
		{
			addTravel(lines, "Brimhaven teleport");
		}
		else if (lower.contains("fenkenstrain"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Fenkenstrain's castle teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 0, 1, 0, 0);
				addRune(lines, "Soul rune", 1);
			}
		}
		else if (lower.contains("harmony"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Harmony island teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 1, 0, 0, 0, 0);
				addRune(lines, "Nature rune", 1);
				addRune(lines, "Soul rune", 1);
			}
		}
		else if (lower.contains("trollheim"))
		{
			if (lower.contains("tablet"))
			{
				addTravel(lines, "Trollheim teleport");
			}
			else
			{
				addRunes(lines, bank, inventory, worn, 2, 0, 0, 2, 0);
			}
		}
	}

	private void addTravel(Map<String, MutableLine> lines, String name)
	{
		add(lines, SupplyGroup.TRAVEL, name, 1, resolve(name), false);
	}

	private void addChargedTravel(Map<String, MutableLine> lines, String name)
	{
		add(lines, SupplyGroup.TRAVEL, name, 1, resolveCharged(name), false);
	}

	/**
	 * Charge-based teleport jewellery has a base uncharged item plus numbered variants. Keep the
	 * numbered variants so the bank tab never plans an uncharged piece; if the search has no
	 * numbered variants (Xeric's talisman, for example), keep the charged base item.
	 */
	private int[] resolveCharged(String name)
	{
		List<ItemPrice> prices = itemManager.search(name);
		if (prices == null || prices.isEmpty())
		{
			return new int[0];
		}
		List<ItemPrice> unnoted = new ArrayList<>();
		for (ItemPrice price : prices)
		{
			if (itemManager.getItemComposition(price.getId()).getNote() == 0)
			{
				unnoted.add(price);
			}
		}
		if (unnoted.isEmpty())
		{
			unnoted = prices;
		}

		boolean hasNumberedVariants = false;
		List<Integer> charged = new ArrayList<>();
		for (ItemPrice price : unnoted)
		{
			String itemName = itemManager.getItemComposition(price.getId()).getName();
			String lower = itemName.toLowerCase(Locale.ROOT);
			if (lower.contains("uncharged") || lower.contains("empty"))
			{
				continue;
			}
			if (hasChargeSuffix(itemName))
			{
				hasNumberedVariants = true;
			}
			charged.add(price.getId());
		}
		if (hasNumberedVariants)
		{
			charged.removeIf(id -> !hasChargeSuffix(itemManager.getItemComposition(id).getName()));
		}
		if (charged.isEmpty())
		{
			return resolve(name);
		}
		int[] result = new int[charged.size()];
		for (int i = 0; i < charged.size(); i++)
		{
			result[i] = charged.get(i);
		}
		return result;
	}

	private static boolean hasChargeSuffix(String itemName)
	{
		int open = itemName.lastIndexOf('(');
		int close = itemName.lastIndexOf(')');
		if (open < 0 || close <= open + 1)
		{
			return false;
		}
		try
		{
			return Integer.parseInt(itemName.substring(open + 1, close).trim()) >= 0;
		}
		catch (NumberFormatException e)
		{
			return false;
		}
	}

	private void addRunes(Map<String, MutableLine> lines, ItemContainer bank, ItemContainer inventory,
		ItemContainer worn, int law, int air, int earth, int fire, int water)
	{
		addRune(lines, "Law rune", law);
		addElementalRunes(lines, bank, inventory, worn, air, earth, fire, water);
	}

	/**
	 * Uses combination runes from the bank when they cover two required elements, then fills the
	 * remainder with single-element runes. Greedy by covered pairs keeps the planned rune stacks
	 * as few as the player's stock allows.
	 */
	private void addElementalRunes(Map<String, MutableLine> lines, ItemContainer bank,
		ItemContainer inventory, ItemContainer worn, int air, int earth, int fire, int water)
	{
		int[] need = {air, water, earth, fire};
		String[] names = {"Mist rune", "Dust rune", "Mud rune", "Smoke rune", "Steam rune", "Lava rune"};
		int[][] pairs = {{0, 1}, {0, 2}, {1, 2}, {0, 3}, {1, 3}, {2, 3}};
		int[] available = new int[names.length];
		for (int i = 0; i < names.length; i++)
		{
			int[] ids = resolve(names[i]);
			available[i] = count(bank, ids) + count(inventory, ids) + count(worn, ids);
		}

		boolean progress = true;
		while (progress)
		{
			progress = false;
			int best = -1;
			int bestCover = 0;
			for (int i = 0; i < names.length; i++)
			{
				int a = pairs[i][0];
				int b = pairs[i][1];
				int cover = Math.min(Math.min(need[a], need[b]), available[i]);
				if (cover > bestCover)
				{
					bestCover = cover;
					best = i;
				}
			}
			if (best >= 0)
			{
				int a = pairs[best][0];
				int b = pairs[best][1];
				need[a] -= bestCover;
				need[b] -= bestCover;
				available[best] -= bestCover;
				addRune(lines, names[best], bestCover);
				progress = true;
			}
		}

		addRune(lines, "Air rune", need[0]);
		addRune(lines, "Water rune", need[1]);
		addRune(lines, "Earth rune", need[2]);
		addRune(lines, "Fire rune", need[3]);
	}

	private void addRune(Map<String, MutableLine> lines, String name, int need)
	{
		if (need > 0)
		{
			add(lines, SupplyGroup.RUNES, name, need, resolve(name), false);
		}
	}

	private void addCompost(Map<String, MutableLine> lines, ItemContainer bank,
		ItemContainer inventory, ItemContainer worn)
	{		if (config.bottomlessCompostBucket())
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
			lines.put(key, new MutableLine(group, name, need, itemIds, equipmentSlot(name), noted));
		}
		else
		{
			line.need += need;
		}
	}

	private static String equipmentSlot(String name)
	{
		switch (name)
		{
			case "Magic secateurs":
				return "weapon";
			case "Farming cape":
			case "Ardougne cloak":
				return "cape";
			case "Explorer's ring":
			case "Ring of wealth":
				return "ring";
			case "Skills necklace":
			case "Xeric's talisman":
			case "Pendant of ates":
				return "neck";
			default:
				return null;
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
		private final String equipmentSlot;
		private final boolean noted;

		private MutableLine(SupplyGroup group, String name, int need, int[] itemIds,
			String equipmentSlot, boolean noted)
		{
			this.group = group;
			this.name = name;
			this.need = need;
			this.itemIds = itemIds;
			this.equipmentSlot = equipmentSlot;
			this.noted = noted;
		}
	}
}

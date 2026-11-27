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
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.ItemManager;
import net.runelite.http.api.item.ItemPrice;

/**
 * Builds the list of items needed for the selected run types: tools, seeds/saplings, payments
 * and the best compost choice. Counts are compared against the bank, inventory and worn
 * equipment.
 */
@Slf4j
@Singleton
public class SupplyPlanner
{
	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] RUNE_POUCH_QTY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3, VarbitID.RUNE_POUCH_QUANTITY_4,
		VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};
	private static final int[] RUNE_POUCH_IDS = {
		ItemID.DIVINE_RUNE_POUCH, ItemID.BH_RUNE_POUCH, ItemID.DIVINE_RUNE_POUCH_TROUVER
	};
	private static final String[][] COMBINATION_RUNES = {
		{"Mist rune", "Air rune", "Water rune"},
		{"Dust rune", "Air rune", "Earth rune"},
		{"Mud rune", "Water rune", "Earth rune"},
		{"Smoke rune", "Air rune", "Fire rune"},
		{"Steam rune", "Water rune", "Fire rune"},
		{"Lava rune", "Earth rune", "Fire rune"},
		{"Sunfire rune", "Fire rune"},
		{"Aether rune", "Soul rune"}
	};

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
	{
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		log.debug("Supply plan starting: types={} transports={} required={}", types.size(),
			transports.size(), requiredItems.size());

		Map<String, MutableLine> lines = new LinkedHashMap<>();
		Map<String, Integer> runeNeeds = new LinkedHashMap<>();

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
		log.debug("Supply plan tools done: {} lines", lines.size());

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

		log.debug("Supply plan crops done: {} lines, {} rune needs", lines.size(), runeNeeds.size());
		for (String transport : transports)
		{
			addTransport(transport, lines, runeNeeds);
		}
		for (int itemId : requiredItems)
		{
			addRequiredItem(lines, runeNeeds, itemId);
		}
		log.debug("Supply plan transports done: {} lines, {} rune needs", lines.size(), runeNeeds.size());
		planRunes(lines, runeNeeds, bank, inventory, worn);
		log.debug("Supply plan runes done: {} lines, {} rune needs", lines.size(), runeNeeds.size());
		for (MutableLine line : lines.values())
		{
			if (line.uses > 0)
			{
				line.need = travelNeed(line, bank, inventory, worn);
			}
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
				line.itemIds, where, line.equipmentSlot, wear, line.noted, line.details));
		}
		log.debug("Supply plan finished: {} lines", result.size());
		return result;
	}

	/** Adds an item Shortest Path explicitly says the route needs to pick up. */
	private void addRequiredItem(Map<String, MutableLine> lines, Map<String, Integer> runeNeeds,
		int itemId)
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
		String name = def.getName();
		String lower = name.toLowerCase(Locale.ROOT);
		if (isCombination(name))
		{
			for (String[] combo : COMBINATION_RUNES)
			{
				if (combo[0].equalsIgnoreCase(name))
				{
					addRuneNeed(runeNeeds, combo[1], 1);
					if (combo.length > 2)
					{
						addRuneNeed(runeNeeds, combo[2], 1);
					}
					return;
				}
			}
		}
		if (lower.endsWith("rune") || lower.contains(" rune"))
		{
			addRuneNeed(runeNeeds, name, 1);
			return;
		}
		if (lower.equals("divine rune pouch") || lower.equals("rune pouch"))
		{
			return; // handled with the pouch contents by planRunes
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
		addTravelUse(lines, name, new int[]{id});
	}

	private void addTransport(String display, Map<String, MutableLine> lines,
		Map<String, Integer> runeNeeds)
	{
		String lower = display.toLowerCase(Locale.ROOT);
		if (lower.contains("soul wars") || lower.contains("minigame")
			|| lower.contains("grand exchange")
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
		else if (lower.contains("slayer ring"))
		{
			addChargedTravel(lines, "Slayer ring");
		}
		else if (lower.contains("ring of dueling"))
		{
			addChargedTravel(lines, "Ring of dueling");
		}
		else if (lower.contains("games necklace"))
		{
			addChargedTravel(lines, "Games necklace");
		}
		else if (lower.contains("combat bracelet"))
		{
			addChargedTravel(lines, "Combat bracelet");
		}
		else if (lower.contains("amulet of glory"))
		{
			addChargedTravel(lines, "Amulet of glory");
		}
		else if (lower.contains("burning amulet"))
		{
			addChargedTravel(lines, "Burning amulet");
		}
		else if (lower.contains("digsite pendant"))
		{
			addChargedTravel(lines, "Digsite pendant");
		}
		else if (lower.contains("necklace of passage"))
		{
			addChargedTravel(lines, "Necklace of passage");
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
				addRunes(runeNeeds, 1, 1, 1, 0, 0);
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
				addRunes(runeNeeds, 2, 0, 1, 1, 0);
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
				addRunes(runeNeeds, 2, 0, 0, 1, 1);
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
				addRunes(runeNeeds, 2, 0, 2, 0, 0);
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
				addRunes(runeNeeds, 2, 0, 0, 0, 0);
				addRuneNeed(runeNeeds, "Blood rune", 1);
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
				addRunes(runeNeeds, 3, 0, 0, 0, 10);
				addRuneNeed(runeNeeds, "Astral rune", 3);
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
				addRunes(runeNeeds, 1, 3, 0, 1, 0);
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
				addRunes(runeNeeds, 1, 3, 1, 0, 0);
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
				addRunes(runeNeeds, 1, 3, 0, 0, 1);
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
				addRunes(runeNeeds, 1, 5, 0, 0, 0);
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
				addRunes(runeNeeds, 2, 0, 0, 0, 2);
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
				addRunes(runeNeeds, 3, 0, 0, 0, 10);
				addRuneNeed(runeNeeds, "Astral rune", 3);
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
				addRunes(runeNeeds, 1, 0, 1, 0, 0);
				addRuneNeed(runeNeeds, "Soul rune", 1);
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
				addRunes(runeNeeds, 1, 0, 0, 0, 0);
				addRuneNeed(runeNeeds, "Nature rune", 1);
				addRuneNeed(runeNeeds, "Soul rune", 1);
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
				addRunes(runeNeeds, 2, 0, 0, 2, 0);
			}
		}
	}

	private void addTravel(Map<String, MutableLine> lines, String name)
	{
		addTravelUse(lines, name, resolve(name));
	}

	private void addChargedTravel(Map<String, MutableLine> lines, String name)
	{
		addTravelUse(lines, name, resolveCharged(name));
	}

	private void addTravelUse(Map<String, MutableLine> lines, String name, int[] ids)
	{
		if (ids.length == 0)
		{
			ids = explicitIds(name);
		}
		if (ids.length == 0)
		{
			return;
		}
		String key = SupplyGroup.TRAVEL + ":" + name;
		MutableLine line = lines.get(key);
		if (line == null)
		{
			line = new MutableLine(SupplyGroup.TRAVEL, name, 1, ids, equipmentSlot(name), false);
			lines.put(key, line);
		}
		line.uses++;
	}

	private static int[] explicitIds(String name)
	{
		switch (name)
		{
			case "Pendant of ates":
				return new int[]{ItemID.PENDANT_OF_ATES};
			case "Divine rune pouch":
				return new int[]{ItemID.DIVINE_RUNE_POUCH};
			default:
				return new int[0];
		}
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
		String query = name.toLowerCase(Locale.ROOT);
		List<ItemPrice> matching = new ArrayList<>();
		for (ItemPrice price : prices)
		{
			String itemName = itemManager.getItemComposition(price.getId()).getName()
				.toLowerCase(Locale.ROOT);
			if (matchesVariant(itemName, query))
			{
				matching.add(price);
			}
		}
		if (!matching.isEmpty())
		{
			prices = matching;
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

	private static boolean matchesVariant(String itemName, String query)
	{
		return itemName.equals(query)
			|| itemName.startsWith(query + " ")
			|| itemName.startsWith(query + "(")
			|| (query.equals("quetzal whistle") && itemName.endsWith(" quetzal whistle"));
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

	private void addRunes(Map<String, Integer> runeNeeds, int law, int air, int earth, int fire,
		int water)
	{
		addRuneNeed(runeNeeds, "Law rune", law);
		addElementalRunes(runeNeeds, air, earth, fire, water);
	}

	private void addElementalRunes(Map<String, Integer> runeNeeds, int air, int earth, int fire,
		int water)
	{
		addRuneNeed(runeNeeds, "Air rune", air);
		addRuneNeed(runeNeeds, "Water rune", water);
		addRuneNeed(runeNeeds, "Earth rune", earth);
		addRuneNeed(runeNeeds, "Fire rune", fire);
	}

	private static void addRuneNeed(Map<String, Integer> runeNeeds, String name, int need)
	{
		if (need > 0)
		{
			runeNeeds.merge(name, need, Integer::sum);
		}
	}

	private void addRuneLine(Map<String, MutableLine> lines, String name, int need)
	{
		if (need > 0)
		{
			add(lines, SupplyGroup.RUNES, name, need, resolve(name), false);
		}
	}

	/**
	 * Plans runes after all spell requirements are known. The pouch contents are used first, then
	 * combination runes from the bank/inventory, then whatever single runes remain.
	 */
	private void planRunes(Map<String, MutableLine> lines, Map<String, Integer> runeNeeds,
		ItemContainer bank, ItemContainer inventory, ItemContainer worn)
	{
		if (runeNeeds.isEmpty())
		{
			return;
		}
		log.debug("planRunes: reading pouch");
		Map<String, Integer> pouch = readRunePouch();
		log.debug("planRunes: pouch={}", pouch);
		int pouchId = pouchItemId(bank, inventory, worn);
		if (pouchId > 0 && !pouch.isEmpty())
		{
			log.debug("planRunes: subtracting pouch");
			boolean used = subtractPouch(runeNeeds, pouch);
			if (used)
			{
				String name = pouchName(pouchId);
				add(lines, SupplyGroup.TOOLS, name, 1, new int[]{pouchId}, false);
				setDetails(lines, SupplyGroup.TOOLS, name,
					pouchDetails(pouch, allNeedsMet(runeNeeds)));
			}
			log.debug("planRunes: pouch subtracted, needs={}", runeNeeds);
		}
		log.debug("planRunes: using bank combinations");
		useCombinationRunes(lines, runeNeeds, bank, inventory, worn);
		log.debug("planRunes: combinations done, needs={}", runeNeeds);
		for (Map.Entry<String, Integer> entry : runeNeeds.entrySet())
		{
			addRuneLine(lines, entry.getKey(), entry.getValue());
		}
		log.debug("planRunes: finished");
	}

	private Map<String, Integer> readRunePouch()
	{
		Map<String, Integer> runes = new LinkedHashMap<>();
		log.debug("readRunePouch: reading enum");
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		log.debug("readRunePouch: enum={}", runeEnum);
		if (runeEnum == null)
		{
			return runes;
		}
		for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
		{
			int quantity = client.getVarbitValue(RUNE_POUCH_QTY_VARBITS[i]);
			int rune = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			log.debug("readRunePouch: slot {} qty={} type={}", i, quantity, rune);
			if (quantity <= 0 || rune < 0)
			{
				continue;
			}
			int itemId = runeEnum.getIntValue(rune);
			if (itemId <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(itemId);
			String name = itemManager.getItemComposition(canonical).getName();
			runes.merge(name, quantity, Integer::sum);
		}
		return runes;
	}

	private boolean subtractPouch(Map<String, Integer> needs, Map<String, Integer> pouch)
	{
		Map<String, Integer> available = new LinkedHashMap<>(pouch);
		boolean used = consumeCombinations(needs, available, null, false);
		for (Map.Entry<String, Integer> entry : pouch.entrySet())
		{
			if (isCombination(entry.getKey()))
			{
				continue;
			}
			int need = needs.getOrDefault(entry.getKey(), 0);
			int cover = Math.min(need, entry.getValue());
			if (cover > 0)
			{
				needs.put(entry.getKey(), need - cover);
				used = true;
			}
		}
		return used;
	}

	private void useCombinationRunes(Map<String, MutableLine> lines, Map<String, Integer> needs,
		ItemContainer bank, ItemContainer inventory, ItemContainer worn)
	{
		Map<String, Integer> available = new LinkedHashMap<>();
		for (String[] combo : COMBINATION_RUNES)
		{
			int[] ids = resolve(combo[0]);
			int count = count(bank, ids) + count(inventory, ids) + count(worn, ids);
			if (count > 0)
			{
				available.put(combo[0], count);
			}
		}
		consumeCombinations(needs, available, lines, true);
	}

	/**
	 * Consumes combination runes from {@code available}. A two-element combination first covers a
	 * pair when both elements are needed; otherwise it can still cover one needed element, since
	 * either component may be used. This is what lets a pouch's lava/mist runes cover a spell that
	 * only needs one of their elements.
	 */
	private boolean consumeCombinations(Map<String, Integer> needs,
		Map<String, Integer> available, Map<String, MutableLine> lines, boolean addLines)
	{
		boolean used = false;
		boolean progress = true;
		int guard = 0;
		while (progress)
		{
			if (++guard > 10000)
			{
				log.warn("Combination rune planning aborted after {} iterations", guard);
				break;
			}
			progress = false;
			int best = -1;
			int bestCover = 0;
			boolean bestBoth = false;
			for (int i = 0; i < COMBINATION_RUNES.length; i++)
			{
				String name = COMBINATION_RUNES[i][0];
				int quantity = available.getOrDefault(name, 0);
				if (quantity <= 0)
				{
					continue;
				}
				String first = COMBINATION_RUNES[i][1];
				int needFirst = needs.getOrDefault(first, 0);
				if (COMBINATION_RUNES[i].length == 2)
				{
					int cover = Math.min(quantity, needFirst);
					if (cover > bestCover)
					{
						best = i;
						bestCover = cover;
						bestBoth = false;
					}
					continue;
				}
				String second = COMBINATION_RUNES[i][2];
				int needSecond = needs.getOrDefault(second, 0);
				int both = Math.min(quantity, Math.min(needFirst, needSecond));
				int single = (needFirst > 0 ^ needSecond > 0)
					? Math.min(quantity, Math.max(needFirst, needSecond)) : 0;
				if (both > 0)
				{
					if (both > bestCover || !bestBoth)
					{
						best = i;
						bestCover = both;
						bestBoth = true;
					}
				}
				else if (!bestBoth && single > bestCover)
				{
					best = i;
					bestCover = single;
					bestBoth = false;
				}
			}
			if (best >= 0)
			{
				String name = COMBINATION_RUNES[best][0];
				String first = COMBINATION_RUNES[best][1];
				if (bestBoth)
				{
					String second = COMBINATION_RUNES[best][2];
					needs.put(first, needs.getOrDefault(first, 0) - bestCover);
					needs.put(second, needs.getOrDefault(second, 0) - bestCover);
				}
				else if (COMBINATION_RUNES[best].length > 2)
				{
					// A combination rune can cover either component when only one is
					// needed. Subtract from the component that is actually needed, not
					// always from the first one; otherwise the second need never drops
					// and a large bank stack is consumed one rune per iteration.
					String second = COMBINATION_RUNES[best][2];
					if (needs.getOrDefault(first, 0) > 0)
					{
						needs.put(first, needs.getOrDefault(first, 0) - bestCover);
					}
					else if (needs.getOrDefault(second, 0) > 0)
					{
						needs.put(second, needs.getOrDefault(second, 0) - bestCover);
					}
				}
				else
				{
					needs.put(first, needs.getOrDefault(first, 0) - bestCover);
				}
				available.put(name, available.getOrDefault(name, 0) - bestCover);
				if (addLines)
				{
					addRuneLine(lines, name, bestCover);
				}
				used = true;
				progress = true;
			}
		}
		return used;
	}

	private static boolean isCombination(String name)
	{
		for (String[] combo : COMBINATION_RUNES)
		{
			if (combo[0].equalsIgnoreCase(name))
			{
				return true;
			}
		}
		return false;
	}

	private static int pouchItemId(ItemContainer bank, ItemContainer inventory, ItemContainer worn)
	{
		for (int id : RUNE_POUCH_IDS)
		{
			if (count(bank, id) + count(inventory, id) + count(worn, id) > 0)
			{
				return id;
			}
		}
		return 0;
	}

	private static String pouchName(int itemId)
	{
		return itemId == ItemID.BH_RUNE_POUCH ? "Rune pouch" : "Divine rune pouch";
	}

	private static boolean allNeedsMet(Map<String, Integer> needs)
	{
		for (int need : needs.values())
		{
			if (need > 0)
			{
				return false;
			}
		}
		return true;
	}

	private static String pouchDetails(Map<String, Integer> pouch, boolean complete)
	{
		StringBuilder details = new StringBuilder("Holds ");
		boolean first = true;
		for (Map.Entry<String, Integer> entry : pouch.entrySet())
		{
			if (!first)
			{
				details.append(", ");
			}
			details.append(entry.getValue()).append(' ').append(entry.getKey());
			first = false;
		}
		details.append(complete
			? "; covers the run's spell runes."
			: "; covers part of the run's spell runes.");
		return details.toString();
	}

	private static void setDetails(Map<String, MutableLine> lines, SupplyGroup group, String name,
		String details)
	{
		MutableLine line = lines.get(group + ":" + name);
		if (line != null)
		{
			line.details = details;
		}
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
		List<ItemPrice> exact = new ArrayList<>();
		for (ItemPrice price : prices)
		{
			if (itemManager.getItemComposition(price.getId()).getName().equalsIgnoreCase(name))
			{
				exact.add(price);
			}
		}
		List<ItemPrice> candidates = exact.isEmpty() ? prices : exact;
		int[] unnoted = candidates.stream()
			.mapToInt(ItemPrice::getId)
			.filter(id -> itemManager.getItemComposition(id).getNote() == 0)
			.toArray();
		if (unnoted.length > 0)
		{
			return unnoted;
		}
		return candidates.stream().mapToInt(ItemPrice::getId).toArray();
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

	private int travelNeed(MutableLine line, ItemContainer bank, ItemContainer inventory,
		ItemContainer worn)
	{
		int capacity = capacityFor(line.name);
		if (capacity > 1)
		{
			int owned = bestOwnedCapacity(line.itemIds, bank, inventory, worn);
			if (owned > 0)
			{
				capacity = owned;
			}
		}
		if (capacity <= 0)
		{
			return 1;
		}
		return Math.max(1, (line.uses + capacity - 1) / capacity);
	}

	/** Uses per item; 0 means reusable, 1 means one use per item. */
	private static int capacityFor(String name)
	{
		switch (name)
		{
			case "Pendant of ates":
			case "Xeric's talisman":
			case "Ardougne cloak":
			case "Explorer's ring":
			case "Farming cape":
			case "Eternal teleport crystal":
			case "Royal seed pod":
			case "Ectophial":
				return 0;
			case "Ring of wealth":
				return 5;
			case "Skills necklace":
				return 6;
			case "Slayer ring":
			case "Ring of dueling":
			case "Games necklace":
				return 8;
			case "Combat bracelet":
			case "Amulet of glory":
				return 6;
			case "Burning amulet":
			case "Digsite pendant":
			case "Necklace of passage":
			case "Teleport crystal":
			case "Crystal teleport seed":
			case "Quetzal whistle":
				return 5;
			default:
				return 1;
		}
	}

	private int bestOwnedCapacity(int[] itemIds, ItemContainer bank, ItemContainer inventory,
		ItemContainer worn)
	{
		int best = 0;
		for (int id : itemIds)
		{
			if (count(bank, id) + count(inventory, id) + count(worn, id) <= 0)
			{
				continue;
			}
			String itemName = itemManager.getItemComposition(id).getName();
			String lower = itemName.toLowerCase(Locale.ROOT);
			int charges = chargeValue(itemName);
			if (charges > 0)
			{
				best = Math.max(best, charges);
			}
			else if (lower.contains("perfected quetzal"))
			{
				best = Math.max(best, 15);
			}
			else if (lower.contains("enhanced quetzal"))
			{
				best = Math.max(best, 10);
			}
			else if (lower.contains("basic quetzal"))
			{
				best = Math.max(best, 5);
			}
		}
		return best;
	}

	private static int chargeValue(String itemName)
	{
		int open = itemName.lastIndexOf('(');
		int close = itemName.lastIndexOf(')');
		if (open < 0 || close <= open + 1)
		{
			return 0;
		}
		try
		{
			return Integer.parseInt(itemName.substring(open + 1, close).trim());
		}
		catch (NumberFormatException e)
		{
			return 0;
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
			case "Slayer ring":
			case "Ring of dueling":
				return "ring";
			case "Skills necklace":
			case "Xeric's talisman":
			case "Pendant of ates":
			case "Games necklace":
			case "Amulet of glory":
			case "Burning amulet":
			case "Digsite pendant":
			case "Necklace of passage":
				return "neck";
			case "Combat bracelet":
				return "hands";
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
		private int uses;
		private final int[] itemIds;
		private final String equipmentSlot;
		private final boolean noted;
		private String details;

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

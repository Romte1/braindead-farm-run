package com.romte.farmrunplanner.domain.patch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Every crop supported by the first release: allotments, herbs, trees and fruit trees.
 *
 * <p>Growth stages and tick lengths follow RuneLite's Time Tracking plugin. Levels and
 * gardener payments follow the OSRS Wiki. A crop is ready after {@code (stages - 1)} growth
 * ticks of its patch type's tick length.</p>
 */
public enum Crop
{
	// Herbs: 20-minute ticks.
	GUAM(PatchType.HERB, "Guam", 9, "Guam seed", 5, null, 0),
	MARRENTILL(PatchType.HERB, "Marrentill", 14, "Marrentill seed", 5, null, 0),
	TARROMIN(PatchType.HERB, "Tarromin", 19, "Tarromin seed", 5, null, 0),
	HARRALANDER(PatchType.HERB, "Harralander", 26, "Harralander seed", 5, null, 0),
	RANARR(PatchType.HERB, "Ranarr", 32, "Ranarr seed", 5, null, 0),
	TOADFLAX(PatchType.HERB, "Toadflax", 38, "Toadflax seed", 5, null, 0),
	IRIT(PatchType.HERB, "Irit", 44, "Irit seed", 5, null, 0),
	AVANTOE(PatchType.HERB, "Avantoe", 50, "Avantoe seed", 5, null, 0),
	KWUARM(PatchType.HERB, "Kwuarm", 56, "Kwuarm seed", 5, null, 0),
	SNAPDRAGON(PatchType.HERB, "Snapdragon", 62, "Snapdragon seed", 5, null, 0),
	HUASCA(PatchType.HERB, "Huasca", 65, "Huasca seed", 5, null, 0),
	CADANTINE(PatchType.HERB, "Cadantine", 67, "Cadantine seed", 5, null, 0),
	LANTADYME(PatchType.HERB, "Lantadyme", 73, "Lantadyme seed", 5, null, 0),
	DWARF_WEED(PatchType.HERB, "Dwarf weed", 79, "Dwarf weed seed", 5, null, 0),
	TORSTOL(PatchType.HERB, "Torstol", 85, "Torstol seed", 5, null, 0),

	// Allotments: 10-minute ticks, 3 seeds per patch.
	POTATO(PatchType.ALLOTMENT, "Potato", 1, "Potato seed", 5, "Compost", 2),
	ONION(PatchType.ALLOTMENT, "Onion", 5, "Onion seed", 5, "Potatoes(10)", 1),
	CABBAGE(PatchType.ALLOTMENT, "Cabbage", 7, "Cabbage seed", 5, "Onions(10)", 1),
	TOMATO(PatchType.ALLOTMENT, "Tomato", 12, "Tomato seed", 5, "Cabbages(10)", 2),
	SWEETCORN(PatchType.ALLOTMENT, "Sweetcorn", 20, "Sweetcorn seed", 7, "Jute fibre", 10),
	STRAWBERRY(PatchType.ALLOTMENT, "Strawberry", 31, "Strawberry seed", 7, "Apples(5)", 1),
	WATERMELON(PatchType.ALLOTMENT, "Watermelon", 47, "Watermelon seed", 9, "Curry leaf", 10),
	SNAPE_GRASS(PatchType.ALLOTMENT, "Snape grass", 61, "Snape grass seed", 8, "Jangerberries", 5),

	// Trees: 40-minute ticks.
	OAK(PatchType.TREE, "Oak", 15, "Oak sapling", 5, "Tomatoes(5)", 1),
	WILLOW(PatchType.TREE, "Willow", 30, "Willow sapling", 7, "Apples(5)", 1),
	MAPLE(PatchType.TREE, "Maple", 45, "Maple sapling", 9, "Oranges(5)", 1),
	YEW(PatchType.TREE, "Yew", 60, "Yew sapling", 11, "Cactus spine", 10),
	MAGIC(PatchType.TREE, "Magic", 75, "Magic sapling", 13, "Coconut", 25),

	// Fruit trees: 160-minute ticks.
	APPLE(PatchType.FRUIT_TREE, "Apple", 27, "Apple sapling", 7, "Sweetcorn", 9),
	BANANA(PatchType.FRUIT_TREE, "Banana", 33, "Banana sapling", 7, "Apples(5)", 4),
	ORANGE(PatchType.FRUIT_TREE, "Orange", 39, "Orange sapling", 7, "Strawberries(5)", 3),
	CURRY(PatchType.FRUIT_TREE, "Curry", 42, "Curry sapling", 7, "Bananas(5)", 5),
	PINEAPPLE(PatchType.FRUIT_TREE, "Pineapple", 51, "Pineapple sapling", 7, "Watermelon", 10),
	PAPAYA(PatchType.FRUIT_TREE, "Papaya", 57, "Papaya sapling", 7, "Pineapple", 10),
	PALM(PatchType.FRUIT_TREE, "Palm", 68, "Palm sapling", 7, "Papaya fruit", 15),
	DRAGONFRUIT(PatchType.FRUIT_TREE, "Dragonfruit", 81, "Dragonfruit sapling", 7, "Coconut", 15);

	private static final Map<PatchType, List<Crop>> BY_TYPE;

	static
	{
		Map<PatchType, List<Crop>> byType = new EnumMap<>(PatchType.class);
		for (PatchType type : PatchType.values())
		{
			List<Crop> crops = new ArrayList<>();
			for (Crop crop : values())
			{
				if (crop.type == type)
				{
					crops.add(crop);
				}
			}
			byType.put(type, Collections.unmodifiableList(crops));
		}
		BY_TYPE = Collections.unmodifiableMap(byType);
	}

	private final PatchType type;
	private final String displayName;
	private final int level;
	private final String plantedItemName;
	private final int stages;
	private final String paymentItem;
	private final int paymentQuantity;

	Crop(PatchType type, String displayName, int level, String plantedItemName, int stages,
		String paymentItem, int paymentQuantity)
	{
		this.type = type;
		this.displayName = displayName;
		this.level = level;
		this.plantedItemName = plantedItemName;
		this.stages = stages;
		this.paymentItem = paymentItem;
		this.paymentQuantity = paymentQuantity;
	}

	public PatchType getType()
	{
		return type;
	}

	public String getDisplayName()
	{
		return displayName;
	}

	public int getLevel()
	{
		return level;
	}

	/** Seed or sapling used to plant the crop. */
	public String getPlantedItemName()
	{
		return plantedItemName;
	}

	public int getStages()
	{
		return stages;
	}

	public String getPaymentItem()
	{
		return paymentItem;
	}

	public int getPaymentQuantity()
	{
		return paymentQuantity;
	}

	/** Allotments take 3 seeds per patch; everything else takes one seed or sapling. */
	public int getSeedsPerPatch()
	{
		return type == PatchType.ALLOTMENT ? 3 : 1;
	}

	/** Minutes from planting until the crop is ready to harvest or check. */
	public int getGrowthMinutes()
	{
		return (stages - 1) * type.getTickMinutes();
	}

	public boolean needsPayment()
	{
		return paymentItem != null && paymentQuantity > 0;
	}

	/** Crops of one patch type, in declaration order. */
	public static List<Crop> forType(PatchType type)
	{
		return BY_TYPE.getOrDefault(type, Collections.emptyList());
	}

	/** Case-insensitive lookup by display name, or null if there is no such crop. */
	public static Crop byDisplayName(String name)
	{
		if (name == null)
		{
			return null;
		}
		for (Crop crop : values())
		{
			if (crop.displayName.equalsIgnoreCase(name))
			{
				return crop;
			}
		}
		return null;
	}
}

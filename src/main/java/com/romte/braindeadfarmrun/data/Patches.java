package com.romte.braindeadfarmrun.data;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The patches supported by the first release. Region lists and varbits follow RuneLite's Time
 * Tracking plugin; tiles are the farming patch points from RuneLite's world map data.
 *
 * <p>The nearby fairy ring and nearest bank are route hints for later phases.</p>
 */
public final class Patches
{
	private static final List<PatchDefinition> ALL = List.of(
		// Trees.
		new PatchDefinition("varrock_tree", "Varrock tree", PatchType.TREE, 3229, 3459, 0, new int[]{12854, 12853}, 4771, null, "Varrock East bank"),
		new PatchDefinition("lumbridge_tree", "Lumbridge tree", PatchType.TREE, 3193, 3231, 0, new int[]{12594, 12850}, 4771, null, "Lumbridge Castle bank (top floor)"),
		new PatchDefinition("falador_tree", "Falador tree (park)", PatchType.TREE, 3004, 3373, 0, new int[]{11828, 12084}, 4771, null, "Falador West bank"),
		new PatchDefinition("taverley_tree", "Taverley tree", PatchType.TREE, 2936, 3438, 0, new int[]{11573, 11829}, 4771, null, "Falador West bank"),
		new PatchDefinition("gnome_tree", "Gnome Stronghold tree", PatchType.TREE, 2436, 3415, 0, new int[]{9781, 9525, 9526, 9782}, 4771, null, "Grand Tree bank"),
		new PatchDefinition("farming_guild_tree", "Farming Guild tree", PatchType.TREE, 1232, 3736, 0, new int[]{4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179}, 7905, "CIR", "Farming Guild bank"),
		new PatchDefinition("nemus_retreat_tree", "Nemus Retreat tree", PatchType.TREE, 1367, 3322, 0, new int[]{5427, 5428, 5684}, 4771, null, null),

		// Fruit trees.
		new PatchDefinition("gnome_fruit", "Gnome Stronghold fruit tree", PatchType.FRUIT_TREE, 2476, 3446, 0, new int[]{9781, 9525, 9526, 9782}, 4772, null, "Grand Tree bank"),
		new PatchDefinition("tree_gnome_village_fruit", "Tree Gnome Village fruit tree", PatchType.FRUIT_TREE, 2490, 3180, 0, new int[]{9777, 10033}, 4771, null, "Grand Tree bank"),
		new PatchDefinition("catherby_fruit", "Catherby fruit tree", PatchType.FRUIT_TREE, 2860, 3433, 0, new int[]{11317}, 4771, null, "Catherby bank"),
		new PatchDefinition("brimhaven_fruit", "Brimhaven fruit tree", PatchType.FRUIT_TREE, 2765, 3213, 0, new int[]{11058, 11057}, 4771, null, "Ardougne South bank"),
		new PatchDefinition("farming_guild_fruit", "Farming Guild fruit tree", PatchType.FRUIT_TREE, 1242, 3758, 0, new int[]{4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179}, 7909, "CIR", "Farming Guild bank"),
		new PatchDefinition("kastori_fruit", "Kastori fruit tree", PatchType.FRUIT_TREE, 1350, 3057, 0, new int[]{5423, 5167, 5424}, 4772, null, null),
		new PatchDefinition("lletya_fruit", "Lletya fruit tree", PatchType.FRUIT_TREE, 2347, 3162, 0, new int[]{9265, 11103}, 4771, null, "Lletya bank"),

		// Calquats.
		new PatchDefinition("tai_bwo_calquat", "Tai Bwo Wannai calquat", PatchType.CALQUAT, 2796, 3101, 0, new int[]{11056}, 4771, "CKR", null),
		new PatchDefinition("summer_shore_calquat", "Summer Shore calquat", PatchType.CALQUAT, 3128, 2405, 0, new int[]{12581, 12325, 12326, 12327, 12580, 12582, 12583, 12836, 12837, 12838, 12839, 13092, 13093, 13194}, 4773, "CJQ", null),
		new PatchDefinition("kastori_calquat", "Kastori calquat", PatchType.CALQUAT, 1367, 3032, 0, new int[]{5423, 5167, 5424}, 4771, null, "Civitas illa Fortis bank"),

		// Hardwood trees.
		new PatchDefinition("fossil_hardwood_east", "Fossil Island hardwood (east)", PatchType.HARDWOOD_TREE, 3716, 3837, 0, new int[]{14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163}, 4771, null, null),
		new PatchDefinition("fossil_hardwood_middle", "Fossil Island hardwood (middle)", PatchType.HARDWOOD_TREE, 3710, 3837, 0, new int[]{14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163}, 4772, null, null),
		new PatchDefinition("fossil_hardwood_west", "Fossil Island hardwood (west)", PatchType.HARDWOOD_TREE, 3704, 3837, 0, new int[]{14651, 14907, 14908, 15164, 14652, 14906, 14650, 15162, 15163}, 4773, null, null),
		new PatchDefinition("locus_oasis_hardwood", "Locus Oasis hardwood", PatchType.HARDWOOD_TREE, 1687, 2972, 0, new int[]{6702, 6446}, 4771, null, null),
		new PatchDefinition("anglers_retreat_hardwood", "Anglers' Retreat hardwood", PatchType.HARDWOOD_TREE, 2471, 2703, 0, new int[]{9770}, 4771, null, null),

		// Seaweed.
		new PatchDefinition("fossil_seaweed_north", "Fossil Island seaweed (north)", PatchType.SEAWEED, 3734, 10276, 0, new int[]{15008}, 4771, null, null),
		new PatchDefinition("fossil_seaweed_south", "Fossil Island seaweed (south)", PatchType.SEAWEED, 3734, 10266, 0, new int[]{15008}, 4772, null, null),

		// Herbs.
		new PatchDefinition("falador_herb", "Falador herb", PatchType.HERB, 3058, 3311, 0, new int[]{12083}, 4774, null, "Falador East bank"),
		new PatchDefinition("catherby_herb", "Catherby herb", PatchType.HERB, 2813, 3463, 0, new int[]{11062, 11061, 11317, 11318}, 4774, null, "Catherby bank"),
		new PatchDefinition("ardougne_herb", "Ardougne herb", PatchType.HERB, 2670, 3374, 0, new int[]{10548}, 4774, "BLR", "Ardougne North bank"),
		new PatchDefinition("morytania_herb", "Morytania herb", PatchType.HERB, 3605, 3529, 0, new int[]{14391, 14390}, 4774, "ALQ", "Port Phasmatys bank"),
		new PatchDefinition("hosidius_herb", "Hosidius herb", PatchType.HERB, 1738, 3550, 0, new int[]{6967, 6711}, 4774, "AKR", "Hosidius kitchen bank"),
		new PatchDefinition("farming_guild_herb", "Farming Guild herb", PatchType.HERB, 1238, 3726, 0, new int[]{4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179}, 4775, "CIR", "Farming Guild bank"),
		new PatchDefinition("varlamore_herb", "Varlamore herb (Ortus Farm)", PatchType.HERB, 1582, 3094, 0, new int[]{6192, 6191, 6193, 6447, 6448, 6449}, 4774, "AJP", "Civitas illa Fortis bank"),
		new PatchDefinition("troll_herb", "Troll Stronghold herb", PatchType.HERB, 2826, 3694, 0, new int[]{11321}, 4771, null, null),
		new PatchDefinition("weiss_herb", "Weiss herb", PatchType.HERB, 2848, 3934, 0, new int[]{11325}, 4771, null, null),
		new PatchDefinition("harmony_herb", "Harmony Island herb", PatchType.HERB, 3789, 2837, 0, new int[]{15148}, 4772, null, null),

		// Allotments: two patches per location.
		new PatchDefinition("falador_allotment_a", "Falador allotment (north-west)", PatchType.ALLOTMENT, 3049, 3312, 0, new int[]{12083}, 4771, null, "Falador East bank"),
		new PatchDefinition("falador_allotment_b", "Falador allotment (south-east)", PatchType.ALLOTMENT, 3055, 3306, 0, new int[]{12083}, 4772, null, "Falador East bank"),
		new PatchDefinition("catherby_allotment_a", "Catherby allotment (north)", PatchType.ALLOTMENT, 2807, 3465, 0, new int[]{11062, 11061, 11317, 11318}, 4771, null, "Catherby bank"),
		new PatchDefinition("catherby_allotment_b", "Catherby allotment (south)", PatchType.ALLOTMENT, 2813, 3459, 0, new int[]{11062, 11061, 11317, 11318}, 4772, null, "Catherby bank"),
		new PatchDefinition("ardougne_allotment_a", "Ardougne allotment (north)", PatchType.ALLOTMENT, 2660, 3378, 0, new int[]{10548}, 4771, "BLR", "Ardougne North bank"),
		new PatchDefinition("ardougne_allotment_b", "Ardougne allotment (south)", PatchType.ALLOTMENT, 2666, 3372, 0, new int[]{10548}, 4772, "BLR", "Ardougne North bank"),
		new PatchDefinition("morytania_allotment_a", "Morytania allotment (north-west)", PatchType.ALLOTMENT, 3595, 3527, 0, new int[]{14391, 14390}, 4771, "ALQ", "Port Phasmatys bank"),
		new PatchDefinition("morytania_allotment_b", "Morytania allotment (south-east)", PatchType.ALLOTMENT, 3601, 3521, 0, new int[]{14391, 14390}, 4772, "ALQ", "Port Phasmatys bank"),
		new PatchDefinition("hosidius_allotment_a", "Hosidius allotment (north-east)", PatchType.ALLOTMENT, 1726, 3561, 0, new int[]{6967, 6711}, 4771, "AKR", "Hosidius kitchen bank"),
		new PatchDefinition("hosidius_allotment_b", "Hosidius allotment (south-west)", PatchType.ALLOTMENT, 1732, 3555, 0, new int[]{6967, 6711}, 4772, "AKR", "Hosidius kitchen bank"),
		new PatchDefinition("varlamore_allotment_a", "Varlamore (Ortus Farm) allotment (north)", PatchType.ALLOTMENT, 1584, 3104, 0, new int[]{6192, 6191, 6193, 6447, 6448, 6449}, 4771, "AJP", "Civitas illa Fortis bank"),
		new PatchDefinition("varlamore_allotment_b", "Varlamore (Ortus Farm) allotment (south)", PatchType.ALLOTMENT, 1590, 3098, 0, new int[]{6192, 6191, 6193, 6447, 6448, 6449}, 4772, "AJP", "Civitas illa Fortis bank"),
		new PatchDefinition("guild_allotment_a", "Farming Guild allotment (north)", PatchType.ALLOTMENT, 1266, 3733, 0, new int[]{4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179}, 4773, "CIR", "Farming Guild bank"),
		new PatchDefinition("guild_allotment_b", "Farming Guild allotment (south)", PatchType.ALLOTMENT, 1272, 3727, 0, new int[]{4922, 4665, 4666, 4667, 4921, 4923, 5177, 5178, 5179}, 4774, "CIR", "Farming Guild bank")
	);

	private static final Map<PatchType, List<PatchDefinition>> BY_TYPE;
	private static final Map<String, PatchDefinition> BY_ID;

	static
	{
		Map<PatchType, List<PatchDefinition>> byType = new EnumMap<>(PatchType.class);
		Map<String, PatchDefinition> byId = new HashMap<>();
		for (PatchDefinition patch : ALL)
		{
			byType.computeIfAbsent(patch.getType(), type -> new ArrayList<>()).add(patch);
			PatchDefinition previous = byId.put(patch.getId(), patch);
			if (previous != null)
			{
				throw new IllegalStateException("Duplicate patch id: " + patch.getId());
			}
		}

		Map<PatchType, List<PatchDefinition>> immutableByType = new EnumMap<>(PatchType.class);
		for (Map.Entry<PatchType, List<PatchDefinition>> entry : byType.entrySet())
		{
			immutableByType.put(entry.getKey(), Collections.unmodifiableList(entry.getValue()));
		}
		BY_TYPE = Collections.unmodifiableMap(immutableByType);
		BY_ID = Collections.unmodifiableMap(byId);
	}

	private Patches()
	{
	}

	public static List<PatchDefinition> all()
	{
		return ALL;
	}

	public static List<PatchDefinition> forType(PatchType type)
	{
		return BY_TYPE.getOrDefault(type, Collections.emptyList());
	}

	public static PatchDefinition byId(String id)
	{
		return BY_ID.get(id);
	}
}

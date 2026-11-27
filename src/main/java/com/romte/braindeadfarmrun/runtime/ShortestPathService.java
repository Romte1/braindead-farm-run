package com.romte.braindeadfarmrun.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * Uses Shortest Path's public plugin-message API to query the route for each stop and read the
 * transport hints it would use. The query uses the Bank + Inventory teleport-item setting.
 */
@Slf4j
@Singleton
public class ShortestPathService
{
	private static final String NAMESPACE = "shortestpath";
	private static final String ID_PREFIX = "braindead-";
	private static final int SPELLBOOK_STANDARD = 0;
	private static final int SPELLBOOK_ANCIENT = 1;
	private static final int SPELLBOOK_LUNAR = 2;
	private static final int SPELLBOOK_ARCEUUS = 3;

	private final EventBus eventBus;
	private final Client client;
	private final ConfigManager configManager;
	private final Map<String, QueryResult> results = new ConcurrentHashMap<>();

	@Inject
	ShortestPathService(EventBus eventBus, Client client, ConfigManager configManager)
	{
		this.eventBus = eventBus;
		this.client = client;
		this.configManager = configManager;
	}

	/**
	 * Ask Shortest Path to consider teleport items from the bank and inventory for this run, and
	 * block the tablets of spells the player can cast on their current spellbook so the route
	 * uses runes instead. Sending only the config override applies it without a displayed path.
	 */
	public void applyInventoryAndBank()
	{
		Map<String, Object> data = new HashMap<>();
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("useTeleportationItems", "INVENTORY_AND_BANK");
		String blocked = blockedTabletsForSpellbook();
		log.debug("Shortest Path spellbook={} blockedTablets={}",
			client.getVarbitValue(VarbitID.SPELLBOOK), blocked);
		if (!blocked.isEmpty())
		{
			String existing = configManager.getConfiguration(NAMESPACE, "blockedTeleportItems");
			if (existing != null && !existing.isBlank())
			{
				blocked = existing + "," + blocked;
			}
			overrides.put("blockedTeleportItems", blocked);
		}
		data.put("config", overrides);
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	private String blockedTabletsForSpellbook()
	{
		int spellbook = client.getVarbitValue(VarbitID.SPELLBOOK);
		int magic = client.getRealSkillLevel(Skill.MAGIC);
		StringBuilder blocked = new StringBuilder();
		switch (spellbook)
		{
			case SPELLBOOK_STANDARD:
				addBlockedTablet(blocked, magic, 25, null, 8007); // Varrock
				addBlockedTablet(blocked, magic, 31, null, 8008); // Lumbridge
				addBlockedTablet(blocked, magic, 37, null, 8009); // Falador
				addBlockedTablet(blocked, magic, 40, null, 8013); // Teleport to House
				addBlockedTablet(blocked, magic, 45, null, 8010); // Camelot
				addBlockedTablet(blocked, magic, 48, Quest.CLIENT_OF_KOUREND, 19651); // Kourend
				addBlockedTablet(blocked, magic, 51, Quest.PLAGUE_CITY, 8011); // Ardougne
				addBlockedTablet(blocked, magic, 54, Quest.TWILIGHTS_PROMISE, 28824); // Civitas
				addBlockedTablet(blocked, magic, 58, Quest.WATCHTOWER, 8012); // Watchtower
				addBlockedTablet(blocked, magic, 61, Quest.EADGARS_RUSE, 11747); // Trollheim
				break;
			case SPELLBOOK_ANCIENT:
				addBlockedTablet(blocked, magic, 54, Quest.DESERT_TREASURE_I, 12781); // Paddewwa
				addBlockedTablet(blocked, magic, 60, Quest.DESERT_TREASURE_I, 12782); // Senntisten
				addBlockedTablet(blocked, magic, 66, Quest.DESERT_TREASURE_I, 12779); // Kharyll
				addBlockedTablet(blocked, magic, 72, Quest.DESERT_TREASURE_I, 12780); // Lassar
				addBlockedTablet(blocked, magic, 78, Quest.DESERT_TREASURE_I, 12777); // Dareeyak
				addBlockedTablet(blocked, magic, 84, Quest.DESERT_TREASURE_I, 12776); // Carrallangar
				addBlockedTablet(blocked, magic, 90, Quest.DESERT_TREASURE_I, 12775); // Annakarl
				addBlockedTablet(blocked, magic, 96, Quest.DESERT_TREASURE_I, 12778); // Ghorrock
				break;
			case SPELLBOOK_LUNAR:
				addBlockedTablet(blocked, magic, 69, Quest.LUNAR_DIPLOMACY, 24949); // Moonclan
				addBlockedTablet(blocked, magic, 71, Quest.LUNAR_DIPLOMACY, 24951); // Ourania
				addBlockedTablet(blocked, magic, 72, Quest.LUNAR_DIPLOMACY, 24953); // Waterbirth
				addBlockedTablet(blocked, magic, 75, Quest.LUNAR_DIPLOMACY, 24955); // Barbarian
				addBlockedTablet(blocked, magic, 78, Quest.LUNAR_DIPLOMACY, 24957); // Khazard
				addBlockedTablet(blocked, magic, 85, Quest.LUNAR_DIPLOMACY, 24959); // Fishing Guild
				addBlockedTablet(blocked, magic, 87, Quest.LUNAR_DIPLOMACY, 24961); // Catherby
				addBlockedTablet(blocked, magic, 89, Quest.LUNAR_DIPLOMACY, 24963); // Ice Plateau
				break;
			case SPELLBOOK_ARCEUUS:
				if (Quest.A_KINGDOM_DIVIDED.getState(client) != QuestState.FINISHED)
				{
					break;
				}
				addBlockedTablet(blocked, magic, 17, null, 19615); // Draynor Manor
				addBlockedTablet(blocked, magic, 28, null, 19617); // Mind Altar
				addBlockedTablet(blocked, magic, 40, null, 19619); // Salve Graveyard
				addBlockedTablet(blocked, magic, 48, null, 19621); // Fenkenstrain's Castle
				addBlockedTablet(blocked, magic, 61, null, 19623); // West Ardougne
				addBlockedTablet(blocked, magic, 65, null, 19625); // Harmony Island
				addBlockedTablet(blocked, magic, 71, null, 19627); // Cemetery
				addBlockedTablet(blocked, magic, 83, null, 19629); // Barrows
				addBlockedTablet(blocked, magic, 90, null, 19631); // Ape Atoll
				break;
			default:
				break;
		}
		return blocked.toString();
	}

	private void addBlockedTablet(StringBuilder blocked, int magic, int requiredMagic, Quest quest,
		int tabletId)
	{
		if (magic < requiredMagic)
		{
			return;
		}
		if (quest != null && quest.getState(client) != QuestState.FINISHED)
		{
			return;
		}
		if (blocked.length() > 0)
		{
			blocked.append(',');
		}
		blocked.append(tabletId);
	}

	public void query(int index, WorldPoint start, WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id(index));
		data.put("start", start);
		data.put("target", target);
		eventBus.post(new PluginMessage(NAMESPACE, "query", data));
	}

	public boolean hasResult(int index)
	{
		return results.containsKey(id(index));
	}

	public boolean reached(int index)
	{
		QueryResult result = results.get(id(index));
		return result != null && result.reached;
	}

	public List<String> transportsFor(int index)
	{
		QueryResult result = results.get(id(index));
		return result == null ? Collections.emptyList() : result.transports;
	}

	public List<String> allTransports()
	{
		List<String> result = new ArrayList<>();
		for (QueryResult queryResult : results.values())
		{
			result.addAll(queryResult.transports);
		}
		Collections.sort(result);
		return result;
	}

	public void clearResults()
	{
		results.clear();
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!NAMESPACE.equals(event.getNamespace()) || !"result".equals(event.getName()))
		{
			return;
		}
		Map<String, Object> data = event.getData();
		if (data == null)
		{
			return;
		}
		Object id = data.get("id");
		Object resultTransports = data.get("transports");
		if (!(id instanceof String) || !((String) id).startsWith(ID_PREFIX)
			|| !(resultTransports instanceof List))
		{
			return;
		}

		List<String> lines = new ArrayList<>();
		for (Object entry : (List<?>) resultTransports)
		{
			if (entry instanceof Map)
			{
				Object display = ((Map<?, ?>) entry).get("displayInfo");
				if (display instanceof String && !((String) display).isEmpty())
				{
					lines.add((String) display);
				}
			}
		}
		boolean reached = Boolean.TRUE.equals(data.get("reached"));
		results.put((String) id, new QueryResult(reached, lines));
	}

	private static String id(int index)
	{
		return ID_PREFIX + index;
	}

	private static final class QueryResult
	{
		private final boolean reached;
		private final List<String> transports;

		private QueryResult(boolean reached, List<String> transports)
		{
			this.reached = reached;
			this.transports = Collections.unmodifiableList(transports);
		}
	}
}

package com.romte.braindeadfarmrun.runtime;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * Uses Shortest Path's public plugin-message API to query the route for each stop and read the
 * transport hints it would use. The query uses the Bank + Inventory teleport-item setting.
 */
@Singleton
public class ShortestPathService
{
	private static final String NAMESPACE = "shortestpath";
	private static final String ID_PREFIX = "braindead-";

	private final EventBus eventBus;
	private final Map<String, List<String>> transports = new ConcurrentHashMap<>();

	@Inject
	ShortestPathService(EventBus eventBus)
	{
		this.eventBus = eventBus;
	}

	/** Ask Shortest Path to consider teleport items from the bank and inventory for this run. */
	public void applyInventoryAndBank(WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("target", target);
		Map<String, Object> overrides = new HashMap<>();
		overrides.put("useTeleportationItems", "INVENTORY_AND_BANK");
		data.put("config", overrides);
		eventBus.post(new PluginMessage(NAMESPACE, "path", data));
	}

	public void query(int index, WorldPoint start, WorldPoint target)
	{
		Map<String, Object> data = new HashMap<>();
		data.put("id", id(index));
		data.put("start", start);
		data.put("target", target);
		eventBus.post(new PluginMessage(NAMESPACE, "query", data));
	}

	public List<String> transportsFor(int index)
	{
		return transports.getOrDefault(id(index), Collections.emptyList());
	}

	public List<String> allTransports()
	{
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (List<String> lines : transports.values())
		{
			unique.addAll(lines);
		}
		List<String> result = new ArrayList<>(unique);
		Collections.sort(result);
		return result;
	}

	public void clearResults()
	{
		transports.clear();
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
		if (!(id instanceof String) || !(resultTransports instanceof List))
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
		transports.put((String) id, Collections.unmodifiableList(lines));
	}

	private static String id(int index)
	{
		return ID_PREFIX + index;
	}
}

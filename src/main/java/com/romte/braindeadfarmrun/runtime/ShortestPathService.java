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
	private final Map<String, QueryResult> results = new ConcurrentHashMap<>();

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
		LinkedHashSet<String> unique = new LinkedHashSet<>();
		for (QueryResult result : results.values())
		{
			unique.addAll(result.transports);
		}
		List<String> result = new ArrayList<>(unique);
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

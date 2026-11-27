package com.romte.braindeadfarmrun.runtime;

import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.planning.RoutePlanner;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

/**
 * The active farm run: selected patch types, visit order, and the next stop sent to Shortest
 * Path. The route advances automatically when the player reaches the current patch.
 */
@Singleton
public class RunSession
{
	private static final int ARRIVAL_RADIUS = 6;
	private static final String PREFIX = "<col=00bfff>Braindead Farm Run:</col> ";

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ShortestPathService shortestPath;
	private volatile List<PatchDefinition> route = Collections.emptyList();
	private volatile Set<PatchType> selectedTypes = Collections.unmodifiableSet(
		EnumSet.allOf(PatchType.class));
	private volatile int index;
	private volatile boolean active;
	private volatile boolean routeReady;
	private WorldPoint lastTarget;

	@Inject
	RunSession(Client client, ClientThread clientThread, EventBus eventBus,
		ShortestPathService shortestPath)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.shortestPath = shortestPath;
	}

	public void start(Set<PatchType> types)
	{
		setSelectedTypes(types);
		clientThread.invokeLater(() -> startOnClientThread(types));
	}

	public void setSelectedTypes(Set<PatchType> types)
	{
		Set<PatchType> copy = types.isEmpty() ? EnumSet.noneOf(PatchType.class)
			: EnumSet.copyOf(types);
		selectedTypes = Collections.unmodifiableSet(copy);
	}

	public Set<PatchType> selectedTypes()
	{
		return selectedTypes;
	}

	public List<PatchDefinition> stops()
	{
		return route;
	}

	public int currentIndex()
	{
		return index;
	}

	public List<String> teleportsFor(int stopIndex)
	{
		List<String> lines = shortestPath.transportsFor(stopIndex);
		if (routeReady || lines.isEmpty())
		{
			return routeReady ? lines : Collections.emptyList();
		}
		// Before the bank closes, Shortest Path's result still contains the bank-visit hop.
		// Show the actual destination teleport as a hint; the full path is shown after the
		// inventory-only requery.
		for (int i = lines.size() - 1; i >= 0; i--)
		{
			String line = lines.get(i);
			String lower = line.toLowerCase(Locale.ROOT);
			if (!lower.contains("walk") && !lower.contains("soul wars")
				&& !lower.contains("grand exchange"))
			{
				return Collections.singletonList(line);
			}
		}
		return Collections.singletonList(lines.get(lines.size() - 1));
	}

	/** True when every remaining stop has a Shortest Path result for the current mode. */
	public boolean allResultsReady()
	{
		if (!active)
		{
			return false;
		}
		List<PatchDefinition> current = route;
		int currentIndex = index;
		for (int i = currentIndex; i < current.size(); i++)
		{
			if (!shortestPath.hasResult(i))
			{
				return false;
			}
		}
		return true;
	}

	public boolean hasShortestPathResult(int stopIndex)
	{
		return routeReady && shortestPath.hasResult(stopIndex);
	}

	public boolean reachedShortestPath(int stopIndex)
	{
		return routeReady && shortestPath.reached(stopIndex);
	}

	public boolean routeReady()
	{
		return routeReady;
	}

	public List<String> allTeleportLines()
	{
		List<String> all = new ArrayList<>();
		List<PatchDefinition> current = route;
		for (int i = index; i < current.size(); i++)
		{
			all.addAll(shortestPath.transportsFor(i));
		}
		return all;
	}

	public List<Integer> allRequiredItems()
	{
		LinkedHashSet<Integer> all = new LinkedHashSet<>();
		List<PatchDefinition> current = route;
		for (int i = index; i < current.size(); i++)
		{
			all.addAll(shortestPath.missingItemsFor(i));
		}
		return new ArrayList<>(all);
	}

	public void stop()
	{
		clientThread.invokeLater(() -> stopOnClientThread(true));
	}

	/** Rebuild the route queries when the player changes spellbook during a run. */
	public void refreshTeleportPreference()
	{
		requeryCurrentStop();
	}

	/** Recompute the current leg after the bank closes, using the supplies now carried. */
	public void refreshRouteForInventory()
	{
		if (!active)
		{
			return;
		}
		routeReady = true;
		requeryCurrentStop();
	}

	private void requeryCurrentStop()
	{
		if (!active)
		{
			return;
		}
		clientThread.invokeLater(() ->
		{
			if (!active || index >= route.size())
			{
				return;
			}
			shortestPath.applyTeleportMode(!routeReady);
			shortestPath.clearResults();
			if (routeReady)
			{
				sendCurrentTarget(false);
			}
			WorldPoint start;
			if (index == 0)
			{
				Player player = client.getLocalPlayer();
				start = player == null ? null : player.getWorldLocation();
			}
			else
			{
				start = point(route.get(index - 1));
			}
			if (start != null)
			{
				queryStops(index, start);
			}
		});
	}

	public boolean isActive()
	{
		return active;
	}

	/** Name of the next stop, or null when no run is active. */
	public String nextStopName()
	{
		List<PatchDefinition> current = route;
		int currentIndex = index;
		return active && currentIndex < current.size() ? current.get(currentIndex).getDisplayName() : null;
	}

	public int remainingStops()
	{
		List<PatchDefinition> current = route;
		return active ? Math.max(0, current.size() - index) : 0;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!active)
		{
			return;
		}
		List<PatchDefinition> current = route;
		int currentIndex = index;
		if (currentIndex >= current.size())
		{
			finish();
			return;
		}
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		WorldPoint location = player.getWorldLocation();
		if (location == null)
		{
			return;
		}
		PatchDefinition target = current.get(currentIndex);
		if (distance(location.getX(), location.getY(), target.getX(), target.getY()) <= ARRIVAL_RADIUS)
		{
			index = currentIndex + 1;
			if (index >= current.size())
			{
				finish();
			}
			else
			{
				sendCurrentTarget();
				queryStops(index, point(current.get(currentIndex)));
			}
		}
	}

	private void startOnClientThread(Set<PatchType> types)
	{
		stopOnClientThread(false);

		List<PatchDefinition> patches = new ArrayList<>();
		for (PatchType type : types)
		{
			patches.addAll(Patches.forType(type));
		}
		if (patches.isEmpty())
		{
			message("No patches selected.");
			return;
		}

		int startX = patches.get(0).getX();
		int startY = patches.get(0).getY();
		Player player = client.getLocalPlayer();
		if (player != null && player.getWorldLocation() != null)
		{
			WorldPoint location = player.getWorldLocation();
			startX = location.getX();
			startY = location.getY();
		}

		route = Collections.unmodifiableList(RoutePlanner.plan(patches, startX, startY));
		index = 0;
		active = true;
		routeReady = false;
		message("Route started: " + route.size() + " stops.");
		message("Withdraw the bank tab supplies, then close the bank.");

		int plane = player != null && player.getWorldLocation() != null
			? player.getWorldLocation().getPlane() : 0;
		WorldPoint legStart = new WorldPoint(startX, startY, plane);
		shortestPath.clearResults();
		shortestPath.applyTeleportMode(true);
		queryStops(0, legStart);
	}

	private void queryStops(int from, WorldPoint legStart)
	{
		List<PatchDefinition> current = route;
		for (int i = from; i < current.size(); i++)
		{
			WorldPoint target = point(current.get(i));
			shortestPath.query(i, legStart, target);
			legStart = target;
		}
	}

	private void stopOnClientThread(boolean announce)
	{
		boolean wasActive = active;
		active = false;
		routeReady = false;
		route = Collections.emptyList();
		index = 0;
		shortestPath.clearResults();
		clearPath();
		if (announce && wasActive)
		{
			message("Run stopped.");
		}
	}

	private void finish()
	{
		active = false;
		routeReady = false;
		route = Collections.emptyList();
		index = 0;
		shortestPath.clearResults();
		clearPath();
		message("Run complete.");
	}

	private static WorldPoint point(PatchDefinition patch)
	{
		return new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
	}

	private void sendCurrentTarget()
	{
		sendCurrentTarget(true);
	}

	private void sendCurrentTarget(boolean announce)
	{
		List<PatchDefinition> current = route;
		int currentIndex = index;
		if (currentIndex >= current.size())
		{
			return;
		}
		PatchDefinition target = current.get(currentIndex);
		WorldPoint point = new WorldPoint(target.getX(), target.getY(), target.getPlane());
		Map<String, Object> data = new HashMap<>();
		data.put("target", point);
		eventBus.post(new PluginMessage("shortestpath", "path", data));
		lastTarget = point;
		if (announce)
		{
			message("Next: " + target.getDisplayName() + " (" + (currentIndex + 1) + "/"
				+ current.size() + ")");
		}
	}

	private void clearPath()
	{
		if (lastTarget != null)
		{
			eventBus.post(new PluginMessage("shortestpath", "clear"));
			lastTarget = null;
		}
	}

	private void message(String text)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", PREFIX + text, null);
	}

	private static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}
}

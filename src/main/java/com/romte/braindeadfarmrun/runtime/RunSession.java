package com.romte.braindeadfarmrun.runtime;

import com.romte.braindeadfarmrun.bank.SupplyPlanner;
import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.planning.RoutePlanner;
import com.romte.braindeadfarmrun.planning.SeedSelector;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.game.ItemManager;

/**
 * The active farm run: selected patch types, visit order, and the next stop sent to Shortest
 * Path. The route advances automatically when the player reaches the current patch.
 */
@Slf4j
@Singleton
public class RunSession
{
	private static final int ARRIVAL_RADIUS = 10;
	private static final WorldPoint SEAWEED_ACCESS = new WorldPoint(3764, 3899, 0);
	private static final int SEAWEED_REGION = 15008;
	private static final String PREFIX = "<col=00bfff>Braindead Farm Run:</col> ";
	private static final String FALADOR_DIARY_TEXT = "The gardener protects your tree for you, "
		+ "free of charge, as a token of gratitude for completing the Falador elite diary.";
	private static final Pattern COMPOST_APPLIED = Pattern.compile(
		"You treat the .+ with (?:ultra|super)?compost\\.");
	private static final Pattern COMPOST_ALREADY_APPLIED = Pattern.compile(
		"This .+ has already been (?:treated|fertilised) with (?:ultra|super)?compost.*");
	private static final Pattern PAYMENT_MADE = Pattern.compile(
		"You pay the gardener .+ to protect the patch\\.");

	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ShortestPathService shortestPath;
	private final PatchTracker patchTracker;
	private final ItemManager itemManager;
	private final SeedSelector seedSelector;
	private final SupplyPlanner supplyPlanner;
	private volatile List<PatchDefinition> route = Collections.emptyList();
	private volatile Set<PatchType> selectedTypes = Collections.unmodifiableSet(
		EnumSet.allOf(PatchType.class));
	private volatile int index;
	private volatile boolean active;
	private volatile boolean preparing;
	private volatile boolean routeReady;
	private volatile boolean atStop;
	private volatile List<String> currentTasks = Collections.emptyList();
	private final Set<String> compostedPatches = new HashSet<>();
	private final Set<String> paidPatches = new HashSet<>();
	private StopGuide guide;
	private int completeTicks;
	private WorldPoint lastTarget;
	private String lastGuideKey;

	@Inject
	RunSession(Client client, ClientThread clientThread, EventBus eventBus,
		ShortestPathService shortestPath, PatchTracker patchTracker, ItemManager itemManager,
		SeedSelector seedSelector, SupplyPlanner supplyPlanner)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.shortestPath = shortestPath;
		this.patchTracker = patchTracker;
		this.itemManager = itemManager;
		this.seedSelector = seedSelector;
		this.supplyPlanner = supplyPlanner;
	}

	public void start(Set<PatchType> types)
	{
		preparing = true;
		setSelectedTypes(types);
		clientThread.invokeLater(() -> startOnClientThread(types));
	}

	public boolean isPreparing()
	{
		return preparing;
	}

	public void setPreparing(boolean preparing)
	{
		this.preparing = preparing;
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

	public boolean isAtStop()
	{
		return atStop;
	}

	/** Steps for the current patch, based on its tracked state. */
	public List<String> currentTasks()
	{
		return currentTasks;
	}

	private void beginStop()
	{
		if (!active || index >= route.size())
		{
			guide = null;
			currentTasks = Collections.emptyList();
			return;
		}
		PatchDefinition patch = route.get(index);
		PatchState state = patchTracker.snapshot().get(patch.getId());
		guide = new StopGuide(patch, state, seedSelector.cropFor(patch.getType()),
			supplyPlanner.compostItemName(), compostedPatches.contains(patch.getId()));
		if (paidPatches.contains(patch.getId()))
		{
			guide.onPayment();
		}
		lastGuideKey = null;
		updateCurrentTasks();
	}

	private void updateCurrentTasks()
	{
		if (!active || index >= route.size() || guide == null)
		{
			currentTasks = Collections.emptyList();
			return;
		}
		PatchDefinition patch = route.get(index);
		if (patch.getType() == PatchType.SEAWEED && !atStop)
		{
			currentTasks = Collections.unmodifiableList(seaweedApproachTasks());
			return;
		}
		PatchState state = patchTracker.snapshot().get(patch.getId());
		guide.update(state, patchTracker.isProtected(patch));
		if (guide.phase() == StopGuide.Phase.PLANT && guide.currentItemName() == null)
		{
			guide.setPlantItemName(findInventoryPlantItem(patch));
		}
		currentTasks = Collections.unmodifiableList(guide.stepLines());
		String key = guide.phase() + (guide.isFarmerTarget() ? "/farmer" : "/patch");
		if (!key.equals(lastGuideKey))
		{
			lastGuideKey = key;
			log.debug("Guide target for {}: {}", patch.getDisplayName(), key);
		}
	}

	private void advanceStop()
	{
		if (!active || index >= route.size())
		{
			return;
		}
		atStop = false;
		completeTicks = 0;
		index++;
		if (index >= route.size())
		{
			finish();
			return;
		}
		beginStop();
		sendCurrentTarget();
		queryStops(index, point(route.get(index - 1)));
	}

	public List<String> teleportsFor(int stopIndex)
	{
		List<String> lines = shortestPath.transportsFor(stopIndex);
		if (lines.isEmpty())
		{
			return Collections.emptyList();
		}
		if (!routeReady || stopIndex != index)
		{
			// Before the bank closes, and for legs that have not been requeried with the
			// final inventory yet, show the destination teleport rather than the bank-visit
			// hop from the supply-mode result.
			return Collections.singletonList(lastDestinationTransport(lines));
		}
		return lines;
	}

	private static String lastDestinationTransport(List<String> lines)
	{
		for (int i = lines.size() - 1; i >= 0; i--)
		{
			String line = lines.get(i);
			String lower = line.toLowerCase(Locale.ROOT);
			if (!lower.contains("walk") && !lower.contains("soul wars")
				&& !lower.contains("grand exchange"))
			{
				return line;
			}
		}
		return lines.get(lines.size() - 1);
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
			if (routeReady)
			{
				// Only the current leg needs the inventory-only recalculation; the remaining
				// stops keep their supply-mode hints until the player advances to them.
				shortestPath.clearResult(index);
				sendCurrentTarget(false);
			}
			else
			{
				shortestPath.clearResults();
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
				if (routeReady)
				{
					shortestPath.query(index, start, navigationPoint(route.get(index)));
				}
				else
				{
					queryStops(index, start);
				}
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

	/** Current patch, or null when the run has finished. */
	public PatchDefinition currentPatch()
	{
		List<PatchDefinition> current = route;
		int currentIndex = index;
		return active && currentIndex >= 0 && currentIndex < current.size()
			? current.get(currentIndex) : null;
	}

	/** True when the current checklist action is talking to the patch farmer. */
	public boolean currentTargetIsFarmer()
	{
		return !isSeaweedApproach() && active && atStop && guide != null
			&& guide.isFarmerTarget();
	}

	/** Item the current checklist action needs, or null. */
	public String currentItemName()
	{
		return !isSeaweedApproach() && active && guide != null
			? guide.currentItemName() : null;
	}

	/** True while a seaweed run has not reached the underwater area yet. */
	public boolean isSeaweedApproach()
	{
		PatchDefinition patch = currentPatch();
		return patch != null && patch.getType() == PatchType.SEAWEED && !isUnderwater();
	}

	/** World point the player should walk or swim towards for the current stop. */
	public WorldPoint currentNavigationPoint()
	{
		PatchDefinition patch = currentPatch();
		return patch == null ? null : navigationPoint(patch);
	}

	private List<String> seaweedApproachTasks()
	{
		List<String> tasks = new ArrayList<>();
		if (!isUnderwater())
		{
			Player player = client.getLocalPlayer();
			WorldPoint location = player == null ? null : player.getWorldLocation();
			if (location == null || distance(location.getX(), location.getY(),
				SEAWEED_ACCESS.getX(), SEAWEED_ACCESS.getY()) > ARRIVAL_RADIUS)
			{
				tasks.add("Travel to the Fossil Island bank island.");
			}
			tasks.add("Dive from the rowboat.");
		}
		tasks.add("Swim south to the seaweed patch.");
		return tasks;
	}

	private boolean isUnderwater()
	{
		Player player = client.getLocalPlayer();
		WorldPoint location = player == null ? null : player.getWorldLocation();
		return location != null
			&& (location.getRegionID() == SEAWEED_REGION || location.getY() >= 10_000);
	}

	private WorldPoint navigationPoint(PatchDefinition patch)
	{
		if (patch.getType() == PatchType.SEAWEED && !isUnderwater())
		{
			return SEAWEED_ACCESS;
		}
		return point(patch);
	}

	private String findInventoryPlantItem(PatchDefinition patch)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null || inventory.getItems() == null)
		{
			return null;
		}
		for (Item item : inventory.getItems())
		{
			if (item.getId() <= 0)
			{
				continue;
			}
			int canonical = itemManager.canonicalize(item.getId());
			String name = itemManager.getItemComposition(canonical).getName();
			for (Crop crop : Crop.values())
			{
				if (crop.getType() == patch.getType()
					&& crop.getPlantedItemName().equalsIgnoreCase(name))
				{
					return name;
				}
			}
		}
		return null;
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
		if (!atStop
			&& distance(location.getX(), location.getY(), target.getX(), target.getY()) <= ARRIVAL_RADIUS)
		{
			atStop = true;
			patchTracker.refresh(target);
			beginStop();
			message("At " + target.getDisplayName()
				+ ". The next stop opens automatically when the steps are done.");
		}
		updateCurrentTasks();
		if (target.getType() == PatchType.SEAWEED && isUnderwater())
		{
			// Shortest Path cannot enter the underwater area; the world/minimap arrow takes over.
			clearPath();
		}
		if (atStop && guide != null && guide.isComplete())
		{
			if (++completeTicks >= 3)
			{
				advanceStop();
			}
		}
		else
		{
			completeTicks = 0;
		}
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		String message = event.getMessage();
		if (event.getType() == ChatMessageType.GAMEMESSAGE && active && guide != null
			&& FALADOR_DIARY_TEXT.equals(message))
		{
			guide.onPayment();
			patchTracker.markPaid(route.get(index));
			updateCurrentTasks();
			return;
		}
		if ((event.getType() == ChatMessageType.GAMEMESSAGE
			|| event.getType() == ChatMessageType.SPAM)
			&& active && guide != null)
		{
			if (PAYMENT_MADE.matcher(message).matches())
			{
				PatchDefinition patch = currentPatch();
				if (patch != null)
				{
					paidPatches.add(patch.getId());
				}
				guide.onPayment();
				patchTracker.markPaid(route.get(index));
				updateCurrentTasks();
			}
			else if (guide.phase() == StopGuide.Phase.COMPOST
				&& (COMPOST_APPLIED.matcher(message).matches()
					|| COMPOST_ALREADY_APPLIED.matcher(message).matches()))
			{
				PatchDefinition patch = currentPatch();
				if (patch != null)
				{
					compostedPatches.add(patch.getId());
				}
				guide.onCompost();
				updateCurrentTasks();
			}
		}
	}

	private void startOnClientThread(Set<PatchType> types)
	{
		stopOnClientThread(false);
		preparing = true;

		List<PatchDefinition> patches = new ArrayList<>();
		for (PatchType type : types)
		{
			patches.addAll(Patches.forType(type));
		}
		if (patches.isEmpty())
		{
			preparing = false;
			message("No patches selected.");
			return;
		}

		// Patches that are already growing have nothing to do on this run.
		Map<String, PatchState> states = patchTracker.snapshot();
		List<PatchDefinition> pending = new ArrayList<>();
		for (PatchDefinition patch : patches)
		{
			PatchState state = states.get(patch.getId());
			if (state != null && state.statusAt(Instant.now()) == PatchState.Status.GROWING)
			{
				continue;
			}
			pending.add(patch);
		}
		patches = pending;
		if (patches.isEmpty())
		{
			preparing = false;
			message("No patches need attention.");
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
		atStop = false;
		routeReady = false;
		beginStop();
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
			WorldPoint target = navigationPoint(current.get(i));
			shortestPath.query(i, legStart, target);
			legStart = target;
		}
	}

	private void stopOnClientThread(boolean announce)
	{
		boolean wasActive = active;
		active = false;
		preparing = false;
		routeReady = false;
		atStop = false;
		guide = null;
		completeTicks = 0;
		lastGuideKey = null;
		currentTasks = Collections.emptyList();
		route = Collections.emptyList();
		index = 0;
		compostedPatches.clear();
		paidPatches.clear();
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
		preparing = false;
		routeReady = false;
		atStop = false;
		guide = null;
		completeTicks = 0;
		lastGuideKey = null;
		currentTasks = Collections.emptyList();
		route = Collections.emptyList();
		index = 0;
		compostedPatches.clear();
		paidPatches.clear();
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
		WorldPoint point = navigationPoint(target);
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

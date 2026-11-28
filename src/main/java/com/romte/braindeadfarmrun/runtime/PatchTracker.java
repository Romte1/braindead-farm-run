package com.romte.braindeadfarmrun.runtime;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.romte.braindeadfarmrun.BraindeadFarmRunConfig;
import com.romte.braindeadfarmrun.data.PatchLocator;
import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.domain.patch.PatchStateMapper;
import com.romte.braindeadfarmrun.domain.patch.PatchStates;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import java.awt.Rectangle;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;

/**
 * Tracks farming patches from the objects that spawn in the scene and their varbits.
 *
 * <p>All client state is touched on the client thread. The panel reads a concurrent snapshot.</p>
 */
@Slf4j
@Singleton
public class PatchTracker
{
	private static final String STATE_KEY = "patchStates";
	private static final String TIME_TRACKING_GROUP = "timetracking";
	private static final Type STORED_TYPE = new TypeToken<Map<String, StoredPatch>>()
	{
	}.getType();

	private final Client client;
	private final ClientThread clientThread;
	private final ConfigManager configManager;
	private final Gson gson;
	private final BraindeadFarmRunConfig config;
	private final Map<String, PatchState> states = new ConcurrentHashMap<>();
	private final Map<String, Long> observedAt = new ConcurrentHashMap<>();
	private final Map<GameObject, TrackedObject> tracked = new HashMap<>();
	private final Map<String, Rectangle> patchAreas = new HashMap<>();
	private final Set<String> patchAreaMisses = new HashSet<>();
	private final Set<NPC> farmers = new HashSet<>();
	private final Set<NPC> leprechauns = new HashSet<>();
	private final Set<Integer> farmingVarbits = new HashSet<>();
	private volatile boolean dirty;

	@Inject
	PatchTracker(Client client, ClientThread clientThread, ConfigManager configManager, Gson gson,
		BraindeadFarmRunConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.configManager = configManager;
		this.gson = gson;
		this.config = config;
		for (PatchDefinition patch : Patches.all())
		{
			farmingVarbits.add(patch.getVarbit());
		}
	}

	public void startUp()
	{
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				load();
				importTimeTracking();
				trackSceneNpcs();
			}
		});
	}

	public void shutDown()
	{
		save();
		states.clear();
		observedAt.clear();
		tracked.clear();
		patchAreas.clear();
		patchAreaMisses.clear();
		farmers.clear();
		leprechauns.clear();
		dirty = false;
	}

	/** Immutable copy of the current patch states, safe to read from the Swing thread. */
	public Map<String, PatchState> snapshot()
	{
		return Collections.unmodifiableMap(new HashMap<>(states));
	}

	/** Time Tracking stores whether the farmer is protecting this patch. */
	public boolean isProtected(PatchDefinition patch)
	{
		for (int region : patch.getRegions())
		{
			String key = region + "." + patch.getVarbit() + ".protected";
			if (Boolean.TRUE.equals(configManager.getRSProfileConfiguration("timetracking", key,
				Boolean.class)))
			{
				return true;
			}
		}
		return false;
	}

	/** Records that the player paid the farmer for this patch. */
	public void markPaid(PatchDefinition patch)
	{
		if (patch == null)
		{
			return;
		}
		PatchState state = states.get(patch.getId());
		if (state != null)
		{
			states.put(patch.getId(), state.withPaid(true));
			dirty = true;
		}
	}

	/** Reads the live varbit for a patch, refreshing state that may have loaded before the scene. */
	public void refresh(PatchDefinition patch)
	{
		if (patch == null)
		{
			return;
		}
		int value = client.getVarbitValue(patch.getVarbit());
		PatchState previous = states.get(patch.getId());
		Instant now = Instant.now();
		PatchState next = PatchStateMapper.apply(patch.getId(),
			PatchStates.get().decode(patch.getType(), value), previous, now);
		if (!next.equals(previous))
		{
			states.put(patch.getId(), next);
			observedAt.put(patch.getId(), now.toEpochMilli());
			dirty = true;
		}
	}

	/**
	 * Bounds of the soil patch near the patch coordinate. The farming varbit object is often
	 * only a single crop model, so this scans the neighbouring tiles for the static patch
	 * objects (including ground decorations) and returns their combined footprint.
	 */
	public Rectangle patchAreaFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		Rectangle cached = patchAreas.get(patch.getId());
		if (cached != null)
		{
			return cached;
		}
		if (patchAreaMisses.contains(patch.getId()))
		{
			return null;
		}
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getScene() == null)
		{
			return null;
		}
		int plane = patch.getPlane();
		Tile[][][] tiles = worldView.getScene().getTiles();
		if (plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		int baseX = worldView.getBaseX();
		int baseY = worldView.getBaseY();
		int centerX = patch.getX() - baseX;
		int centerY = patch.getY() - baseY;
		List<Rectangle> candidates = new ArrayList<>();
		for (int x = centerX - 8; x <= centerX + 8; x++)
		{
			for (int y = centerY - 8; y <= centerY + 8; y++)
			{
				if (x < 0 || y < 0 || x >= Constants.SCENE_SIZE || y >= Constants.SCENE_SIZE
					|| tiles[plane][x][y] == null)
				{
					continue;
				}
				Tile tile = tiles[plane][x][y];
				GameObject[] objects = tile.getGameObjects();
				if (objects != null)
				{
					for (GameObject object : objects)
					{
						addAreaCandidate(candidates, patch, object, baseX, baseY);
					}
				}
				addAreaCandidate(candidates, patch, tile.getGroundObject(), baseX, baseY);
				addAreaCandidate(candidates, patch, tile.getDecorativeObject(), baseX, baseY);
				addAreaCandidate(candidates, patch, tile.getWallObject(), baseX, baseY);
			}
		}
		if (candidates.isEmpty())
		{
			patchAreaMisses.add(patch.getId());
			return null;
		}
		Rectangle seed = null;
		int seedDistance = Integer.MAX_VALUE;
		for (Rectangle candidate : candidates)
		{
			int distance = rectangleDistance(candidate, patch.getX(), patch.getY());
			int area = candidate.width * candidate.height;
			if (seed == null || distance < seedDistance
				|| (distance == seedDistance && area > seed.width * seed.height))
			{
				seed = candidate;
				seedDistance = distance;
			}
		}
		Rectangle area = new Rectangle(seed);
		boolean changed;
		do
		{
			changed = false;
			for (Rectangle candidate : candidates)
			{
				if (!area.contains(candidate) && rectangleGap(area, candidate) <= 1)
				{
					area.add(candidate);
					changed = true;
				}
			}
		}
		while (changed);
		patchAreas.put(patch.getId(), area);
		log.debug("Patch area for {}: {} (seed={} candidates={})",
			patch.getId(), area, seed, candidates.size());
		return area;
	}

	private void addAreaCandidate(List<Rectangle> candidates, PatchDefinition patch,
		TileObject object, int baseX, int baseY)
	{
		if (object == null)
		{
			return;
		}
		ObjectComposition definition = client.getObjectDefinition(object.getId());
		if (definition == null || definition.getName() == null
			|| !isPatchArea(patch.getType(), definition.getName()))
		{
			return;
		}
		int minX;
		int minY;
		int maxX;
		int maxY;
		if (object instanceof GameObject)
		{
			GameObject gameObject = (GameObject) object;
			Point min = gameObject.getSceneMinLocation();
			Point max = gameObject.getSceneMaxLocation();
			minX = baseX + min.getX();
			minY = baseY + min.getY();
			maxX = baseX + max.getX();
			maxY = baseY + max.getY();
		}
		else
		{
			WorldPoint location = object.getWorldLocation();
			minX = location.getX();
			minY = location.getY();
			maxX = minX;
			maxY = minY;
		}
		candidates.add(new Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1));
	}

	private static int rectangleDistance(Rectangle rectangle, int x, int y)
	{
		int dx = x < rectangle.x ? rectangle.x - x
			: x >= rectangle.x + rectangle.width ? x - (rectangle.x + rectangle.width - 1) : 0;
		int dy = y < rectangle.y ? rectangle.y - y
			: y >= rectangle.y + rectangle.height ? y - (rectangle.y + rectangle.height - 1) : 0;
		return Math.max(dx, dy);
	}

	private static int rectangleGap(Rectangle a, Rectangle b)
	{
		int dx = a.x + a.width <= b.x ? b.x - (a.x + a.width)
			: b.x + b.width <= a.x ? a.x - (b.x + b.width) : 0;
		int dy = a.y + a.height <= b.y ? b.y - (a.y + a.height)
			: b.y + b.height <= a.y ? a.y - (b.y + b.height) : 0;
		return Math.max(dx, dy);
	}

	private static boolean isPatchArea(PatchType type, String name)
	{
		String lower = name.toLowerCase(Locale.ROOT);
		switch (type)
		{
			case ALLOTMENT:
				return lower.contains("allotment");
			case HERB:
				return lower.contains("herb") && lower.contains("patch");
			case TREE:
				return lower.contains("tree patch") && !lower.contains("fruit")
					&& !lower.contains("hardwood") && !lower.contains("calquat");
			case FRUIT_TREE:
				return lower.contains("fruit tree");
			case CALQUAT:
				return lower.contains("calquat");
			case HARDWOOD_TREE:
				return lower.contains("hardwood");
			case SEAWEED:
				return lower.contains("seaweed");
			default:
				return false;
		}
	}

	/**
	 * A walkable tile beside the patch that is closest to the tool leprechaun. Allotment,
	 * flower and herb produce is noted at the leprechaun, so this avoids sending the player to
	 * the far side of a large patch. Client thread only.
	 */
	public WorldPoint targetTileFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		NPC leprechaun = toolLeprechaunFor(patch);
		WorldPoint leprechaunPoint = leprechaun == null ? null : leprechaun.getWorldLocation();
		int compareX = leprechaunPoint == null ? patch.getX() : leprechaunPoint.getX();
		int compareY = leprechaunPoint == null ? patch.getY() : leprechaunPoint.getY();
		Rectangle area = patchAreaFor(patch);
		WorldPoint best = null;
		if (area != null)
		{
			int bestDistance = Integer.MAX_VALUE;
			for (int x = area.x; x < area.x + area.width; x++)
			{
				for (int y = area.y; y < area.y + area.height; y++)
				{
					int distance = distance(x, y, compareX, compareY);
					if (distance < bestDistance)
					{
						bestDistance = distance;
						best = new WorldPoint(x, y, patch.getPlane());
					}
				}
			}
		}
		if (best == null)
		{
			best = new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
		}
		log.debug("Target tile for {}: {} area={} leprechaun={} at {}",
			patch.getId(), best, area, leprechaun == null ? "none" : leprechaun.getName(),
			leprechaunPoint);
		return best;
	}

	/** Nearest tool leprechaun loaded for a patch. Client thread only. */
	public NPC toolLeprechaunFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		NPC best = nearestLeprechaun(leprechauns, patch);
		if (best != null)
		{
			return best;
		}
		WorldView worldView = client.getTopLevelWorldView();
		return worldView == null || worldView.npcs() == null
			? null : nearestLeprechaun(worldView.npcs(), patch);
	}

	private static NPC nearestLeprechaun(Iterable<? extends NPC> candidates, PatchDefinition patch)
	{
		NPC best = null;
		int bestDistance = 24;
		for (NPC npc : candidates)
		{
			if (!isToolLeprechaun(npc))
			{
				continue;
			}
			WorldPoint location = npc.getWorldLocation();
			if (location == null || location.getPlane() != patch.getPlane())
			{
				continue;
			}
			int distance = distance(location.getX(), location.getY(), patch.getX(), patch.getY());
			if (distance < bestDistance)
			{
				best = npc;
				bestDistance = distance;
			}
		}
		return best;
	}

	/** Nearest loaded farmer or gardener for a patch. Client thread only. */
	public NPC farmerFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		NPC best = nearestFarmer(farmers, patch);
		if (best != null)
		{
			return best;
		}

		// The NPC may have loaded before this plugin subscribed, or the spawn event may have
		// been missed during a region change. Fall back to the current world view once.
		WorldView worldView = client.getTopLevelWorldView();
		return worldView == null || worldView.npcs() == null
			? null : nearestFarmer(worldView.npcs(), patch);
	}

	private static NPC nearestFarmer(Iterable<? extends NPC> candidates, PatchDefinition patch)
	{
		NPC best = null;
		int bestDistance = 21;
		for (NPC npc : candidates)
		{
			if (!isFarmer(npc))
			{
				continue;
			}
			WorldPoint location = npc.getWorldLocation();
			if (location == null || location.getPlane() != patch.getPlane())
			{
				continue;
			}
			int distance = distance(location.getX(), location.getY(), patch.getX(), patch.getY());
			if (distance < bestDistance)
			{
				best = npc;
				bestDistance = distance;
			}
		}
		return best;
	}

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		trackNpc(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		farmers.remove(event.getNpc());
		leprechauns.remove(event.getNpc());
	}

	private void trackSceneNpcs()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.npcs() != null)
		{
			for (NPC npc : worldView.npcs())
			{
				trackNpc(npc);
			}
		}
	}

	private void trackNpc(NPC npc)
	{
		if (isFarmer(npc))
		{
			farmers.add(npc);
		}
		if (isToolLeprechaun(npc))
		{
			leprechauns.add(npc);
		}
	}

	private static boolean isToolLeprechaun(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}
		String name = npc.getName();
		if (name == null)
		{
			NPCComposition composition = npc.getComposition();
			name = composition == null ? null : composition.getName();
		}
		return name != null && name.toLowerCase(Locale.ROOT).contains("tool leprechaun");
	}

	private static boolean isFarmer(NPC npc)
	{
		if (npc == null)
		{
			return false;
		}
		String name = npc.getName();
		if (name != null)
		{
			String lower = name.toLowerCase(Locale.ROOT);
			if (lower.contains("farmer") || lower.contains("gardener"))
			{
				return true;
			}
		}

		// Some patch gardeners have unique names (for example Heskel). The reliable common
		// property is the "Pay" action used to protect or clear a patch.
		NPCComposition composition = npc.getComposition();
		if (composition == null || composition.getActions() == null)
		{
			return false;
		}
		for (String action : composition.getActions())
		{
			if (action != null && action.startsWith("Pay"))
			{
				return true;
			}
		}
		return false;
	}

	private static int distance(int x1, int y1, int x2, int y2)
	{
		return Math.max(Math.abs(x1 - x2), Math.abs(y1 - y2));
	}

	@Subscribe
	public void onGameObjectSpawned(GameObjectSpawned event)
	{
		GameObject object = event.getGameObject();
		ObjectComposition definition = client.getObjectDefinition(object.getId());
		if (definition == null)
		{
			return;
		}
		int varbit = definition.getVarbitId();
		if (varbit <= 0 || !farmingVarbits.contains(varbit))
		{
			return;
		}
		WorldPoint location = object.getWorldLocation();
		if (location == null)
		{
			return;
		}
		PatchDefinition patch = PatchLocator.find(location.getRegionID(), varbit,
			location.getX(), location.getY());
		if (patch == null)
		{
			return;
		}
		tracked.put(object, new TrackedObject(patch, varbit));
		update(patch, client.getVarbitValue(varbit), Instant.now());
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		tracked.remove(event.getGameObject());
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		int varbit = event.getVarbitId();
		if (!farmingVarbits.contains(varbit))
		{
			return;
		}
		int value = client.getVarbitValue(varbit);
		Instant now = Instant.now();
		for (TrackedObject object : tracked.values())
		{
			if (object.varbit == varbit)
			{
				update(object.patch, value, now);
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		switch (event.getGameState())
		{
			case LOGGED_IN:
				load();
				importTimeTracking();
				trackSceneNpcs();
				break;
			case LOGIN_SCREEN:
				save();
				tracked.clear();
				patchAreas.clear();
				patchAreaMisses.clear();
				farmers.clear();
				leprechauns.clear();
				states.clear();
				observedAt.clear();
				break;
			case LOADING:
				tracked.clear();
				patchAreas.clear();
				patchAreaMisses.clear();
				farmers.clear();
				leprechauns.clear();
				break;
			default:
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (dirty)
		{
			dirty = false;
			save();
		}
	}

	private void update(PatchDefinition patch, int value, Instant now)
	{
		PatchState previous = states.get(patch.getId());
		PatchState next = PatchStateMapper.apply(patch.getId(),
			PatchStates.get().decode(patch.getType(), value), previous, now);
		states.put(patch.getId(), next);
		observedAt.put(patch.getId(), now.toEpochMilli());
		dirty = true;
	}

	private void load()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		String json = configManager.getRSProfileConfiguration(BraindeadFarmRunConfig.GROUP, STATE_KEY);
		if (json == null || json.isEmpty())
		{
			return;
		}
		Map<String, StoredPatch> stored = gson.fromJson(json, STORED_TYPE);
		if (stored == null)
		{
			return;
		}
		states.clear();
		observedAt.clear();
		for (Map.Entry<String, StoredPatch> entry : stored.entrySet())
		{
			StoredPatch value = entry.getValue();
			PatchState state = value.toState(entry.getKey());
			if (state != null)
			{
				states.put(entry.getKey(), state);
				if (value.observedAt > 0)
				{
					observedAt.put(entry.getKey(), value.observedAt);
				}
			}
		}
	}

	/**
	 * Reads the timers RuneLite's Time Tracking plugin already saved. Each patch is stored as
	 * {@code regionId.varbitId = "value:unixSeconds"}; the timestamp is when the varbit changed,
	 * which lets us estimate the planting time without visiting the patch.
	 */
	private void importTimeTracking()
	{
		if (!config.importTimeTracking() || configManager.getRSProfileKey() == null)
		{
			return;
		}
		boolean changed = false;
		for (PatchDefinition patch : Patches.all())
		{
			String raw = configManager.getRSProfileConfiguration(TIME_TRACKING_GROUP,
				patch.getRegionId() + "." + patch.getVarbit());
			if (raw == null)
			{
				continue;
			}
			String[] parts = raw.split(":");
			if (parts.length < 2)
			{
				continue;
			}
			int value;
			long changedAtSeconds;
			try
			{
				value = Integer.parseInt(parts[0]);
				changedAtSeconds = Long.parseLong(parts[1]);
			}
			catch (NumberFormatException e)
			{
				continue;
			}
			long changedAtMillis = changedAtSeconds * 1000L;
			Instant changedAt = Instant.ofEpochMilli(changedAtMillis);
			long lastObserved = observedAt.getOrDefault(patch.getId(), 0L);
			if (lastObserved >= changedAtMillis - 60_000L)
			{
				continue; // our live data is newer
			}
			PatchState previous = states.get(patch.getId());
			PatchStates.Decoded decoded = PatchStates.get().decode(patch.getType(), value);
			if (decoded.getState() == PatchStates.State.UNKNOWN)
			{
				continue;
			}
			if (decoded.getState() == PatchStates.State.EMPTY)
			{
				// Value 0 for a tree patch is already the first weedy stage. Record it even when
				// there is no earlier crop, otherwise a patch that was never visited by this
				// plugin loses its weeds and the bank tab omits the rake.
				states.put(patch.getId(), PatchState.empty(patch.getId())
					.withWeeds(decoded.getStage() > 0));
			}
			else
			{
				states.put(patch.getId(),
					PatchStateMapper.applyAt(patch.getId(), decoded, previous, changedAt));
			}
			observedAt.put(patch.getId(), changedAtMillis);
			changed = true;
		}
		if (changed)
		{
			save();
		}
	}

	private void save()
	{
		if (configManager.getRSProfileKey() == null)
		{
			return;
		}
		Map<String, StoredPatch> stored = new HashMap<>();
		for (Map.Entry<String, PatchState> entry : states.entrySet())
		{
			stored.put(entry.getKey(), StoredPatch.from(entry.getValue(),
				observedAt.getOrDefault(entry.getKey(), 0L)));
		}
		configManager.setRSProfileConfiguration(BraindeadFarmRunConfig.GROUP, STATE_KEY,
			gson.toJson(stored));
	}

	private static final class TrackedObject
	{
		private final PatchDefinition patch;
		private final int varbit;

		private TrackedObject(PatchDefinition patch, int varbit)
		{
			this.patch = patch;
			this.varbit = varbit;
		}
	}

	private static final class StoredPatch
	{
		private String crop;
		private long plantedAt;
		private boolean paid;
		private boolean diseased;
		private boolean dead;
		private boolean stump;
		private boolean checkHealth;
		private boolean fell;
		private boolean weedy;
		private long observedAt;

		private static StoredPatch from(PatchState state, long observedAt)
		{
			StoredPatch stored = new StoredPatch();
			stored.crop = state.getCrop() == null ? null : state.getCrop().name();
			stored.plantedAt = state.getPlantedAt() == null ? 0 : state.getPlantedAt().toEpochMilli();
			stored.paid = state.isPaid();
			stored.diseased = state.isDiseased();
			stored.dead = state.isDead();
			stored.stump = state.isStump();
			stored.checkHealth = state.isCheckHealth();
			stored.fell = state.isFell();
			stored.weedy = state.isWeedy();
			stored.observedAt = observedAt;
			return stored;
		}

		private PatchState toState(String patchId)
		{
			if (crop == null || plantedAt <= 0)
			{
				return PatchState.empty(patchId).withWeeds(weedy);
			}
			Crop value;
			try
			{
				value = Crop.valueOf(crop);
			}
			catch (IllegalArgumentException e)
			{
				return PatchState.empty(patchId);
			}
			return PatchState.planted(patchId, value, Instant.ofEpochMilli(plantedAt), paid)
				.withDiseased(diseased)
				.withDead(dead)
				.withStump(stump)
				.withCheckHealth(checkHealth)
				.withFell(fell)
				.withWeeds(weedy);
		}
	}
}

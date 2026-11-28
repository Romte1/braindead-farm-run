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
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.CollisionData;
import net.runelite.api.CollisionDataFlag;
import net.runelite.api.Constants;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Point;
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
		states.put(patch.getId(), next);
		observedAt.put(patch.getId(), now.toEpochMilli());
		dirty = true;
	}

	/**
	 * Loaded object that best represents the patch. A patch can have several objects sharing
	 * its varbit (the soil, and one or more crop models). Prefer the largest object containing
	 * the patch point so the overlay outlines the patch rather than a single plant.
	 */
	public GameObject objectFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		WorldView worldView = client.getTopLevelWorldView();
		GameObject best = null;
		boolean bestContains = false;
		int bestArea = -1;
		int bestDistance = Integer.MAX_VALUE;
		for (Map.Entry<GameObject, TrackedObject> entry : tracked.entrySet())
		{
			TrackedObject value = entry.getValue();
			if (!value.patch.getId().equals(patch.getId()))
			{
				continue;
			}
			GameObject object = entry.getKey();
			WorldPoint location = object.getWorldLocation();
			if (location == null || location.getPlane() != patch.getPlane())
			{
				continue;
			}
			Point min = object.getSceneMinLocation();
			Point max = object.getSceneMaxLocation();
			int minX = worldView == null ? location.getX() : worldView.getBaseX() + min.getX();
			int minY = worldView == null ? location.getY() : worldView.getBaseY() + min.getY();
			int maxX = worldView == null ? location.getX() + object.sizeX() - 1
				: worldView.getBaseX() + max.getX();
			int maxY = worldView == null ? location.getY() + object.sizeY() - 1
				: worldView.getBaseY() + max.getY();
			boolean contains = patch.getX() >= minX && patch.getX() <= maxX
				&& patch.getY() >= minY && patch.getY() <= maxY;
			int area = (maxX - minX + 1) * (maxY - minY + 1);
			int distance = distance(location.getX(), location.getY(), patch.getX(), patch.getY());
			if ((contains && !bestContains)
				|| (contains == bestContains && area > bestArea)
				|| (contains == bestContains && area == bestArea && distance < bestDistance))
			{
				best = object;
				bestContains = contains;
				bestArea = area;
				bestDistance = distance;
			}
		}
		return best;
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
		GameObject object = objectFor(patch);
		if (object == null)
		{
			return new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
		}
		WorldPoint fallback = object.getWorldLocation();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getScene() == null)
		{
			return fallback;
		}
		NPC leprechaun = toolLeprechaunFor(patch);
		WorldPoint leprechaunPoint = leprechaun == null ? null : leprechaun.getWorldLocation();

		Point min = object.getSceneMinLocation();
		Point max = object.getSceneMaxLocation();
		int minX = min.getX();
		int minY = min.getY();
		int maxX = max.getX();
		int maxY = max.getY();
		int plane = patch.getPlane();
		CollisionData[] collisionMaps = worldView.getCollisionMaps();
		int[][] flags = collisionMaps != null && plane >= 0 && plane < collisionMaps.length
			&& collisionMaps[plane] != null ? collisionMaps[plane].getFlags() : null;
		int sceneSize = Constants.SCENE_SIZE;
		int compareX = leprechaunPoint == null ? patch.getX() : leprechaunPoint.getX();
		int compareY = leprechaunPoint == null ? patch.getY() : leprechaunPoint.getY();
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		boolean useObjectRing = object.sizeX() > 1 || object.sizeY() > 1;
		if (!useObjectRing)
		{
			// Many varbit objects are a single crop model. Search around the patch
			// coordinate instead of orbiting that one plant.
			int radius = patch.getType() == PatchType.ALLOTMENT ? 4 : 3;
			for (int dx = -radius; dx <= radius; dx++)
			{
				for (int dy = -radius; dy <= radius; dy++)
				{
					int x = patch.getX() - worldView.getBaseX() + dx;
					int y = patch.getY() - worldView.getBaseY() + dy;
					if (dx == 0 && dy == 0 || x < 0 || y < 0 || x >= sceneSize || y >= sceneSize)
					{
						continue;
					}
					if (flags != null && (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
					{
						continue;
					}
					WorldPoint candidate = new WorldPoint(worldView.getBaseX() + x,
						worldView.getBaseY() + y, plane);
					if (candidate.equals(leprechaunPoint))
					{
						continue;
					}
					int distance = distance(candidate.getX(), candidate.getY(), compareX, compareY);
					if (distance < bestDistance)
					{
						bestDistance = distance;
						best = candidate;
					}
				}
			}
		}
		else
		{
			for (int x = minX - 1; x <= maxX + 1; x++)
			{
				for (int y = minY - 1; y <= maxY + 1; y++)
				{
					if (x >= minX && x <= maxX && y >= minY && y <= maxY)
					{
						continue; // the patch itself is not a standing tile
					}
					if (x < 0 || y < 0 || x >= sceneSize || y >= sceneSize)
					{
						continue;
					}
					if (flags != null && (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) != 0)
					{
						continue;
					}
					WorldPoint candidate = new WorldPoint(worldView.getBaseX() + x,
						worldView.getBaseY() + y, plane);
					if (candidate.equals(leprechaunPoint))
					{
						continue;
					}
					int distance = distance(candidate.getX(), candidate.getY(), compareX, compareY);
					if (distance < bestDistance)
					{
						bestDistance = distance;
						best = candidate;
					}
				}
			}
		}
		WorldPoint result = best == null ? fallback : best;
		log.debug("Target tile for {}: {} object={} {}x{} at {} leprechaun={} at {}",
			patch.getId(), result, object.getId(), object.sizeX(), object.sizeY(),
			object.getWorldLocation(), leprechaun == null ? "none" : leprechaun.getName(),
			leprechaunPoint);
		return result;
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
				farmers.clear();
				leprechauns.clear();
				states.clear();
				observedAt.clear();
				break;
			case LOADING:
				tracked.clear();
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

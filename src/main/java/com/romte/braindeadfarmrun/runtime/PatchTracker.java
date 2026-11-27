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
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.ObjectComposition;
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
	private final Map<GameObject, TrackedObject> tracked = new HashMap<>();
	private final Set<NPC> farmers = new HashSet<>();
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
		tracked.clear();
		farmers.clear();
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
		String key = patch.getRegionId() + "." + patch.getVarbit() + ".protected";
		return Boolean.TRUE.equals(configManager.getRSProfileConfiguration("timetracking", key,
			Boolean.class));
	}

	/** Nearest loaded object for a patch. Client thread only. */
	public GameObject objectFor(PatchDefinition patch)
	{
		if (patch == null)
		{
			return null;
		}
		GameObject best = null;
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
			int distance = distance(location.getX(), location.getY(), patch.getX(), patch.getY());
			if (distance < bestDistance)
			{
				best = object;
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
		NPC best = null;
		int bestDistance = 13;
		for (NPC npc : farmers)
		{
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
		trackFarmer(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		farmers.remove(event.getNpc());
	}

	private void trackSceneNpcs()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView != null && worldView.npcs() != null)
		{
			for (NPC npc : worldView.npcs())
			{
				trackFarmer(npc);
			}
		}
	}

	private void trackFarmer(NPC npc)
	{
		if (npc == null)
		{
			return;
		}
		String name = npc.getName();
		if (name == null)
		{
			return;
		}
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("farmer") || lower.contains("gardener"))
		{
			farmers.add(npc);
		}
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
				states.clear();
				break;
			case LOADING:
				tracked.clear();
				farmers.clear();
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
		for (Map.Entry<String, StoredPatch> entry : stored.entrySet())
		{
			PatchState state = entry.getValue().toState(entry.getKey());
			if (state != null)
			{
				states.put(entry.getKey(), state);
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
			Instant changedAt = Instant.ofEpochSecond(changedAtSeconds);
			PatchState previous = states.get(patch.getId());
			if (previous != null && previous.getPlantedAt() != null
				&& previous.getPlantedAt().toEpochMilli() >= changedAt.toEpochMilli() - 60_000L)
			{
				continue; // our live data is newer
			}
			PatchStates.Decoded decoded = PatchStates.get().decode(patch.getType(), value);
			if (decoded.getState() == PatchStates.State.UNKNOWN)
			{
				continue;
			}
			if (decoded.getState() == PatchStates.State.EMPTY)
			{
				boolean newer = previous == null || previous.getPlantedAt() == null
					|| previous.getPlantedAt().toEpochMilli() < changedAt.toEpochMilli() - 60_000L;
				if (newer && previous != null && previous.getCrop() != null)
				{
					states.put(patch.getId(), PatchState.empty(patch.getId()));
					changed = true;
				}
				continue;
			}
			states.put(patch.getId(),
				PatchStateMapper.applyAt(patch.getId(), decoded, previous, changedAt));
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
			stored.put(entry.getKey(), StoredPatch.from(entry.getValue()));
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

		private static StoredPatch from(PatchState state)
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
			return stored;
		}

		private PatchState toState(String patchId)
		{
			if (crop == null || plantedAt <= 0)
			{
				return PatchState.empty(patchId);
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
				.withFell(fell);
		}
	}
}

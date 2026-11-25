package com.romte.farmrunplanner.domain.patch;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns a farming patch varbit value into the crop and growth state it represents.
 *
 * <p>The table is generated from RuneLite's Time Tracking plugin
 * ({@code PatchImplementation.java}, Copyright (c) 2019 Abex, BSD 2-Clause) and only contains
 * the patch types supported by this plugin.</p>
 */
public final class PatchStates
{
	public enum State
	{
		/** Weeds or bare soil. */
		EMPTY,
		GROWING,
		/** Ready to harvest, or a grown tree waiting for check-health or chopping. */
		READY,
		/** A tree that has been chopped; a stump is left. */
		STUMP,
		DISEASED,
		DEAD,
		UNKNOWN
	}

	public static final class Decoded
	{
		private final State state;
		private final Crop crop;
		private final int stage;
		private final boolean fell;
		private final boolean checkHealth;

		private Decoded(State state, Crop crop, int stage, boolean fell, boolean checkHealth)
		{
			this.state = state;
			this.crop = crop;
			this.stage = stage;
			this.fell = fell;
			this.checkHealth = checkHealth;
		}

		public State getState()
		{
			return state;
		}

		/** Null for weeds, unknown crops or crops the plugin does not plant. */
		public Crop getCrop()
		{
			return crop;
		}

		public int getStage()
		{
			return stage;
		}

		/** Grown tree that must be chopped down before replanting. */
		public boolean isFell()
		{
			return fell;
		}

		/** Grown tree whose health has not been checked yet. */
		public boolean isCheckHealth()
		{
			return checkHealth;
		}
	}

	private static final class Row
	{
		private final int lo;
		private final int hi;
		private final String crop;
		private final String state;
		private final int stage;
		private final int step;
		private final String flag;

		private Row(String[] columns)
		{
			lo = Integer.parseInt(columns[1]);
			hi = Integer.parseInt(columns[2]);
			crop = columns[3];
			state = columns[4];
			stage = Integer.parseInt(columns[5]);
			step = Integer.parseInt(columns[6]);
			flag = columns[7];
		}
	}

	private static final Map<String, Crop> CROPS_BY_NAME;

	static
	{
		Map<String, Crop> crops = new HashMap<>();
		for (Crop crop : Crop.values())
		{
			crops.put(crop.name(), crop);
		}
		CROPS_BY_NAME = Collections.unmodifiableMap(crops);
	}

	private static volatile PatchStates instance;

	private final Map<PatchType, List<Row>> rows = new EnumMap<>(PatchType.class);

	private PatchStates(InputStream in) throws IOException
	{
		for (PatchType type : PatchType.values())
		{
			rows.put(type, new ArrayList<>());
		}

		try (BufferedReader reader = new BufferedReader(
			new InputStreamReader(in, StandardCharsets.UTF_8)))
		{
			String line;
			while ((line = reader.readLine()) != null)
			{
				if (line.isEmpty() || line.startsWith("#"))
				{
					continue;
				}
				String[] columns = line.split("\t");
				if (columns.length < 8)
				{
					continue;
				}
				try
				{
					PatchType type = PatchType.valueOf(columns[0]);
					rows.get(type).add(new Row(columns));
				}
				catch (IllegalArgumentException ignored)
				{
					// A patch type this release does not support.
				}
			}
		}
	}

	/** Shared table loaded from the plugin's resources. */
	public static PatchStates get()
	{
		PatchStates local = instance;
		if (local == null)
		{
			synchronized (PatchStates.class)
			{
				local = instance;
				if (local == null)
				{
					local = load(PatchStates.class.getResourceAsStream("patch-states.tsv"));
					instance = local;
				}
			}
		}
		return local;
	}

	public static PatchStates load(InputStream in)
	{
		if (in == null)
		{
			throw new IllegalStateException("patch-states.tsv is missing from resources");
		}
		try
		{
			return new PatchStates(in);
		}
		catch (IOException e)
		{
			throw new UncheckedIOException(e);
		}
	}

	public Decoded decode(PatchType type, int value)
	{
		for (Row row : rows.get(type))
		{
			if (value < row.lo || value > row.hi)
			{
				continue;
			}
			int stage = row.stage + row.step * (value - row.lo);
			Crop crop = CROPS_BY_NAME.get(row.crop);

			if ("WEEDS".equals(row.crop))
			{
				return new Decoded(State.EMPTY, null, stage, false, false);
			}

			switch (row.state)
			{
				case "GROWING":
					if ("check".equals(row.flag))
					{
						return new Decoded(State.READY, crop, stage, false, true);
					}
					return new Decoded(State.GROWING, crop, stage, false, false);
				case "HARVESTABLE":
					if ("stump".equals(row.flag))
					{
						return new Decoded(State.STUMP, crop, stage, false, false);
					}
					return new Decoded(State.READY, crop, stage, "chop".equals(row.flag), false);
				case "DISEASED":
					return new Decoded(State.DISEASED, crop, stage, false, false);
				case "DEAD":
					return new Decoded(State.DEAD, crop, stage, false, false);
				default:
					return new Decoded(State.UNKNOWN, crop, stage, false, false);
			}
		}
		return new Decoded(State.UNKNOWN, null, 0, false, false);
	}
}

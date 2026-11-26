package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.data.Patches;
import com.romte.braindeadfarmrun.domain.patch.GrowthClock;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchState;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import java.awt.BorderLayout;
import java.awt.Color;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel with the current patch timers. The run planner and supply sections will be added
 * in later phases.
 */
class BraindeadFarmRunPanel extends PluginPanel
{
	private final PatchTracker tracker;
	private final JTextArea text = new JTextArea();

	BraindeadFarmRunPanel(PatchTracker tracker)
	{
		super(false);
		this.tracker = tracker;
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Braindead Farm Run");
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setForeground(Color.WHITE);
		add(title, BorderLayout.NORTH);

		text.setEditable(false);
		text.setOpaque(false);
		text.setForeground(Color.LIGHT_GRAY);
		text.setLineWrap(true);
		text.setWrapStyleWord(true);
		text.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

		JScrollPane scroll = new JScrollPane(text);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		add(scroll, BorderLayout.CENTER);

		refresh();
	}

	void refresh()
	{
		Map<String, PatchState> snapshot = tracker.snapshot();
		Instant now = Instant.now();
		List<String> lines = new ArrayList<>();
		for (PatchState state : snapshot.values())
		{
			if (state.getCrop() == null)
			{
				continue;
			}
			PatchDefinition patch = Patches.byId(state.getPatchId());
			String name = patch != null ? patch.getDisplayName() : state.getPatchId();
			lines.add(name + "\n  " + state.getCrop().getDisplayName() + " - "
				+ describe(state, now));
		}
		if (lines.isEmpty())
		{
			lines.add("No active patches.\nPlant something to start tracking.");
		}

		String value = String.join("\n\n", lines);
		SwingUtilities.invokeLater(() ->
		{
			if (!value.equals(text.getText()))
			{
				text.setText(value);
			}
		});
	}

	private static String describe(PatchState state, Instant now)
	{
		switch (state.statusAt(now))
		{
			case READY:
				if (state.isFell())
				{
					return "ready - chop down";
				}
				if (state.isCheckHealth())
				{
					return "ready - check health";
				}
				return "ready";
			case STUMP:
				return "stump - clear it";
			case DISEASED:
				return "diseased";
			case DEAD:
				return "dead";
			case GROWING:
				return "ready in " + format(GrowthClock.remaining(
					state.getCrop(), state.getPlantedAt(), now));
			default:
				return "empty";
		}
	}

	private static String format(Duration duration)
	{
		long minutes = duration.toMinutes();
		if (minutes <= 0)
		{
			return "<1m";
		}
		long hours = minutes / 60;
		long rest = minutes % 60;
		return hours > 0 ? hours + "h " + rest + "m" : minutes + "m";
	}
}

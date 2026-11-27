package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Quest Helper-style box showing the current stop, patch type, location and transport. */
@Singleton
public class FarmRunInfoOverlay extends OverlayPanel
{
	private static final Color TITLE = Color.WHITE;
	private static final Color TEXT = new Color(228, 228, 228);
	private static final Color LABEL = new Color(190, 210, 230);

	private final RunSession runSession;

	@Inject
	FarmRunInfoOverlay(RunSession runSession)
	{
		this.runSession = runSession;
		setPosition(OverlayPosition.TOP_LEFT);
		setMovable(true);
		setSnappable(true);
		panelComponent.setBackgroundColor(new Color(70, 61, 50, 230));
		panelComponent.setBorder(new Rectangle(4, 4, 4, 4));
		panelComponent.setGap(new Point(2, 2));
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		panelComponent.getChildren().clear();
		if (!runSession.isActive())
		{
			return null;
		}

		int index = runSession.currentIndex();
		List<PatchDefinition> stops = runSession.stops();
		if (index < 0 || index >= stops.size())
		{
			return null;
		}

		PatchDefinition patch = stops.get(index);
		addTitle("Stop " + (index + 1) + "/" + stops.size());
		addLine(patch.getDisplayName(), TITLE);
		addLine("Type: " + patch.getType().getDisplayName(), TEXT);
		addLine("Location: " + locationOf(patch), TEXT);
		addLine("Teleport: " + teleportText(index), LABEL);
		List<String> tasks = runSession.currentTasks();
		if (runSession.isAtStop() && !tasks.isEmpty())
		{
			addLine("Do:", LABEL);
			for (String task : tasks)
			{
				addLine("\u2022 " + task, TEXT);
			}
		}
		return super.render(graphics);
	}

	private void addTitle(String text)
	{
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(text)
			.color(TITLE)
			.build());
	}

	private void addLine(String text, Color color)
	{
		panelComponent.getChildren().add(LineComponent.builder()
			.left(text)
			.leftColor(color)
			.build());
	}

	private String teleportText(int index)
	{
		if (!runSession.routeReady())
		{
			List<String> hints = runSession.teleportsFor(index);
			return hints.isEmpty() ? "Withdraw supplies first" : String.join(" > ", hints);
		}
		if (!runSession.hasShortestPathResult(index))
		{
			return "Calculating...";
		}
		if (!runSession.reachedShortestPath(index))
		{
			return "No route";
		}
		List<String> teleports = runSession.teleportsFor(index);
		return teleports.isEmpty() ? "Walk / no teleport" : String.join(" > ", teleports);
	}

	private static String locationOf(PatchDefinition patch)
	{
		String name = patch.getDisplayName();
		String suffix;
		switch (patch.getType())
		{
			case TREE:
				suffix = " tree";
				break;
			case FRUIT_TREE:
				suffix = " fruit tree";
				break;
			case HERB:
				suffix = " herb";
				break;
			case ALLOTMENT:
				suffix = " allotment";
				break;
			default:
				return name;
		}
		int index = name.toLowerCase(Locale.ROOT).indexOf(suffix);
		return index > 0 ? name.substring(0, index) : name;
	}
}

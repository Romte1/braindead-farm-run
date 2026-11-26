package com.romte.braindeadfarmrun;

import com.google.inject.Provides;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

@Slf4j
@PluginDescriptor(
	name = "Braindead Farm Run",
	description = "Plans allotment, herb, tree and fruit tree runs: what to bring, where to go and when to return",
	tags = {"farming", "farm run", "herb run", "tree run", "fruit tree", "allotment", "timers", "planner"}
)
public class BraindeadFarmRunPlugin extends Plugin
{
	@Inject
	private BraindeadFarmRunConfig config;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PatchTracker patchTracker;

	@Inject
	private EventBus eventBus;

	private NavigationButton navButton;
	private BraindeadFarmRunPanel panel;
	private int panelTicks;

	@Override
	protected void startUp()
	{
		patchTracker.startUp();
		eventBus.register(patchTracker);

		panel = new BraindeadFarmRunPanel(patchTracker);
		navButton = NavigationButton.builder()
			.tooltip("Braindead Farm Run")
			.icon(createIcon())
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);
		log.debug("Braindead Farm Run started (debug={})", config.debug());
	}

	@Override
	protected void shutDown()
	{
		if (navButton != null)
		{
			clientToolbar.removeNavigation(navButton);
			navButton = null;
		}
		eventBus.unregister(patchTracker);
		patchTracker.shutDown();
		panel = null;
		log.debug("Braindead Farm Run stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (panel != null && ++panelTicks % 20 == 0)
		{
			panel.refresh();
		}
	}

	@Provides
	BraindeadFarmRunConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(BraindeadFarmRunConfig.class);
	}

	private static BufferedImage createIcon()
	{
		BufferedImage icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = icon.createGraphics();
		graphics.setColor(new Color(76, 175, 80));
		graphics.fillRoundRect(1, 1, 14, 14, 4, 4);
		graphics.setColor(new Color(27, 94, 32));
		graphics.fillRect(4, 5, 8, 2);
		graphics.fillRect(4, 9, 8, 2);
		graphics.dispose();
		return icon;
	}
}

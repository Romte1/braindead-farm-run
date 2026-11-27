package com.romte.braindeadfarmrun;

import com.google.inject.Provides;
import com.romte.braindeadfarmrun.bank.BankTab;
import com.romte.braindeadfarmrun.bank.SupplyPlanner;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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
	private ConfigManager configManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PatchTracker patchTracker;

	@Inject
	private RunSession runSession;

	@Inject
	private BankTab bankTab;

	@Inject
	private SupplyPlanner supplyPlanner;

	@Inject
	private EventBus eventBus;

	private NavigationButton navButton;
	private BraindeadFarmRunPanel panel;
	private int panelTicks;
	private int bankTicks;

	@Override
	protected void startUp()
	{
		patchTracker.startUp();
		eventBus.register(patchTracker);
		eventBus.register(runSession);

		bankTab.startUp(() -> supplyPlanner.plan(runSession.selectedTypes(), patchTracker.snapshot()));
		bankTab.setEnabled(true);

		panel = new BraindeadFarmRunPanel(runSession, config, configManager);
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
		eventBus.unregister(runSession);
		runSession.stop();
		bankTab.shutDown();
		panel = null;
		log.debug("Braindead Farm Run stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		bankTab.setRunActive(runSession.isActive());
		bankTab.onGameTick();
		if (panel != null && ++panelTicks % 5 == 0)
		{
			panel.refresh();
		}
		if (++bankTicks % 20 == 0)
		{
			bankTab.refresh();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		bankTab.onWidgetLoaded(event);
	}

	@Subscribe
	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		bankTab.onScriptCallbackEvent(event);
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		bankTab.onScriptPreFired(event);
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		bankTab.onScriptPostFired(event);
	}

	@Subscribe(priority = -1.0f)
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		bankTab.onMenuOptionClicked(event);
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (BraindeadFarmRunConfig.GROUP.equals(event.getGroup())
			&& "bottomlessCompostBucket".equals(event.getKey()))
		{
			bankTab.refresh();
			if (panel != null)
			{
				panel.setBottomless(config.bottomlessCompostBucket());
			}
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

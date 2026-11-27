package com.romte.braindeadfarmrun;

import com.google.inject.Provides;
import com.romte.braindeadfarmrun.bank.BankTab;
import com.romte.braindeadfarmrun.bank.SupplyPlanner;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import com.romte.braindeadfarmrun.runtime.ShortestPathService;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;

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
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private PatchTracker patchTracker;

	@Inject
	private RunSession runSession;

	@Inject
	private ShortestPathService shortestPathService;

	@Inject
	private BankTab bankTab;

	@Inject
	private SupplyPlanner supplyPlanner;

	@Inject
	private EventBus eventBus;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private FarmRunInfoOverlay infoOverlay;

	private NavigationButton navButton;
	private BraindeadFarmRunPanel panel;
	private int panelTicks;
	private int questTicks;
	private boolean bankRefreshQueued;
	private boolean bankLayoutDirty;
	private boolean wasActive;
	private int lastResultVersion = -1;

	@Override
	protected void startUp()
	{
		patchTracker.startUp();
		eventBus.register(patchTracker);
		eventBus.register(shortestPathService);
		eventBus.register(runSession);

		bankTab.startUp(() -> runSession.allResultsReady()
			? supplyPlanner.plan(runSession.selectedTypes(), patchTracker.snapshot(),
				runSession.allTeleportLines(), runSession.allRequiredItems())
			: null);
		bankTab.setEnabled(true);
		overlayManager.add(infoOverlay);
		clientThread.invokeLater(supplyPlanner::refreshQuestState);

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
		eventBus.unregister(shortestPathService);
		eventBus.unregister(runSession);
		runSession.stop();
		overlayManager.remove(infoOverlay);
		bankTab.shutDown();
		panel = null;
		log.debug("Braindead Farm Run stopped");
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (questTicks++ % 100 == 0)
		{
			supplyPlanner.refreshQuestState();
		}
		boolean active = runSession.isActive();
		if (active && !wasActive)
		{
			lastResultVersion = -1;
		}
		wasActive = active;
		bankTab.setRunActive(active);
		if (active && runSession.allResultsReady() && !bankTab.hasPlan())
		{
			// Results can arrive before setRunActive(true) is processed. Retry here so a
			// fast stop/start cannot leave the panel stuck on CREATING ROUTE.
			bankTab.invalidatePlan();
			bankTab.refresh();
		}
		if (active && runSession.allResultsReady() && bankTab.hasPlan())
		{
			runSession.setPreparing(false);
			bankTab.unlockButton();
		}
		bankTab.onGameTick();
		if (bankLayoutDirty)
		{
			bankLayoutDirty = false;
			bankTab.refresh();
		}
		if (panel != null && ++panelTicks % 5 == 0)
		{
			panel.refresh();
		}
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK || id == InventoryID.INV || id == InventoryID.WORN)
		{
			bankLayoutDirty = true;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			questTicks = 0;
			clientThread.invokeLater(supplyPlanner::refreshQuestState);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() == VarbitID.SPELLBOOK)
		{
			runSession.refreshTeleportPreference();
		}
	}

	@Subscribe
	public void onPluginMessage(PluginMessage event)
	{
		if (!"shortestpath".equals(event.getNamespace()) || !"result".equals(event.getName())
			|| bankRefreshQueued)
		{
			return;
		}
		// Wait for the whole route's results, then rebuild the bank plan once. Refreshing on
		// every result would recompute and relayout the tab repeatedly while the bank is open.
		bankRefreshQueued = true;
		clientThread.invokeLater(() ->
		{
			bankRefreshQueued = false;
			int version = shortestPathService.resultVersion();
			if (version == lastResultVersion)
			{
				return;
			}
			lastResultVersion = version;
			if (runSession.allResultsReady())
			{
				// Build the supply plan once, only after the whole route is known.
				bankTab.invalidatePlan();
				bankTab.refresh();
			}
		});
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		bankTab.onWidgetLoaded(event);
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			// The player has had a chance to withdraw the tab's supplies; recompute the leg
			// with the inventory they are actually leaving with.
			bankTab.invalidatePlan();
			runSession.refreshRouteForInventory();
		}
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
			bankTab.invalidatePlan();
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

package com.romte.braindeadfarmrun.bank;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.ScriptCallbackEvent;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.ScriptID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.QuantityFormatter;
import net.runelite.client.util.Text;

/**
 * The Farm run bank tab. This is deliberately the same mechanism Quest Helper and Farm Run
 * Autopilot use: hide the bank's own item widgets, reuse them for the run's items, and add our
 * own headers and labels as children of the bank item container. No Bank Tags tag and no widget
 * moving on every bank rebuild.
 */
@Slf4j
@Singleton
public class BankTab
{
	private static final int ITEMS_PER_ROW = 8;
	private static final int ITEM_VERTICAL_SPACING = 36;
	private static final int ITEM_HORIZONTAL_SPACING = 48;
	private static final int ITEM_ROW_START = 51;
	private static final int LINE_VERTICAL_SPACING = 5;
	private static final int LINE_HEIGHT = 2;
	private static final int TEXT_HEIGHT = 15;
	private static final int ICON_SIZE = 10;
	private static final int PLANNED_ROW_SPACING = 64;
	private static final int MAX_REST_ITEMS = 100;
	private static final int ITEM_WIDTH = 36;
	private static final int ITEM_HEIGHT = 32;
	private static final int LABEL_WIDTH = 46;
	private static final int LABEL_HEIGHT = 14;
	private static final int FADED = 120;
	private static final int BANK_FILLER = 6512;
	private static final int LINE_SPRITE = 897;
	private static final int CHECK_SPRITE = 1217;
	private static final int CROSS_SPRITE = 1216;
	private static final int WITHDRAW_QUANTITY_VARBIT = 6590;
	private static final int WITHDRAW_X_VARBIT = 3960;
	private static final int CHARGES_VARBIT = 16125;
	private static final int PLACEHOLDER_VARBIT = 3755;
	private static final int CHARGES_PARAM = 2257;
	private static final Color HEADER = new Color(228, 216, 162);
	private static final Color CARRIED = new Color(76, 193, 82);
	private static final Color IN_STORAGE = new Color(232, 197, 58);
	private static final Color MISSING = new Color(224, 85, 85);
	private static final Color WEAR = new Color(170, 215, 255);
	private static final String REST_OF_BANK = "Everything else";
	private static final String TITLE = "Tab <col=ff0000>Farm run</col>";

	private final Client client;
	private final ClientThread clientThread;
	private final ChatMessageManager chatMessageManager;
	private final BankTabButton button;
	private final OverlayManager overlayManager;
	private final BankTabButtonOverlay overlay;
	private final List<Widget> addedWidgets = new ArrayList<>();
	private final Map<Widget, BankSupplyLine> lineByWidget = new HashMap<>();
	private volatile Supplier<List<BankSupplyLine>> plan = Collections::emptyList;
	private volatile List<BankSupplyLine> cachedPlan = Collections.emptyList();
	private volatile boolean planDirty = true;
	private volatile boolean planComputed;
	private volatile boolean buttonUnlocked;
	private volatile boolean enabled;
	private volatile boolean runActive;
	private boolean layingOut;
	private boolean planComputing;
	private boolean scrollUpdateQueued;
	private int lastScrollHeight = -1;
	private int lastScroll = -1;

	@Inject
	BankTab(Client client, ClientThread clientThread, ChatMessageManager chatMessageManager,
		BankTabButton button, OverlayManager overlayManager, BankTabButtonOverlay overlay)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.chatMessageManager = chatMessageManager;
		this.button = button;
		this.overlayManager = overlayManager;
		this.overlay = overlay;
	}

	public void startUp(Supplier<List<BankSupplyLine>> plan)
	{
		this.plan = plan;
		overlayManager.add(overlay);
	}

	public boolean isBankOpen()
	{
		return button.isBankOpen();
	}

	public boolean isTabOpen()
	{
		return runActive && button.isActive() && button.isBankOpen();
	}

	/** The bank tab button only exists while a run is active. */
	public void setRunActive(boolean active)
	{
		if (runActive == active)
		{
			return;
		}
		runActive = active;
		planDirty = true;
		planComputed = false;
		if (!active)
		{
			cachedPlan = Collections.emptyList();
			buttonUnlocked = false;
		}
		clientThread.invokeLater(() ->
		{
			if (!runActive)
			{
				button.destroy();
				removeAddedWidgets();
			}
			else if (enabled && buttonUnlocked)
			{
				button.init();
			}
		});
	}

	/** The route results changed; the next layout recomputes the supply plan once. */
	public void invalidatePlan()
	{
		planDirty = true;
		planComputed = false;
	}

	public boolean hasPlan()
	{
		return planComputed;
	}

	/** Reveal the bank-tab button only after the complete route and supply plan exist. */
	public void unlockButton()
	{
		if (!runActive || buttonUnlocked)
		{
			return;
		}
		buttonUnlocked = true;
		clientThread.invokeLater(() ->
		{
			if (runActive && enabled)
			{
				button.init();
			}
		});
	}

	public void onGameTick()
	{
		if (enabled)
		{
			button.onGameTick();
		}
	}

	public void shutDown()
	{
		overlayManager.remove(overlay);
		setEnabled(false);
	}

	/**
	 * Widgets may only be touched on the client thread, and the plugin can be enabled from the
	 * config panel (AWT thread), so the actual init/destroy is always deferred.
	 */
	public void setEnabled(boolean enabled)
	{
		if (this.enabled == enabled)
		{
			return;
		}
		this.enabled = enabled;
		clientThread.invokeLater(() -> applyEnabled(enabled));
	}

	private void applyEnabled(boolean enabled)
	{
		if (this.enabled != enabled)
		{
			return; // a newer toggle superseded this one
		}
		if (enabled)
		{
			if (runActive && buttonUnlocked)
			{
				button.init();
			}
		}
		else
		{
			button.destroy();
			removeAddedWidgets();
		}
	}

	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (enabled && event.getGroupId() == InterfaceID.BANKMAIN)
		{
			lastScrollHeight = -1;
			lastScroll = -1;
			addedWidgets.clear();
			lineByWidget.clear();
			if (runActive && buttonUnlocked)
			{
				button.reload();
			}
			else
			{
				button.destroy();
			}
		}
	}

	public void onScriptCallbackEvent(ScriptCallbackEvent event)
	{
		if ("getSearchingTagTab".equals(event.getEventName()) && button.isActive())
		{
			client.getIntStack()[client.getIntStackSize() - 1] = 1;
		}
	}

	public void onScriptPreFired(ScriptPreFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_FINISHBUILDING)
		{
			resetItemWidgetSizes();
			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (button.isActive() && title != null)
			{
				title.setText(TITLE);
			}
		}
		else if (event.getScriptId() == ScriptID.BANKMAIN_SEARCH_TOGGLE)
		{
			button.onSearchToggled();
		}
	}

	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() == ScriptID.BANKMAIN_SEARCHING)
		{
			if (button.isActive())
			{
				client.getIntStack()[client.getIntStackSize() - 1] = 1;
			}
			return;
		}
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING)
		{
			return;
		}
		log.debug("Bank tab build finished: active={} runActive={} enabled={}",
			button.isActive(), runActive, enabled);
		if (!runActive)
		{
			removeAddedWidgets();
			return;
		}
		if (enabled && buttonUnlocked)
		{
			// The bank's title bar is only ready once building finishes, so retry here in case
			// the WidgetLoaded init ran too early.
			button.init();
		}
		if (layingOut)
		{
			return;
		}
		Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null)
		{
			return;
		}
		layingOut = true;
		try
		{
			removeAddedWidgets();
			if (!button.isActive())
			{
				return;
			}
			computePlanIfDirty();
			layOut(itemContainer, itemContainer.getDynamicChildren(), cachedPlan);
		}
		finally
		{
			layingOut = false;
		}
	}

	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!runActive)
		{
			return;
		}
		button.onMenuOptionClicked(event);
		if (event.getParam1() != InterfaceID.Bankmain.ITEMS || !button.isActive())
		{
			return;
		}
		MenuEntry menu = event.getMenuEntry();
		if ("Details".equals(menu.getOption()))
		{
			event.consume();
			BankSupplyLine line = lineByWidget.get(event.getWidget());
			if (line != null)
			{
				showDetails(line);
			}
			return;
		}
		Widget widget = menu.getWidget();
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		if (widget != null && widget.getItemId() > -1 && bank != null)
		{
			int slot = bank.find(widget.getItemId());
			if (slot > -1 && menu.getParam0() != slot)
			{
				menu.setParam0(slot);
			}
		}
	}

	public void refresh()
	{
		if (!runActive || layingOut)
		{
			log.debug("Bank tab refresh skipped: runActive={} layingOut={}", runActive, layingOut);
			return;
		}
		computePlanIfDirty();
		if (!button.isActive())
		{
			return;
		}
		Widget itemContainer = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (itemContainer == null || itemContainer.isHidden())
		{
			return;
		}
		layingOut = true;
		try
		{
			removeAddedWidgets();
			layOut(itemContainer, itemContainer.getDynamicChildren(), cachedPlan);
		}
		finally
		{
			layingOut = false;
		}
	}

	private void computePlanIfDirty()
	{
		if (!planDirty || planComputing)
		{
			return;
		}
		planComputing = true;
		// Do not read the pouch/enums while the bank-building script is still on the stack;
		// defer the plan to the next client tick and keep the tab responsive.
		clientThread.invokeLater(() ->
		{
			planComputing = false;
			if (!planDirty)
			{
				return;
			}
			log.debug("Bank tab plan computation starting");
			List<BankSupplyLine> computed = plan.get();
			if (computed == null)
			{
				return; // route results are still arriving
			}
			cachedPlan = computed;
			planDirty = false;
			planComputed = true;
			log.debug("Bank tab plan computed: {} lines", cachedPlan.size());
			refresh();
		});
	}

	private void layOut(Widget itemContainer, Widget[] itemWidgets, List<BankSupplyLine> lines)
	{
		log.debug("Bank tab layout starting: planned={} children={} dynamic={}", lines.size(),
			itemContainer.getChildren() == null ? -1 : itemContainer.getChildren().length,
			itemWidgets == null ? -1 : itemWidgets.length);
		ItemContainer bank = client.getItemContainer(InventoryID.BANK);
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (bank == null)
		{
			return;
		}
		lineByWidget.clear();
		hideBankWidgets(itemContainer, itemWidgets);

		List<BankSupplyLine> equipment = new ArrayList<>();
		List<BankSupplyLine> inventoryLines = new ArrayList<>();
		Set<Integer> planned = new HashSet<>();
		for (BankSupplyLine line : lines)
		{
			(line.wear ? equipment : inventoryLines).add(line);
			for (int id : line.itemIds)
			{
				planned.add(id);
				int noted = notedId(id);
				if (noted > 0)
				{
					planned.add(noted);
				}
			}
		}
		equipment.sort(Comparator.comparingInt(line -> groupRank(line.group)));
		inventoryLines.sort(Comparator.comparingInt(line -> groupRank(line.group)));

		LinkedHashSet<Integer> rest = new LinkedHashSet<>();
		for (Item item : bank.getItems())
		{
			if (rest.size() >= MAX_REST_ITEMS)
			{
				break;
			}
			if (item.getId() <= -1 || item.getId() == BANK_FILLER || planned.contains(item.getId())
				|| isUnplannedRune(item.getId()))
			{
				continue;
			}
			rest.add(item.getId());
		}

		List<PendingText> pending = new ArrayList<>();
		int height = 0;

		int[] nextWidget = {0};
		if (!equipment.isEmpty())
		{
			height = addHeader(itemContainer, "To Equip", height);
			height = drawSection(itemContainer, equipment, height, nextWidget, pending, bank, inventory);
		}
		if (!inventoryLines.isEmpty())
		{
			height = addHeader(itemContainer, "Inventory", height);
			height = drawSection(itemContainer, inventoryLines, height, nextWidget, pending, bank, inventory);
		}

		if (!rest.isEmpty())
		{
			height = addHeader(itemContainer, REST_OF_BANK, height);
			int added = 0;
			Iterator<Integer> iterator = rest.iterator();
			while (iterator.hasNext())
			{
				Widget widget = itemContainer.getChild(nextWidget[0]++);
				if (widget == null)
				{
					break;
				}
				int id = iterator.next();
				drawItem(widget, id, bank, null);
				placeItem(widget, added++, height, ITEM_VERTICAL_SPACING);
			}
			height = rowsEnd(height, added, ITEM_VERTICAL_SPACING);
		}

		for (PendingText pendingText : pending)
		{
			int textX = pendingText.x + (pendingText.spriteId == -1 ? 2 : ICON_SIZE + 2);
			Widget label = createText(itemContainer, pendingText.text, pendingText.colour, 34,
				LABEL_HEIGHT, textX, pendingText.y);
			label.setFontId(496);
			label.setYTextAlignment(1);
			label.revalidate();
			addedWidgets.add(label);
			if (pendingText.spriteId != -1)
			{
				addedWidgets.add(createIcon(itemContainer, pendingText.spriteId,
					pendingText.x, pendingText.y + 2));
			}
		}

		log.debug("Bank tab layout sections done: equip={} inventory={} rest={} height={}",
			equipment.size(), inventoryLines.size(), rest.size(), height);
		int scrollHeight = Math.max(height, itemContainer.getHeight());
		itemContainer.setScrollHeight(scrollHeight);
		int scroll = itemContainer.getScrollY();
		if ((scrollHeight != lastScrollHeight || scroll != lastScroll) && !scrollUpdateQueued)
		{
			lastScrollHeight = scrollHeight;
			lastScroll = scroll;
			scrollUpdateQueued = true;
			clientThread.invokeLater(() ->
			{
				scrollUpdateQueued = false;
				client.runScript(ScriptID.UPDATE_SCROLLBAR,
					InterfaceID.Bankmain.SCROLLBAR, InterfaceID.Bankmain.ITEMS, scroll);
			});
		}
	}

	private int drawSection(Widget itemContainer, List<BankSupplyLine> lines, int height,
		int[] nextWidget, List<PendingText> pending, ItemContainer bank, ItemContainer inventory)
	{
		int added = 0;
		for (BankSupplyLine line : lines)
		{
			Widget widget = itemContainer.getChild(nextWidget[0]++);
			if (widget == null)
			{
				break;
			}
			drawItem(widget, displayItem(line, bank), bank, line);
			placeItem(widget, added++, height, PLANNED_ROW_SPACING);
			pending.add(neededText(widget, line));
			if (line.wear && count(inventory, line.itemIds) > 0)
			{
				pending.add(wearText(widget));
			}
		}
		return rowsEnd(height, added, PLANNED_ROW_SPACING);
	}

	private static int groupRank(SupplyGroup group)
	{
		if (group == null)
		{
			return 6;
		}
		switch (group)
		{
			case EQUIPMENT:
				return 0;
			case TOOLS:
				return 1;
			case SEEDS:
				return 2;
			case RUNES:
				return 3;
			case TRAVEL:
				return 4;
			case PAYMENTS:
				return 5;
			default:
				return 6;
		}
	}

	private int displayItem(BankSupplyLine line, ItemContainer bank)
	{
		int best = line.itemIds[0];
		int bestScore = -1;
		int bestCount = -1;
		for (int id : line.itemIds)
		{
			int count = count(bank, id);
			if (count <= 0)
			{
				continue;
			}
			int noted = notedId(id);
			int display = id;
			if (line.noted && noted > 0 && bank.count(noted) > 0)
			{
				display = noted;
			}
			else if (bank.count(id) <= 0 && noted > 0)
			{
				display = noted;
			}
			int score = variantScore(id);
			if (score > bestScore || (score == bestScore && count > bestCount))
			{
				best = display;
				bestScore = score;
				bestCount = count;
			}
		}
		if (bestScore < 0)
		{
			for (int id : line.itemIds)
			{
				int score = variantScore(id);
				if (score > bestScore)
				{
					best = id;
					bestScore = score;
				}
			}
		}
		return best;
	}

	/** Prefer charged jewellery variants and the highest tier over an uncharged or low tier one. */
	private int variantScore(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		String name = def.getName();
		String lower = name.toLowerCase(Locale.ROOT);
		if (lower.contains("uncharged") || lower.contains("empty"))
		{
			return 0;
		}
		if (lower.contains("eternal"))
		{
			return 1000;
		}
		int open = name.lastIndexOf('(');
		int close = name.lastIndexOf(')');
		if (open >= 0 && close > open + 1)
		{
			try
			{
				return Integer.parseInt(name.substring(open + 1, close).trim());
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		int space = name.lastIndexOf(' ');
		if (space >= 0)
		{
			try
			{
				return Integer.parseInt(name.substring(space + 1).trim());
			}
			catch (NumberFormatException ignored)
			{
			}
		}
		return 1;
	}

	private void drawItem(Widget widget, int itemId, ItemContainer bank, BankSupplyLine line)
	{
		int qty = count(bank, itemId);
		// Planned rows show the amount to withdraw, not the whole bank stack; otherwise a
		// 50k rune stack in the bank looks like a 50k requirement.
		int shownQty = line == null ? qty : Math.min(qty, Math.max(1, line.need));
		ItemComposition def = client.getItemDefinition(itemId);
		widget.setItemId(itemId);
		widget.setItemQuantity(shownQty);
		widget.setItemQuantityMode(1);
		widget.setDragDeadTime(1000);
		widget.setName("<col=ff9040>" + def.getName() + "</col>");
		widget.clearActions();
		if (def.getPlaceholderTemplateId() >= 0 && def.getPlaceholderId() >= 0)
		{
			widget.setOpacity(FADED);
			widget.setAction(7, "Release");
			widget.setAction(9, "Examine");
		}
		else if (qty == 0)
		{
			widget.setOpacity(FADED);
			widget.setItemQuantity(0);
			widget.setItemQuantityMode(0);
			widget.setAction(1, "Details");
		}
		else
		{
			setWithdrawActions(widget, def);
			widget.setOpacity(0);
		}
		widget.setOnDragListener(ScriptID.BANKMAIN_DRAGSCROLL, ScriptEvent.WIDGET_ID,
			ScriptEvent.WIDGET_INDEX, ScriptEvent.MOUSE_X, ScriptEvent.MOUSE_Y,
			InterfaceID.Bankmain.SCROLLBAR, 0);
		widget.setOnDragCompleteListener((JavaScriptCallback) ev ->
		{
		});
		if (line != null)
		{
			lineByWidget.put(widget, line);
		}
		widget.setHidden(false);
		widget.revalidate();
	}

	private void setWithdrawActions(Widget widget, ItemComposition def)
	{
		int quantityType = client.getVarbitValue(WITHDRAW_QUANTITY_VARBIT);
		int requested = client.getVarbitValue(WITHDRAW_X_VARBIT);
		String suffix;
		switch (quantityType)
		{
			case 1:
				suffix = "5";
				break;
			case 2:
				suffix = "10";
				break;
			case 3:
				suffix = Integer.toString(Math.max(1, requested));
				break;
			case 4:
				suffix = "All";
				break;
			default:
				suffix = "1";
		}
		widget.setAction(0, "Withdraw-" + suffix);
		if (quantityType != 0)
		{
			widget.setAction(1, "Withdraw-1");
		}
		widget.setAction(2, "Withdraw-5");
		widget.setAction(3, "Withdraw-10");
		if (requested > 0)
		{
			widget.setAction(4, "Withdraw-" + requested);
		}
		widget.setAction(5, "Withdraw-X");
		widget.setAction(6, "Withdraw-All");
		widget.setAction(7, "Withdraw-All-but-1");
		if (client.getVarbitValue(CHARGES_VARBIT) == 1 && def.getIntValue(CHARGES_PARAM) != -1)
		{
			widget.setAction(8, "Configure-Charges");
		}
		if (client.getVarbitValue(PLACEHOLDER_VARBIT) == 0)
		{
			widget.setAction(9, "Placeholder");
		}
		widget.setAction(10, "Examine");
	}

	/** Runes that are not part of the plan belong to the normal bank, not the run tab. */
	private boolean isUnplannedRune(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		if (def == null)
		{
			return false;
		}
		String name = def.getName().toLowerCase(Locale.ROOT);
		return name.endsWith("rune") || name.contains(" rune");
	}

	private int count(ItemContainer container, int[] itemIds)
	{
		if (container == null)
		{
			return 0;
		}
		int total = 0;
		for (int id : itemIds)
		{
			total += count(container, id);
		}
		return total;
	}

	private int count(ItemContainer container, int itemId)
	{
		if (container == null)
		{
			return 0;
		}
		int total = container.count(itemId);
		int noted = notedId(itemId);
		if (noted > 0 && noted != itemId)
		{
			total += container.count(noted);
		}
		return total;
	}

	private int notedId(int itemId)
	{
		ItemComposition def = client.getItemDefinition(itemId);
		return def == null ? -1 : def.getLinkedNoteId();
	}

	private PendingText neededText(Widget widget, BankSupplyLine line)
	{
		String needed = QuantityFormatter.quantityToStackSize(line.need)
			+ (line.noted ? " noted" : "");
		int x = widget.getOriginalX() - 5;
		int y = widget.getOriginalY() + ITEM_HEIGHT + 1;
		int inBank = count(client.getItemContainer(InventoryID.BANK), line.itemIds);
		int carried = count(client.getItemContainer(InventoryID.INV), line.itemIds)
			+ count(client.getItemContainer(InventoryID.WORN), line.itemIds);
		int have = inBank + carried;
		Color colour;
		int sprite;
		if (carried >= line.need)
		{
			colour = CARRIED;
			sprite = CHECK_SPRITE;
		}
		else if (have >= line.need)
		{
			colour = IN_STORAGE;
			sprite = -1;
		}
		else
		{
			colour = MISSING;
			sprite = CROSS_SPRITE;
		}
		return new PendingText(needed, colour.getRGB(), x, y, sprite);
	}

	private static PendingText wearText(Widget widget)
	{
		return new PendingText("WEAR", WEAR.getRGB(), widget.getOriginalX() - 5,
			widget.getOriginalY() + ITEM_HEIGHT + 16, -1);
	}

	private void showDetails(BankSupplyLine line)
	{
		int inBank = count(client.getItemContainer(InventoryID.BANK), line.itemIds);
		int carried = count(client.getItemContainer(InventoryID.INV), line.itemIds)
			+ count(client.getItemContainer(InventoryID.WORN), line.itemIds);
		String where = carried >= line.need ? "carried"
			: inBank + carried >= line.need ? "in your bank" : null;
		String message = "You need " + QuantityFormatter.formatNumber(line.need) + " x "
			+ Text.removeTags(line.name) + ". "
			+ (where == null ? "You don't have any." : "You have " + where + ".")
			+ (line.details == null || line.details.isEmpty() ? "" : " " + line.details);
		chatMessageManager.queue(QueuedMessage.builder()
			.type(ChatMessageType.ITEM_EXAMINE)
			.runeLiteFormattedMessage(message)
			.build());
	}

	private void hideBankWidgets(Widget itemContainer, Widget[] itemWidgets)
	{
		for (int i = 0; i < itemWidgets.length; i++)
		{
			Widget widget = itemContainer.getChild(i);
			if (widget == null)
			{
				continue;
			}
			boolean item = !widget.isSelfHidden() && widget.getItemId() > -1
				&& widget.getItemId() != BANK_FILLER;
			String text = widget.getText();
			boolean tabDivider = widget.getSpriteId() == LINE_SPRITE
				|| (text != null && text.contains("Tab"));
			if (!item && !tabDivider)
			{
				continue;
			}
			widget.setHidden(true);
		}
	}

	private void resetItemWidgetSizes()
	{
		Widget container = client.getWidget(InterfaceID.Bankmain.ITEMS);
		if (container == null || container.getChildren() == null)
		{
			return;
		}
		for (Widget child : container.getChildren())
		{
			if (child.getOriginalHeight() < ITEM_HEIGHT)
			{
				break;
			}
			if (child.getOriginalWidth() == ITEM_WIDTH && child.getOriginalHeight() == ITEM_HEIGHT)
			{
				continue;
			}
			child.setOriginalWidth(ITEM_WIDTH);
			child.setOriginalHeight(ITEM_HEIGHT);
			child.revalidate();
		}
	}

	private void removeAddedWidgets()
	{
		if (addedWidgets.isEmpty())
		{
			return;
		}
		Widget parent = addedWidgets.get(0).getParent();
		if (parent != null && parent.getChildren() != null)
		{
			Widget[] children = parent.getChildren();
			List<Widget> kept = new ArrayList<>();
			for (Widget child : children)
			{
				if (child != null && !addedWidgets.contains(child))
				{
					kept.add(child);
				}
			}
			// setChildren casts to the client's internal widget array type, so preserve the
			// original array's runtime component type instead of creating a plain Widget[].
			Widget[] filtered = Arrays.copyOf(children, kept.size());
			for (int i = 0; i < kept.size(); i++)
			{
				filtered[i] = kept.get(i);
			}
			parent.setChildren(filtered);
			parent.revalidate();
		}
		addedWidgets.clear();
	}

	private int addHeader(Widget container, String title, int height)
	{
		addedWidgets.add(createLine(container, height));
		addedWidgets.add(createText(container, title, HEADER.getRGB(), 435, TEXT_HEIGHT,
			ITEM_ROW_START, height + LINE_VERTICAL_SPACING));
		return height + LINE_VERTICAL_SPACING + TEXT_HEIGHT;
	}

	private static int rowsEnd(int height, int items, int rowSpacing)
	{
		int rows = (items + ITEMS_PER_ROW - 1) / ITEMS_PER_ROW;
		return height + rows * rowSpacing;
	}

	private static void placeItem(Widget widget, int index, int sectionTop, int rowSpacing)
	{
		int y = sectionTop + index / ITEMS_PER_ROW * rowSpacing;
		int x = index % ITEMS_PER_ROW * ITEM_HORIZONTAL_SPACING + ITEM_ROW_START;
		if (widget.getOriginalY() != y || widget.getOriginalX() != x)
		{
			widget.setOriginalY(y);
			widget.setOriginalX(x);
			widget.revalidate();
		}
	}

	private static Widget createLine(Widget container, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(384);
		widget.setOriginalHeight(LINE_HEIGHT);
		widget.setOriginalX(ITEM_ROW_START);
		widget.setOriginalY(y);
		widget.setSpriteId(LINE_SPRITE);
		widget.revalidate();
		return widget;
	}

	private static Widget createText(Widget container, String text, int colour, int width, int height,
		int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.TEXT);
		widget.setOriginalWidth(width);
		widget.setOriginalHeight(height);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setText(text);
		widget.setFontId(494);
		widget.setTextColor(colour);
		widget.setTextShadowed(true);
		widget.revalidate();
		return widget;
	}

	private static Widget createIcon(Widget container, int spriteId, int x, int y)
	{
		Widget widget = container.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(ICON_SIZE);
		widget.setOriginalHeight(ICON_SIZE);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.revalidate();
		return widget;
	}

	private static final class PendingText
	{
		final String text;
		final int colour;
		final int x;
		final int y;
		final int spriteId;

		PendingText(String text, int colour, int x, int y, int spriteId)
		{
			this.text = text;
			this.colour = colour;
			this.x = x;
			this.y = y;
			this.spriteId = spriteId;
		}
	}
}

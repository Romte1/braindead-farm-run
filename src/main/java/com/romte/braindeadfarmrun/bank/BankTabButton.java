package com.romte.braindeadfarmrun.bank;

import java.awt.Rectangle;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptEvent;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.JavaScriptCallback;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.plugins.bank.BankSearch;

/**
 * The button in the bank title bar that opens the Farm run tab. This is the same bank-tab
 * mechanism Quest Helper and Farm Run Autopilot use: a normal bank-tab button, not a Bank Tags
 * tag.
 */
@Slf4j
@Singleton
class BankTabButton
{
	static final String NAME = "braindead farm run";
	private static final String VIEW_TAB = "View tab ";
	private static final int POTION_STORE_VARBIT = 4150;
	private static final int POTION_STORE_TAB = 15;
	private static final int BUTTON_SIZE = 25;
	private static final int BUTTON_X = 380;
	private static final int BUTTON_Y = 5;
	private static final int TAB_BUTTON = 195;
	private static final int TAB_BUTTON_ACTIVE = 196;
	private static final int SEARCH_SPRITE = 170;
	private static final int SOUND_TAB = 2266;

	private final Client client;
	private final ClientThread clientThread;
	private final BankSearch bankSearch;
	private final ChatMessageManager chatMessageManager;
	private boolean active;
	private Widget background;

	@Inject
	BankTabButton(Client client, ClientThread clientThread, BankSearch bankSearch,
		ChatMessageManager chatMessageManager)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.bankSearch = bankSearch;
		this.chatMessageManager = chatMessageManager;
	}

	void init()
	{
		if (isBankHidden())
		{
			return;
		}
		Widget parent = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		if (parent == null)
		{
			return;
		}
		// A bank rebuild can leave the old button hidden while still being a child of the
		// universe. Always create a fresh button and hide the old one, like Quest Helper.
		if (background != null)
		{
			background.setHidden(true);
		}
		log.debug("Bank tab button init active={}", active);
		background = createGraphic(parent, NAME, TAB_BUTTON, BUTTON_SIZE, BUTTON_X, BUTTON_Y);
		background.setAction(1, VIEW_TAB);
		background.setOnOpListener((Object[]) new Object[]{(JavaScriptCallback) this::onClick});
		if (active)
		{
			background.setSpriteId(TAB_BUTTON_ACTIVE);
			background.revalidate();
			clientThread.invokeLater(this::activate);
		}
	}

	/** The bank interface was loaded again; the old widgets belong to the previous one. */
	void reload()
	{
		if (background != null)
		{
			background.setHidden(true);
		}
		background = null;
		init();
	}

	void onGameTick()
	{
		if (background == null || background.isHidden() || isBankHidden())
		{
			return;
		}
		boolean lit = active;
		int sprite = lit ? TAB_BUTTON_ACTIVE : TAB_BUTTON;
		if (background.getSpriteId() != sprite)
		{
			background.setSpriteId(sprite);
			background.revalidate();
		}
	}

	/** The button's on-screen bounds while the bank is open, or null if it isn't showing. */
	Rectangle bounds()
	{
		if (background == null || background.isHidden() || isBankHidden())
		{
			return null;
		}
		Rectangle b = background.getBounds();
		return b == null || b.width <= 0 ? null : b;
	}

	boolean isBankOpen()
	{
		return !isBankHidden();
	}

	void destroy()
	{
		if (active)
		{
			close();
			bankSearch.reset(true);
		}
		if (background != null)
		{
			background.setHidden(true);
		}
		background = null;
		active = false;
	}

	void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!active || isBankHidden())
		{
			return;
		}
		String option = event.getMenuOption();
		String target = event.getMenuTarget();
		Widget clicked = event.getWidget();
		boolean own = clicked == background
			|| (target != null && (target.contains(NAME)
				|| target.toLowerCase().contains("farm run")));
		boolean otherTab = option.startsWith("View tab") && !own;
		if (otherTab || option.equals("View all items") || option.startsWith("View tag tab")
			|| option.startsWith("Potion store"))
		{
			close();
		}
	}

	void onSearchToggled()
	{
		log.debug("Bank tab button search toggled active={}", active);
		if (active)
		{
			close();
			client.setVarcStrValue(359, "");
			client.setVarcIntValue(5, 0);
		}
	}

	private void onClick(ScriptEvent event)
	{
		if (event.getOp() != 2)
		{
			return;
		}
		if (client.getVarbitValue(POTION_STORE_VARBIT) == POTION_STORE_TAB)
		{
			chatMessageManager.queue(QueuedMessage.builder()
				.type(ChatMessageType.GAMEMESSAGE)
				.runeLiteFormattedMessage("Close the potion store first, then open the farm run tab.")
				.build());
			return;
		}
		client.setVarbit(POTION_STORE_VARBIT, 0);
		if (active)
		{
			close();
			bankSearch.reset(true);
		}
		else
		{
			activate();
		}
		client.playSoundEffect(SOUND_TAB);
	}

	private void activate()
	{
		if (background == null)
		{
			return;
		}
		boolean wasActive = active;
		log.debug("Bank tab button activate wasActive={}", wasActive);
		background.setSpriteId(TAB_BUTTON_ACTIVE);
		background.revalidate();
		active = true;
		if (!wasActive)
		{
			// Only reset the search when the tab is actually being opened. Reapplying the
			// active state after a bank rebuild must not toggle search and close the tab.
			bankSearch.reset(true);
			clearSearchButton();
		}
	}

	private void close()
	{
		log.debug("Bank tab button close");
		active = false;
		if (background != null)
		{
			background.setSpriteId(TAB_BUTTON);
			background.revalidate();
		}
	}

	private void clearSearchButton()
	{
		Widget search = client.getWidget(InterfaceID.Bankmain.SEARCH);
		if (search != null)
		{
			search.setOnTimerListener((Object[]) null);
			search.setSpriteId(SEARCH_SPRITE);
		}
	}

	private boolean isBankHidden()
	{
		Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
		return bank == null || bank.isHidden();
	}

	private static Widget createGraphic(Widget parent, String name, int spriteId, int size, int x, int y)
	{
		Widget widget = parent.createChild(-1, WidgetType.GRAPHIC);
		widget.setOriginalWidth(size);
		widget.setOriginalHeight(size);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.setSpriteId(spriteId);
		widget.setHasListener(true);
		widget.setName(name);
		widget.revalidate();
		return widget;
	}

	boolean isActive()
	{
		return active;
	}
}

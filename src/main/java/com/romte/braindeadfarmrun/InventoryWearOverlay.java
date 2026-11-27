package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.bank.BankTab;
import java.awt.Color;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Marks primary run equipment with a WEAR tag only while it is in the inventory. */
@Singleton
class InventoryWearOverlay extends WidgetItemOverlay
{
	private static final Color WEAR = new Color(170, 215, 255);

	private final BankTab bankTab;
	private final ItemManager itemManager;

	@Inject
	InventoryWearOverlay(BankTab bankTab, ItemManager itemManager)
	{
		this.bankTab = bankTab;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		Set<Integer> wearIds = bankTab.wearItemIds();
		if (wearIds.isEmpty())
		{
			return;
		}
		int canonical = itemManager.canonicalize(itemId);
		if (!wearIds.contains(canonical))
		{
			return;
		}
		drawWear(graphics, widgetItem.getCanvasBounds());
	}

	private static void drawWear(Graphics2D graphics, Rectangle bounds)
	{
		if (bounds == null)
		{
			return;
		}
		graphics.setFont(FontManager.getRunescapeSmallFont());
		FontMetrics metrics = graphics.getFontMetrics();
		String text = "WEAR";
		int x = bounds.x + (bounds.width - metrics.stringWidth(text)) / 2;
		int y = bounds.y + (bounds.height - metrics.getHeight()) / 2 + metrics.getAscent();
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, x + 1, y + 1);
		graphics.setColor(WEAR);
		graphics.drawString(text, x, y);
	}
}

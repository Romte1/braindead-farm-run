package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.bank.SupplyPlanner;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.util.ImageUtil;

/** Quest Helper-style filled outline on the inventory item used by the current step. */
@Singleton
public class InventoryActionOverlay extends WidgetItemOverlay
{
	private static final Color GUIDE = new Color(51, 187, 51);
	private static final Color FILL = new Color(51, 187, 51, 65);

	private final RunSession runSession;
	private final SupplyPlanner supplyPlanner;
	private final ItemManager itemManager;

	@Inject
	InventoryActionOverlay(RunSession runSession, SupplyPlanner supplyPlanner, ItemManager itemManager)
	{
		this.runSession = runSession;
		this.supplyPlanner = supplyPlanner;
		this.itemManager = itemManager;
		showOnInventory();
	}

	@Override
	public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
	{
		String required = runSession.currentItemName();
		if (required == null)
		{
			return;
		}
		int canonical = itemManager.canonicalize(itemId);
		boolean matches = false;
		for (int id : supplyPlanner.itemIdsFor(required))
		{
			if (id == canonical)
			{
				matches = true;
				break;
			}
		}
		if (!matches)
		{
			return;
		}

		Rectangle bounds = widgetItem.getCanvasBounds();
		BufferedImage outline = itemManager.getItemOutline(itemId, widgetItem.getQuantity(), GUIDE);
		if (outline != null)
		{
			graphics.drawImage(outline, bounds.x, bounds.y, null);
		}
		BufferedImage image = itemManager.getImage(itemId, widgetItem.getQuantity(), false);
		if (image != null)
		{
			graphics.drawImage(ImageUtil.fillImage(image, FILL), bounds.x, bounds.y, null);
		}
	}
}

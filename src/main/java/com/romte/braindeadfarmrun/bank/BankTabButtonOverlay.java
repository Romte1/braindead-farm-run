package com.romte.braindeadfarmrun.bank;

import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/**
 * Draws the bottomless compost bucket icon centred on the bank tab button. The button itself
 * uses the same bank-tab background as Quest Helper; this overlay supplies the item artwork.
 */
class BankTabButtonOverlay extends Overlay
{
	private final BankTabButton button;
	private final ItemManager itemManager;

	@Inject
	BankTabButtonOverlay(BankTabButton button, ItemManager itemManager)
	{
		this.button = button;
		this.itemManager = itemManager;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		Rectangle bounds = button.bounds();
		if (bounds == null)
		{
			return null;
		}
		BufferedImage image = itemManager.getImage(ItemID.BOTTOMLESS_COMPOST_BUCKET_FILLED);
		if (image == null)
		{
			return null;
		}
		int size = Math.min(bounds.width, bounds.height) - 6;
		if (size <= 0)
		{
			return null;
		}
		int x = bounds.x + (bounds.width - size) / 2;
		int y = bounds.y + (bounds.height - size) / 2;
		graphics.drawImage(image, x, y, size, size, null);
		return null;
	}
}

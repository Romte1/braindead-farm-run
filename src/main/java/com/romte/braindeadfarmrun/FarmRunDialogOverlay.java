package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.Text;

/** Colors the affirmative dialogue choice green while a farmer payment is the next action. */
@Singleton
public class FarmRunDialogOverlay extends Overlay
{
	private static final Color GUIDE = new Color(51, 187, 51);

	private final Client client;
	private final RunSession runSession;
	private Widget highlighted;
	private int originalColor;

	@Inject
	FarmRunDialogOverlay(Client client, RunSession runSession)
	{
		this.client = client;
		this.runSession = runSession;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!runSession.isActive() || !runSession.currentTargetIsFarmer())
		{
			restore();
			return null;
		}

		Widget options = client.getWidget(InterfaceID.Chatmenu.OPTIONS);
		Widget target = findYes(options);
		if (target == null)
		{
			restore();
			return null;
		}
		if (target != highlighted)
		{
			restore();
			highlighted = target;
			originalColor = target.getTextColor();
			target.setTextColor(GUIDE.getRGB());
		}
		return null;
	}

	private static Widget findYes(Widget options)
	{
		if (options == null)
		{
			return null;
		}
		Widget found = findYes(options.getChildren());
		return found != null ? found : findYes(options.getNestedChildren());
	}

	private static Widget findYes(Widget[] choices)
	{
		if (choices == null)
		{
			return null;
		}
		for (Widget choice : choices)
		{
			if (choice == null || choice.isHidden() || choice.getText() == null)
			{
				continue;
			}
			String text = Text.removeTags(choice.getText()).trim().toLowerCase(Locale.ROOT);
			if (text.startsWith("yes"))
			{
				return choice;
			}
		}
		return null;
	}

	void restore()
	{
		if (highlighted != null)
		{
			highlighted.setTextColor(originalColor);
			highlighted = null;
		}
	}
}

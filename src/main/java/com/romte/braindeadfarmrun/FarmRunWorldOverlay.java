package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

/**
 * Quest Helper-style world guidance: a green outline around the patch or farmer the current
 * checklist step needs, with a downward arrow over the exact next target.
 */
@Singleton
public class FarmRunWorldOverlay extends Overlay
{
	private static final Color GUIDE = new Color(51, 187, 51);
	private static final int OUTLINE_THICKNESS = 2;
	private static final int OUTLINE_FEATHERING = 0;
	private static final int ARROW_GAP = 15;

	private final Client client;
	private final RunSession runSession;
	private final PatchTracker patchTracker;
	private final ModelOutlineRenderer outlineRenderer;

	@Inject
	FarmRunWorldOverlay(Client client, RunSession runSession, PatchTracker patchTracker,
		ModelOutlineRenderer outlineRenderer)
	{
		this.client = client;
		this.runSession = runSession;
		this.patchTracker = patchTracker;
		this.outlineRenderer = outlineRenderer;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!runSession.isActive())
		{
			return null;
		}
		PatchDefinition patch = runSession.currentPatch();
		if (patch == null)
		{
			return null;
		}

		boolean showArrow = client.getGameCycle() % 4 < 2;
		if (runSession.currentTargetIsFarmer())
		{
			NPC farmer = patchTracker.farmerFor(patch);
			if (farmer != null)
			{
				outlineRenderer.drawOutline(farmer, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
				Shape hull = farmer.getConvexHull();
				if (hull != null)
				{
					if (showArrow)
					{
						drawArrowAbove(graphics, hull);
					}
				}
				else
				{
					drawTileGuide(graphics, farmer.getLocalLocation(), showArrow);
				}
				return null;
			}
		}

		GameObject object = patchTracker.objectFor(patch);
		if (object != null)
		{
			outlineRenderer.drawOutline(object, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
			Shape hull = object.getConvexHull();
			if (hull != null)
			{
				if (showArrow)
				{
					drawArrowAbove(graphics, hull);
				}
			}
			else
			{
				drawTileGuide(graphics, object.getLocalLocation(), showArrow);
			}
			return null;
		}

		WorldPoint point = new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		drawTileGuide(graphics, local, showArrow);
		return null;
	}

	private void drawTileGuide(Graphics2D graphics, LocalPoint local, boolean showArrow)
	{
		if (local == null)
		{
			return;
		}
		Polygon tile = Perspective.getCanvasTilePoly(client, local, 30);
		if (tile == null)
		{
			return;
		}
		OverlayUtil.renderPolygon(graphics, tile, GUIDE);
		if (showArrow)
		{
			Rectangle bounds = tile.getBounds();
			GuideArrow.drawWorldArrow(graphics, GUIDE, bounds.x + bounds.width / 2,
				bounds.y + bounds.height / 2);
		}
	}

	private static void drawArrowAbove(Graphics2D graphics, Shape shape)
	{
		if (shape == null)
		{
			return;
		}
		Rectangle bounds = shape.getBounds();
		if (bounds.width <= 0 || bounds.height <= 0)
		{
			return;
		}
		GuideArrow.drawWorldArrow(graphics, GUIDE, bounds.x + bounds.width / 2,
			bounds.y - ARROW_GAP);
	}
}

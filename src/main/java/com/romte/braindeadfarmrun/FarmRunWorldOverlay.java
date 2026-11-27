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

		boolean showArrow = client.getTickCount() % 4 < 2;
		if (runSession.currentTargetIsFarmer())
		{
			NPC farmer = patchTracker.farmerFor(patch);
			if (farmer != null)
			{
				outlineRenderer.drawOutline(farmer, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
				if (showArrow)
				{
					drawArrowOverNpc(graphics, farmer);
				}
				return null;
			}
		}

		GameObject object = patchTracker.objectFor(patch);
		if (object != null)
		{
			outlineRenderer.drawOutline(object, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
			if (showArrow)
			{
				drawArrowOverObject(graphics, object);
			}
			return null;
		}

		WorldPoint point = new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		drawTileGuide(graphics, local, showArrow);
		return null;
	}

	private void drawArrowOverNpc(Graphics2D graphics, NPC npc)
	{
		Shape hull = npc.getConvexHull();
		Polygon tile = tileOf(npc.getLocalLocation());
		int x = tile != null ? tile.getBounds().x + tile.getBounds().width / 2
			: hull == null ? 0 : hull.getBounds().x + hull.getBounds().width / 2;
		int y = hull != null ? hull.getBounds().y - ARROW_GAP
			: tile == null ? 0 : tile.getBounds().y + tile.getBounds().height / 2;
		if (x != 0 && y != 0)
		{
			GuideArrow.drawWorldArrow(graphics, GUIDE, x, y);
		}
	}

	private void drawArrowOverObject(Graphics2D graphics, GameObject object)
	{
		Polygon tile = tileOf(object.getLocalLocation());
		if (tile != null)
		{
			Rectangle bounds = tile.getBounds();
			GuideArrow.drawWorldArrow(graphics, GUIDE, bounds.x + bounds.width / 2,
				bounds.y + bounds.height / 2);
			return;
		}
		drawArrowAbove(graphics, object.getConvexHull());
	}

	private Polygon tileOf(LocalPoint local)
	{
		return local == null ? null : Perspective.getCanvasTilePoly(client, local, 30);
	}

	private void drawTileGuide(Graphics2D graphics, LocalPoint local, boolean showArrow)
	{
		Polygon tile = tileOf(local);
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

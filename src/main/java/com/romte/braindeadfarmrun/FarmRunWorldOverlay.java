package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
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

		if (runSession.currentTargetIsFarmer())
		{
			NPC farmer = patchTracker.farmerFor(patch);
			if (farmer != null)
			{
				outlineRenderer.drawOutline(farmer, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
				drawArrowAbove(graphics, farmer.getConvexHull());
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
				drawArrowAbove(graphics, hull);
			}
			else
			{
				drawArrowAtTile(graphics, object.getLocalLocation());
			}
			return null;
		}

		WorldPoint point = new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
		LocalPoint local = LocalPoint.fromWorld(client.getTopLevelWorldView(), point);
		drawArrowAtTile(graphics, local);
		return null;
	}

	private void drawArrowAtTile(Graphics2D graphics, LocalPoint local)
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
		Rectangle bounds = tile.getBounds();
		drawArrow(graphics, bounds.x + bounds.width / 2, bounds.y + bounds.height / 2);
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
		drawArrow(graphics, bounds.x + bounds.width / 2, bounds.y - ARROW_GAP);
	}

	private static void drawArrow(Graphics2D graphics, int x, int tipY)
	{
		Line2D.Double line = new Line2D.Double(x, tipY - 13, x, tipY);
		Stroke previousStroke = graphics.getStroke();
		Color previousColor = graphics.getColor();

		graphics.setColor(Color.BLACK);
		graphics.setStroke(new BasicStroke(9));
		graphics.draw(line);
		drawArrowHead(graphics, line, 4, 5);

		graphics.setColor(GUIDE);
		graphics.setStroke(new BasicStroke(6));
		graphics.draw(line);
		drawArrowHead(graphics, line, 2, 3);

		graphics.setStroke(previousStroke);
		graphics.setColor(previousColor);
	}

	private static void drawArrowHead(Graphics2D graphics, Line2D.Double line, int height, int width)
	{
		Polygon head = new Polygon();
		head.addPoint(0, 6 + height);
		head.addPoint(-6 - width, -1 - height);
		head.addPoint(6 + width, -1 - height);

		AffineTransform transform = new AffineTransform();
		transform.translate(line.x2, line.y2);
		double angle = Math.atan2(line.y2 - line.y1, line.x2 - line.x1);
		transform.rotate(angle - Math.PI / 2);

		Graphics2D copy = (Graphics2D) graphics.create();
		copy.setTransform(transform);
		copy.fill(head);
		copy.dispose();
	}
}

package com.romte.braindeadfarmrun;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Player;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;

/**
 * Arrow drawing shared by the world and minimap guidance overlays. The shape, outline and
 * blinking behaviour follow the same pattern used by Quest Helper.
 */
final class GuideArrow
{
	private GuideArrow()
	{
	}

	static void drawWorldArrow(Graphics2D graphics, Color color, int x, int tipY)
	{
		Line2D.Double line = new Line2D.Double(x, tipY - 13, x, tipY);
		drawArrow(graphics, line, color, 9, 4, 5);
	}

	static void drawMinimapArrow(Graphics2D graphics, Line2D.Double line, Color color)
	{
		drawArrow(graphics, line, color, 6, 2, 2);
	}

	static void renderMinimapArrow(Graphics2D graphics, Client client, WorldPoint target,
		Color color)
	{
		Player player = client.getLocalPlayer();
		if (player == null || target == null)
		{
			return;
		}
		WorldPoint start = player.getWorldLocation();
		if (start == null)
		{
			return;
		}

		int maxDistance = maxMinimapDrawDistance(client);
		if (start.distanceTo(target) >= maxDistance)
		{
			drawFarMinimapArrow(graphics, client, start, target, color);
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		LocalPoint local = LocalPoint.fromWorld(worldView, target);
		if (local == null)
		{
			drawFarMinimapArrow(graphics, client, start, target, color);
			return;
		}

		Point point = Perspective.localToMinimap(client, local);
		if (point == null)
		{
			drawFarMinimapArrow(graphics, client, start, target, color);
			return;
		}

		Line2D.Double line = new Line2D.Double(point.getX(), point.getY() - 18,
			point.getX(), point.getY() - 8);
		drawMinimapArrow(graphics, line, color);
	}

	private static void drawFarMinimapArrow(Graphics2D graphics, Client client, WorldPoint start,
		WorldPoint destination, Color color)
	{
		Player player = client.getLocalPlayer();
		if (player == null)
		{
			return;
		}
		Point playerPoint = player.getMinimapLocation();
		Point destinationPoint = minimapPoint(client, start, destination);
		if (playerPoint == null || destinationPoint == null)
		{
			return;
		}

		double xDiff = playerPoint.getX() - destinationPoint.getX();
		double yDiff = destinationPoint.getY() - playerPoint.getY();
		double angle = Math.atan2(yDiff, xDiff);
		int startX = (int) (playerPoint.getX() - Math.cos(angle) * 55);
		int startY = (int) (playerPoint.getY() + Math.sin(angle) * 55);
		int endX = (int) (playerPoint.getX() - Math.cos(angle) * 65);
		int endY = (int) (playerPoint.getY() + Math.sin(angle) * 65);
		drawMinimapArrow(graphics, new Line2D.Double(startX, startY, endX, endY), color);
	}

	private static Point minimapPoint(Client client, WorldPoint start, WorldPoint destination)
	{
		var worldMap = client.getWorldMap();
		if (worldMap == null || worldMap.getWorldMapData() == null)
		{
			return null;
		}
		var data = worldMap.getWorldMapData();
		if (data.surfaceContainsPosition(start.getX(), start.getY())
			!= data.surfaceContainsPosition(destination.getX(), destination.getY()))
		{
			return null;
		}

		int x = destination.getX() - start.getX();
		int y = destination.getY() - start.getY();
		float maxDistance = Math.max(Math.abs(x), Math.abs(y));
		if (maxDistance == 0)
		{
			return null;
		}
		x = (int) (x * 100 / maxDistance);
		y = (int) (y * 100 / maxDistance);

		Widget minimap;
		if (client.isResized())
		{
			minimap = client.getVarbitValue(VarbitID.RESIZABLE_STONE_ARRANGEMENT) == 1
				? client.getWidget(InterfaceID.ToplevelPreEoc.MINIMAP)
				: client.getWidget(InterfaceID.ToplevelOsrsStretch.MINIMAP);
		}
		else
		{
			minimap = client.getWidget(InterfaceID.Toplevel.MINIMAP);
		}
		if (minimap == null)
		{
			return null;
		}

		int cameraYaw = client.getCameraYawTarget() & 0x3FFF;
		int sin = Perspective.SINE14[cameraYaw];
		int cos = Perspective.COSINE14[cameraYaw];
		int xx = y * sin + cos * x >> 16;
		int yy = sin * x - y * cos >> 16;
		Point location = minimap.getCanvasLocation();
		return new Point(location.getX() + xx + minimap.getWidth() / 2,
			minimap.getHeight() / 2 + location.getY() + yy);
	}

	private static int maxMinimapDrawDistance(Client client)
	{
		double zoom = client.getMinimapZoom();
		return zoom > 0.0 ? (int) (64.0 / zoom) : 16;
	}

	private static void drawArrow(Graphics2D graphics, Line2D.Double line, Color color,
		int width, int headHeight, int headWidth)
	{
		Stroke previousStroke = graphics.getStroke();
		Color previousColor = graphics.getColor();

		graphics.setColor(Color.BLACK);
		graphics.setStroke(new BasicStroke(width));
		graphics.draw(line);
		drawArrowHead(graphics, line, headHeight, headWidth);

		graphics.setColor(color);
		graphics.setStroke(new BasicStroke(width - 3));
		graphics.draw(line);
		drawArrowHead(graphics, line, headHeight - 2, headWidth - 2);

		graphics.setStroke(previousStroke);
		graphics.setColor(previousColor);
	}

	private static void drawArrowHead(Graphics2D graphics, Line2D.Double line, int height,
		int width)
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

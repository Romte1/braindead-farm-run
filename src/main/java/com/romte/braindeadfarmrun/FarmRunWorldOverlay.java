package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.bank.SupplyPlanner;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.game.ItemManager;
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
	private final SupplyPlanner supplyPlanner;
	private final ItemManager itemManager;

	@Inject
	FarmRunWorldOverlay(Client client, RunSession runSession, PatchTracker patchTracker,
		ModelOutlineRenderer outlineRenderer, SupplyPlanner supplyPlanner, ItemManager itemManager)
	{
		this.client = client;
		this.runSession = runSession;
		this.patchTracker = patchTracker;
		this.outlineRenderer = outlineRenderer;
		this.supplyPlanner = supplyPlanner;
		this.itemManager = itemManager;
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
		if (runSession.isSeaweedApproach())
		{
			WorldPoint access = runSession.currentNavigationPoint();
			GameObject rowboat = access == null ? null : findRowboat(access);
			if (rowboat != null)
			{
				outlineRenderer.drawOutline(rowboat, OUTLINE_THICKNESS, GUIDE, OUTLINE_FEATHERING);
				if (showArrow)
				{
					drawArrowOverObject(graphics, rowboat);
				}
			}
			else
			{
				LocalPoint local = access == null ? null
					: LocalPoint.fromWorld(client.getTopLevelWorldView(), access);
				drawTileGuide(graphics, local, showArrow);
			}
			return null;
		}
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
		}

		WorldPoint target = runSession.currentNavigationPoint();
		LocalPoint local = target == null ? null
			: LocalPoint.fromWorld(client.getTopLevelWorldView(), target);
		drawActionItem(graphics, null, local);
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

	/** The bank-island rowboat has no farming varbit, so find it around the dive point. */
	private GameObject findRowboat(WorldPoint point)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getScene() == null)
		{
			return null;
		}
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene.getTiles();
		int plane = point.getPlane();
		int baseX = worldView.getBaseX();
		int baseY = worldView.getBaseY();
		int sceneX = point.getX() - baseX;
		int sceneY = point.getY() - baseY;
		if (plane < 0 || plane >= tiles.length)
		{
			return null;
		}
		GameObject best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (int dx = -8; dx <= 8; dx++)
		{
			for (int dy = -8; dy <= 8; dy++)
			{
				int x = sceneX + dx;
				int y = sceneY + dy;
				if (x < 0 || y < 0 || x >= tiles[plane].length
					|| y >= tiles[plane][x].length || tiles[plane][x][y] == null)
				{
					continue;
				}
				GameObject[] objects = tiles[plane][x][y].getGameObjects();
				if (objects == null)
				{
					continue;
				}
				for (GameObject object : objects)
				{
					if (object == null || object.getId() != ObjectID.FOSSIL_ROWBOAT_DIVING)
					{
						continue;
					}
					WorldPoint location = object.getWorldLocation();
					int distance = location == null ? Integer.MAX_VALUE
						: Math.max(Math.abs(location.getX() - point.getX()),
							Math.abs(location.getY() - point.getY()));
					if (distance < bestDistance)
					{
						best = object;
						bestDistance = distance;
					}
				}
			}
		}
		return best;
	}

	private void drawActionItem(Graphics2D graphics, Shape shape, LocalPoint local)
	{
		String itemName = runSession.currentItemName();
		if (itemName == null)
		{
			return;
		}
		int itemId = itemIdForName(itemName);
		if (itemId <= 0)
		{
			return;
		}
		BufferedImage image = itemManager.getImage(itemId, 1, false);
		if (image == null)
		{
			return;
		}

		Rectangle bounds = shape == null ? null : shape.getBounds();
		if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
		{
			Polygon tile = tileOf(local);
			bounds = tile == null ? null : tile.getBounds();
		}
		if (bounds == null)
		{
			return;
		}
		graphics.drawImage(image, bounds.x + bounds.width / 2 - image.getWidth() / 2,
			bounds.y + bounds.height / 2 - image.getHeight() / 2, null);
	}

	private int itemIdForName(String name)
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null && inventory.getItems() != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item.getId() <= 0)
				{
					continue;
				}
				int canonical = itemManager.canonicalize(item.getId());
				if (name.equalsIgnoreCase(itemManager.getItemComposition(canonical).getName()))
				{
					return canonical;
				}
			}
		}
		int[] ids = supplyPlanner.itemIdsFor(name);
		return ids.length == 0 ? -1 : ids[0];
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

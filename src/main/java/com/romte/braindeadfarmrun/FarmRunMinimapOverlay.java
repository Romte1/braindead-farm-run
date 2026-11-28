package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.runtime.PatchTracker;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

/** Quest Helper-style blinking arrow on the minimap for the current action target. */
@Singleton
public class FarmRunMinimapOverlay extends Overlay
{
	private static final Color GUIDE = new Color(51, 187, 51);

	private final Client client;
	private final RunSession runSession;
	private final PatchTracker patchTracker;

	@Inject
	FarmRunMinimapOverlay(Client client, RunSession runSession, PatchTracker patchTracker)
	{
		this.client = client;
		this.runSession = runSession;
		this.patchTracker = patchTracker;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ALWAYS_ON_TOP);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!runSession.isActive() || client.getTickCount() % 4 >= 2)
		{
			return null;
		}
		PatchDefinition patch = runSession.currentPatch();
		if (patch == null)
		{
			return null;
		}
		GuideArrow.renderMinimapArrow(graphics, client, targetOf(patch), GUIDE);
		return null;
	}

	private WorldPoint targetOf(PatchDefinition patch)
	{
		if (runSession.isSeaweedApproach())
		{
			return runSession.currentNavigationPoint();
		}
		if (runSession.currentTargetIsFarmer())
		{
			NPC farmer = patchTracker.farmerFor(patch);
			if (farmer != null && farmer.getWorldLocation() != null)
			{
				return farmer.getWorldLocation();
			}
		}
		GameObject object = patchTracker.objectFor(patch);
		if (object != null && object.getWorldLocation() != null)
		{
			return object.getWorldLocation();
		}
		return new WorldPoint(patch.getX(), patch.getY(), patch.getPlane());
	}
}

package com.romte.braindeadfarmrun;

import java.awt.BorderLayout;
import java.awt.Color;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel. The timer list and run planner will be added in the next phases; for now this
 * makes the plugin visible and gives the navigation button something to open.
 */
class BraindeadFarmRunPanel extends PluginPanel
{
	BraindeadFarmRunPanel()
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Braindead Farm Run");
		title.setHorizontalAlignment(SwingConstants.CENTER);
		title.setForeground(Color.WHITE);
		add(title, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setOpaque(false);
		body.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
		body.add(label("Allotments, herbs, trees and fruit trees."));
		body.add(label("Timers and the run planner are coming next."));
		add(body, BorderLayout.CENTER);
	}

	private static JLabel label(String text)
	{
		JLabel label = new JLabel("<html><body style='width: 180px'>" + text + "</body></html>");
		label.setForeground(Color.LIGHT_GRAY);
		label.setBorder(BorderFactory.createEmptyBorder(0, 0, 8, 0));
		return label;
	}
}

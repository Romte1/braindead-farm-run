package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel: pick the run types, press START RUN, and the session sends each stop to
 * Shortest Path. The bank tab only exists while a run is active.
 */
class BraindeadFarmRunPanel extends PluginPanel
{
	private static final Color START = new Color(56, 142, 60);
	private static final Color START_BORDER = new Color(27, 94, 32);
	private static final Color STOP = new Color(183, 28, 28);
	private static final Color STOP_BORDER = new Color(110, 15, 15);
	private static final Color TEXT = new Color(220, 220, 220);
	private static final Color MUTED = new Color(160, 160, 160);
	private static final Color LINE = new Color(70, 70, 70);

	private final RunSession session;
	private final ConfigManager configManager;
	private final JButton startButton = new JButton("START RUN");
	private final JCheckBox herbs = checkbox("Herbs", true);
	private final JCheckBox trees = checkbox("Trees", true);
	private final JCheckBox fruitTrees = checkbox("Fruit trees", true);
	private final JCheckBox allotments = checkbox("Allotments", true);
	private final JCheckBox bottomless = checkbox("Bottomless compost bucket", true);
	private final JLabel status = new JLabel("Select runs and press START RUN.");
	private boolean wasActive;

	BraindeadFarmRunPanel(RunSession session, BraindeadFarmRunConfig config,
		ConfigManager configManager)
	{
		super(false);
		this.session = session;
		this.configManager = configManager;
		bottomless.setSelected(config.bottomlessCompostBucket());

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JLabel title = new JLabel("Braindead Farm Run");
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		add(title);

		JLabel subtitle = new JLabel("Farm run planner");
		subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
		subtitle.setForeground(MUTED);
		subtitle.setFont(subtitle.getFont().deriveFont(11f));
		add(subtitle);
		add(Box.createVerticalStrut(14));

		styleButton();
		startButton.addActionListener(event -> toggle());
		add(startButton);
		add(Box.createVerticalStrut(14));

		add(section("Runs"));
		JPanel runs = new JPanel();
		runs.setLayout(new BoxLayout(runs, BoxLayout.Y_AXIS));
		runs.setOpaque(false);
		runs.setAlignmentX(Component.LEFT_ALIGNMENT);
		runs.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));
		runs.add(herbs);
		runs.add(trees);
		runs.add(fruitTrees);
		runs.add(allotments);
		add(runs);

		herbs.addActionListener(event -> updateSelection());
		trees.addActionListener(event -> updateSelection());
		fruitTrees.addActionListener(event -> updateSelection());
		allotments.addActionListener(event -> updateSelection());
		updateSelection();

		add(Box.createVerticalStrut(10));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setForeground(TEXT);
		status.setBorder(BorderFactory.createEmptyBorder(0, 2, 0, 0));
		add(status);

		add(Box.createVerticalStrut(10));
		add(separator());
		add(section("Settings"));
		bottomless.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottomless.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		bottomless.setToolTipText(
			"Use the filled bottomless bucket. If off, the tab picks the best compost in your bank.");
		bottomless.addActionListener(event -> configManager.setConfiguration(
			BraindeadFarmRunConfig.GROUP, "bottomlessCompostBucket", bottomless.isSelected()));
		add(bottomless);

		JLabel hint = new JLabel(
			"<html><body style='width: 170px'>The bank tab appears while a run is active.</body></html>");
		hint.setForeground(MUTED);
		hint.setAlignmentX(Component.LEFT_ALIGNMENT);
		hint.setBorder(BorderFactory.createEmptyBorder(6, 8, 0, 0));
		add(hint);

		refresh();
	}

	private static JCheckBox checkbox(String text, boolean selected)
	{
		JCheckBox box = new JCheckBox(text, selected);
		box.setOpaque(false);
		box.setForeground(TEXT);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		return box;
	}

	private static JLabel section(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(MUTED);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(2, 2, 2, 0));
		return label;
	}

	private static JSeparator separator()
	{
		JSeparator separator = new JSeparator();
		separator.setForeground(LINE);
		separator.setBackground(LINE);
		separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
		separator.setAlignmentX(Component.LEFT_ALIGNMENT);
		return separator;
	}

	private void styleButton()
	{
		startButton.setOpaque(true);
		startButton.setContentAreaFilled(true);
		startButton.setFocusPainted(false);
		startButton.setForeground(Color.WHITE);
		startButton.setBackground(START);
		startButton.setFont(startButton.getFont().deriveFont(Font.BOLD, 14f));
		startButton.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(START_BORDER),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		startButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		startButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private void toggle()
	{
		if (session.isActive())
		{
			session.stop();
			return;
		}

		Set<PatchType> types = selectedTypes();
		if (types.isEmpty())
		{
			status.setText("Select at least one run type.");
			return;
		}
		status.setText("Building route...");
		session.start(types);
	}

	private void updateSelection()
	{
		session.setSelectedTypes(selectedTypes());
	}

	private Set<PatchType> selectedTypes()
	{
		Set<PatchType> types = EnumSet.noneOf(PatchType.class);
		if (herbs.isSelected())
		{
			types.add(PatchType.HERB);
		}
		if (trees.isSelected())
		{
			types.add(PatchType.TREE);
		}
		if (fruitTrees.isSelected())
		{
			types.add(PatchType.FRUIT_TREE);
		}
		if (allotments.isSelected())
		{
			types.add(PatchType.ALLOTMENT);
		}
		return types;
	}

	void refresh()
	{
		SwingUtilities.invokeLater(() ->
		{
			boolean active = session.isActive();
			startButton.setText(active ? "STOP RUN" : "START RUN");
			startButton.setBackground(active ? STOP : START);
			startButton.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(active ? STOP_BORDER : START_BORDER),
				BorderFactory.createEmptyBorder(6, 12, 6, 12)));
			if (active)
			{
				String next = session.nextStopName();
				status.setText(next == null ? "Route active."
					: "<html>Next: " + next + "<br><font color='#a0a0a0'>"
						+ session.remainingStops() + " stops left</font></html>");
			}
			else if (wasActive)
			{
				status.setText("Run finished. Select runs and press START RUN.");
			}
			wasActive = active;
		});
	}

	void setBottomless(boolean selected)
	{
		SwingUtilities.invokeLater(() -> bottomless.setSelected(selected));
	}
}

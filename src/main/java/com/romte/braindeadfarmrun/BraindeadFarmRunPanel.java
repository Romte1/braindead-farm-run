package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Sidebar panel. The setup view picks the run types; START RUN switches to the route view,
 * which lists the stop order and the transports Shortest Path reports for each leg.
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
	private final CardLayout cards = new CardLayout();
	private final JPanel root = new JPanel(cards);
	private final JPanel setup = new JPanel();
	private final JPanel run = new JPanel(new BorderLayout());
	private final JButton startButton = new JButton("START RUN");
	private final JButton stopButton = new JButton("STOP RUN");
	private final JCheckBox herbs = checkbox("Herbs", true);
	private final JCheckBox trees = checkbox("Trees", true);
	private final JCheckBox fruitTrees = checkbox("Fruit trees", true);
	private final JCheckBox allotments = checkbox("Allotments", true);
	private final JCheckBox bottomless = checkbox("Bottomless compost bucket", true);
	private final JLabel status = new JLabel("Select runs and press START RUN.");
	private final JTextArea runText = new JTextArea();
	private boolean wasActive;

	BraindeadFarmRunPanel(RunSession session, BraindeadFarmRunConfig config,
		ConfigManager configManager)
	{
		super(false);
		this.session = session;
		this.configManager = configManager;
		bottomless.setSelected(config.bottomlessCompostBucket());

		setLayout(new BorderLayout());
		root.setOpaque(false);
		buildSetup();
		buildRun();
		root.add(setup, "setup");
		root.add(run, "run");
		add(root, BorderLayout.CENTER);

		refresh();
	}

	private void buildSetup()
	{
		setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
		setup.setOpaque(false);
		setup.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JLabel title = new JLabel("Braindead Farm Run");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		setup.add(title);

		JLabel subtitle = new JLabel("Farm run planner");
		subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
		subtitle.setForeground(MUTED);
		subtitle.setFont(subtitle.getFont().deriveFont(11f));
		setup.add(subtitle);
		setup.add(Box.createVerticalStrut(14));

		styleButton(startButton, START, START_BORDER);
		startButton.addActionListener(event -> toggle());
		setup.add(startButton);
		setup.add(Box.createVerticalStrut(14));

		setup.add(section("Runs"));
		JPanel runs = new JPanel();
		runs.setLayout(new BoxLayout(runs, BoxLayout.Y_AXIS));
		runs.setOpaque(false);
		runs.setAlignmentX(Component.LEFT_ALIGNMENT);
		runs.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));
		runs.add(herbs);
		runs.add(trees);
		runs.add(fruitTrees);
		runs.add(allotments);
		setup.add(runs);

		herbs.addActionListener(event -> updateSelection());
		trees.addActionListener(event -> updateSelection());
		fruitTrees.addActionListener(event -> updateSelection());
		allotments.addActionListener(event -> updateSelection());
		updateSelection();

		setup.add(Box.createVerticalStrut(10));
		status.setAlignmentX(Component.LEFT_ALIGNMENT);
		status.setForeground(TEXT);
		setup.add(status);

		setup.add(Box.createVerticalStrut(10));
		setup.add(separator());
		setup.add(section("Settings"));
		bottomless.setAlignmentX(Component.LEFT_ALIGNMENT);
		bottomless.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		bottomless.setToolTipText(
			"Use the filled bottomless bucket. If off, the tab picks the best compost in your bank.");
		bottomless.addActionListener(event -> configManager.setConfiguration(
			BraindeadFarmRunConfig.GROUP, "bottomlessCompostBucket", bottomless.isSelected()));
		setup.add(bottomless);
	}

	private void buildRun()
	{
		run.setOpaque(false);
		run.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(false);
		header.setAlignmentX(Component.LEFT_ALIGNMENT);

		JLabel title = new JLabel("Run in progress");
		title.setAlignmentX(Component.LEFT_ALIGNMENT);
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		header.add(title);
		header.add(Box.createVerticalStrut(10));

		styleButton(stopButton, STOP, STOP_BORDER);
		stopButton.addActionListener(event -> session.stop());
		header.add(stopButton);
		run.add(header, BorderLayout.NORTH);

		runText.setEditable(false);
		runText.setOpaque(false);
		runText.setForeground(TEXT);
		runText.setLineWrap(true);
		runText.setWrapStyleWord(true);
		runText.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));

		JScrollPane scroll = new JScrollPane(runText);
		scroll.setBorder(null);
		scroll.setOpaque(false);
		scroll.getViewport().setOpaque(false);
		run.add(scroll, BorderLayout.CENTER);
	}

	private static void styleButton(JButton button, Color background, Color border)
	{
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setFocusPainted(false);
		button.setForeground(Color.WHITE);
		button.setBackground(background);
		button.setFont(button.getFont().deriveFont(Font.BOLD, 14f));
		button.setHorizontalAlignment(SwingConstants.LEFT);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(border),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		button.setMaximumSize(new Dimension(180, 34));
		button.setAlignmentX(Component.LEFT_ALIGNMENT);
		button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
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
		label.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
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
			if (active != wasActive)
			{
				cards.show(root, active ? "run" : "setup");
				if (!active)
				{
					status.setText("Run finished. Select runs and press START RUN.");
				}
			}
			if (active)
			{
				updateRunText();
			}
			wasActive = active;
		});
	}

	private void updateRunText()
	{
		List<PatchDefinition> stops = session.stops();
		int current = session.currentIndex();
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < stops.size(); i++)
		{
			PatchDefinition stop = stops.get(i);
			String prefix = i == current ? "\u25B6 " : i < current ? "\u2713 " : "  ";
			text.append(prefix).append(i + 1).append(". ").append(stop.getDisplayName()).append('\n');

			List<String> teleports = session.teleportsFor(i);
			if (teleports.isEmpty())
			{
				text.append("     Shortest Path: calculating...\n");
			}
			else
			{
				for (String line : teleports)
				{
					text.append("     ").append(line).append('\n');
				}
			}
			text.append('\n');
		}

		String value = text.toString();
		if (!value.equals(runText.getText()))
		{
			runText.setText(value);
			runText.setCaretPosition(0);
		}
	}

	void setBottomless(boolean selected)
	{
		SwingUtilities.invokeLater(() -> bottomless.setSelected(selected));
	}
}

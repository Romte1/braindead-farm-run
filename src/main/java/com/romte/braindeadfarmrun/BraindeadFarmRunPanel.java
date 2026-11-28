package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.Crop;
import com.romte.braindeadfarmrun.domain.patch.PatchDefinition;
import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.domain.patch.SeedSelection;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
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
	private final BraindeadFarmRunConfig config;
	private final ConfigManager configManager;
	private final BooleanSupplier bankKnown;
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
	private JComboBox<SeedOption> herbSeeds;
	private JComboBox<SeedOption> treeSeeds;
	private JComboBox<SeedOption> fruitTreeSeeds;
	private JComboBox<SeedOption> allotmentSeeds;
	private final JLabel status = new JLabel();
	private Font bankWarningBase;
	private float bankWarningSize = 13f;
	private final JLabel bankWarning = new JLabel("To start, open your bank first.");
	private final JTextArea runText = new JTextArea();
	private final Timer preparingTimer = new Timer(300, event -> animatePreparing());
	private int preparingDots;
	private boolean wasRun;

	BraindeadFarmRunPanel(RunSession session, BraindeadFarmRunConfig config,
		ConfigManager configManager, BooleanSupplier bankKnown)
	{
		super(false);
		this.session = session;
		this.config = config;
		this.configManager = configManager;
		this.bankKnown = bankKnown;
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
		setup.setLayout(new GridBagLayout());
		setup.setOpaque(false);
		setup.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

		GridBagConstraints c = new GridBagConstraints();
		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.WEST;

		JLabel title = new JLabel("Braindead Farm Run");
		title.setForeground(Color.WHITE);
		title.setFont(title.getFont().deriveFont(Font.BOLD, 15f));
		addSetup(c, title, 0, 0, 0, 0);

		JLabel subtitle = new JLabel("Farm run planner");
		subtitle.setForeground(MUTED);
		subtitle.setFont(subtitle.getFont().deriveFont(13f));
		addSetup(c, subtitle, 0, 0, 0, 0);

		styleButton(startButton, START, START_BORDER);
		startButton.addActionListener(event -> toggle());
		addSetup(c, centeredRow(startButton), 14, 0, 0, 0);

		bankWarning.setForeground(new Color(220, 60, 60));
		bankWarningBase = bankWarning.getFont().deriveFont(Font.BOLD);
		bankWarning.setFont(bankWarningBase.deriveFont(bankWarningSize));
		bankWarning.addComponentListener(new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent event)
			{
				fitBankWarning();
			}
		});
		addSetup(c, bankWarning, 4, 0, 0, 0);

		addSetup(c, section("Runs"), 14, 0, 0, 0);
		JPanel runs = new JPanel();
		runs.setLayout(new BoxLayout(runs, BoxLayout.Y_AXIS));
		runs.setOpaque(false);
		runs.setBorder(BorderFactory.createEmptyBorder(2, 8, 2, 0));
		runs.add(herbs);
		runs.add(trees);
		runs.add(fruitTrees);
		runs.add(allotments);
		addSetup(c, runs, 0, 0, 0, 0);

		applyRunTypes(config.runTypes());
		herbs.addActionListener(event -> runTypesChanged());
		trees.addActionListener(event -> runTypesChanged());
		fruitTrees.addActionListener(event -> runTypesChanged());
		allotments.addActionListener(event -> runTypesChanged());
		updateSelection();

		status.setForeground(TEXT);
		status.setVisible(false);
		addSetup(c, status, 12, 0, 0, 0);
		addSetup(c, separator(), 12, 0, 0, 0);
		addSetup(c, section("Settings"), 8, 0, 0, 0);

		bottomless.setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 0));
		bottomless.setToolTipText(
			"Use the filled bottomless bucket. If off, the tab picks the best compost in your bank.");
		bottomless.addActionListener(event -> configManager.setConfiguration(
			BraindeadFarmRunConfig.GROUP, "bottomlessCompostBucket", bottomless.isSelected()));
		addSetup(c, bottomless, 0, 0, 0, 0);

		addSetup(c, separator(), 12, 0, 0, 0);
		addSetup(c, section("Custom Seeds"), 8, 0, 0, 0);

		herbSeeds = seedCombo(PatchType.HERB, config.herbSeed());
		treeSeeds = seedCombo(PatchType.TREE, config.treeSeed());
		fruitTreeSeeds = seedCombo(PatchType.FRUIT_TREE, config.fruitTreeSeed());
		allotmentSeeds = seedCombo(PatchType.ALLOTMENT, config.allotmentSeed());
		addSetup(c, seedRow(PatchType.HERB.getDisplayName(), herbSeeds), 6, 8, 0, 0);
		addSetup(c, seedRow(PatchType.TREE.getDisplayName(), treeSeeds), 6, 8, 0, 0);
		addSetup(c, seedRow(PatchType.FRUIT_TREE.getDisplayName(), fruitTreeSeeds), 6, 8, 0, 0);
		addSetup(c, seedRow(PatchType.ALLOTMENT.getDisplayName(), allotmentSeeds), 6, 8, 0, 0);

		herbSeeds.addActionListener(event -> saveSeed(
			BraindeadFarmRunConfig.HERB_SEED_KEY, herbSeeds));
		treeSeeds.addActionListener(event -> saveSeed(
			BraindeadFarmRunConfig.TREE_SEED_KEY, treeSeeds));
		fruitTreeSeeds.addActionListener(event -> saveSeed(
			BraindeadFarmRunConfig.FRUIT_TREE_SEED_KEY, fruitTreeSeeds));
		allotmentSeeds.addActionListener(event -> saveSeed(
			BraindeadFarmRunConfig.ALLOTMENT_SEED_KEY, allotmentSeeds));

		JPanel filler = new JPanel();
		filler.setOpaque(false);
		c.gridy++;
		c.weighty = 1;
		c.fill = GridBagConstraints.BOTH;
		setup.add(filler, c);
	}

	private void addSetup(GridBagConstraints c, Component component, int top, int left, int bottom, int right)
	{
		c.insets = new Insets(top, left, bottom, right);
		setup.add(component, c);
		c.gridy++;
	}

	private static JPanel centeredRow(JButton button)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		row.setOpaque(false);
		row.add(button);
		return row;
	}

	private void fitBankWarning()
	{
		int available = bankWarning.getWidth() - bankWarning.getInsets().left
			- bankWarning.getInsets().right;
		if (available <= 0)
		{
			return;
		}
		float size = 13f;
		while (size < 24f)
		{
			float next = size + 0.5f;
			if (bankWarning.getFontMetrics(bankWarningBase.deriveFont(next))
				.stringWidth(bankWarning.getText()) > available)
			{
				break;
			}
			size = next;
		}
		if (Math.abs(size - bankWarningSize) >= 0.25f)
		{
			bankWarningSize = size;
			bankWarning.setFont(bankWarningBase.deriveFont(size));
		}
	}

	private void setStatus(String text)
	{
		boolean visible = text != null && !text.isEmpty();
		status.setText(visible ? text : "");
		if (status.isVisible() != visible)
		{
			status.setVisible(visible);
			setup.revalidate();
			setup.repaint();
		}
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
		header.add(centeredRow(stopButton));
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
		button.setHorizontalAlignment(SwingConstants.CENTER);
		button.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(border),
			BorderFactory.createEmptyBorder(6, 12, 6, 12)));
		button.setPreferredSize(new Dimension(180, 34));
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

	private JComboBox<SeedOption> seedCombo(PatchType type, String selected)
	{
		JComboBox<SeedOption> combo = new JComboBox<>();
		combo.setOpaque(true);
		combo.setBackground(new Color(48, 48, 48));
		combo.setForeground(TEXT);
		combo.setFocusable(false);
		combo.setFont(combo.getFont().deriveFont(13f));
		combo.setMaximumRowCount(20);
		combo.setPreferredSize(new Dimension(160, 28));
		combo.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		combo.setToolTipText("Best uses the highest-level crop your Farming level allows.");
		combo.setRenderer(new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(JList<?> list, Object value, int index,
				boolean selected, boolean focus)
			{
				JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index,
					selected, focus);
				label.setBackground(selected ? new Color(76, 76, 76) : new Color(48, 48, 48));
				label.setForeground(TEXT);
				label.setBorder(BorderFactory.createEmptyBorder(1, 6, 1, 6));
				return label;
			}
		});
		combo.addItem(new SeedOption(SeedSelection.BEST, "Best (default)"));
		for (Crop crop : Crop.forType(type))
		{
			combo.addItem(new SeedOption(crop.name(), SeedSelection.label(crop)));
		}
		selectSeed(combo, selected);
		return combo;
	}

	private static void selectSeed(JComboBox<SeedOption> combo, String selected)
	{
		String value = selected == null || selected.isEmpty() ? SeedSelection.BEST : selected;
		for (int i = 0; i < combo.getItemCount(); i++)
		{
			if (combo.getItemAt(i).value.equals(value))
			{
				combo.setSelectedIndex(i);
				return;
			}
		}
		combo.setSelectedIndex(0);
	}

	private static JPanel seedRow(String labelText, JComboBox<SeedOption> combo)
	{
		JPanel row = new JPanel(new BorderLayout(0, 2));
		row.setOpaque(false);
		JLabel label = new JLabel(labelText);
		label.setForeground(TEXT);
		label.setFont(label.getFont().deriveFont(13f));
		row.add(label, BorderLayout.NORTH);
		row.add(combo, BorderLayout.CENTER);
		return row;
	}

	private void saveSeed(String key, JComboBox<SeedOption> combo)
	{
		SeedOption option = (SeedOption) combo.getSelectedItem();
		if (option != null)
		{
			configManager.setConfiguration(BraindeadFarmRunConfig.GROUP, key, option.value);
		}
	}

	private static JLabel section(String text)
	{
		JLabel label = new JLabel(text);
		label.setForeground(MUTED);
		label.setFont(label.getFont().deriveFont(Font.BOLD, 13f));
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
		if (!bankKnown.getAsBoolean())
		{
			return;
		}

		Set<PatchType> types = selectedTypes();
		if (types.isEmpty())
		{
			setStatus("Select at least one run type.");
			return;
		}
		setStatus("");
		startButton.setEnabled(false);
		setSetupEnabled(false);
		startPreparingAnimation();
		session.start(types);
	}

	private void runTypesChanged()
	{
		updateSelection();
		configManager.setConfiguration(BraindeadFarmRunConfig.GROUP,
			BraindeadFarmRunConfig.RUN_TYPES_KEY, runTypesValue(selectedTypes()));
	}

	private void updateSelection()
	{
		session.setSelectedTypes(selectedTypes());
	}

	private void applyRunTypes(String saved)
	{
		Set<PatchType> types = parseRunTypes(saved);
		herbs.setSelected(types.contains(PatchType.HERB));
		trees.setSelected(types.contains(PatchType.TREE));
		fruitTrees.setSelected(types.contains(PatchType.FRUIT_TREE));
		allotments.setSelected(types.contains(PatchType.ALLOTMENT));
	}

	private static Set<PatchType> parseRunTypes(String saved)
	{
		Set<PatchType> types = EnumSet.noneOf(PatchType.class);
		if (saved == null)
		{
			types.addAll(EnumSet.allOf(PatchType.class));
			return types;
		}
		for (String value : saved.split(","))
		{
			if (value.isBlank())
			{
				continue;
			}
			try
			{
				types.add(PatchType.valueOf(value.trim()));
			}
			catch (IllegalArgumentException ignored)
			{
				// Ignore values from an older or manually edited config.
			}
		}
		return types;
	}

	private static String runTypesValue(Set<PatchType> types)
	{
		StringBuilder value = new StringBuilder();
		for (PatchType type : types)
		{
			if (value.length() > 0)
			{
				value.append(',');
			}
			value.append(type.name());
		}
		return value.toString();
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
			boolean preparing = session.isPreparing();
			boolean known = bankKnown.getAsBoolean();
			bankWarning.setVisible(!known);
			boolean showRun = active && !preparing;
			if (showRun != wasRun)
			{
				cards.show(root, showRun ? "run" : "setup");
			}
			if (preparing)
			{
				setSetupEnabled(false);
				startPreparingAnimation();
			}
			else
			{
				setSetupEnabled(true);
				stopPreparingAnimation();
				startButton.setEnabled(known);
				startButton.setText("START RUN");
				if (!active)
				{
					setStatus("");
				}
			}
			if (active && !preparing)
			{
				updateRunText();
			}
			wasRun = showRun;
		});
	}

	private void setSetupEnabled(boolean enabled)
	{
		herbs.setEnabled(enabled);
		trees.setEnabled(enabled);
		fruitTrees.setEnabled(enabled);
		allotments.setEnabled(enabled);
		bottomless.setEnabled(enabled);
		herbSeeds.setEnabled(enabled);
		treeSeeds.setEnabled(enabled);
		fruitTreeSeeds.setEnabled(enabled);
		allotmentSeeds.setEnabled(enabled);
	}

	private void startPreparingAnimation()
	{
		if (!preparingTimer.isRunning())
		{
			preparingDots = 0;
			preparingTimer.start();
		}
	}

	private void stopPreparingAnimation()
	{
		if (preparingTimer.isRunning())
		{
			preparingTimer.stop();
		}
		preparingDots = 0;
	}

	private void animatePreparing()
	{
		preparingDots = (preparingDots + 1) % 4;
		String dots = ".".repeat(preparingDots);
		startButton.setText("CREATING ROUTE" + dots);
	}

	private void updateRunText()
	{
		List<PatchDefinition> stops = session.stops();
		int current = session.currentIndex();
		StringBuilder text = new StringBuilder();
		if (!session.routeReady())
		{
			text.append("Withdraw the bank tab supplies, then close the bank.\n\n");
		}
		for (int i = 0; i < stops.size(); i++)
		{
			PatchDefinition stop = stops.get(i);
			String prefix = i == current ? "\u25B6 " : i < current ? "\u2713 " : "  ";
			text.append(prefix).append(i + 1).append(". ").append(stop.getDisplayName()).append('\n');
			if (i == current)
			{
				for (String task : session.currentTasks())
				{
					text.append("     \u2022 ").append(task).append('\n');
				}
			}

			List<String> teleports = session.teleportsFor(i);
			if (!session.routeReady())
			{
				if (!teleports.isEmpty())
				{
					text.append("     ").append(teleports.get(0)).append('\n');
				}
			}
			else if (!session.hasShortestPathResult(i))
			{
				text.append("     Shortest Path: calculating...\n");
			}
			else if (!session.reachedShortestPath(i))
			{
				text.append("     No route found\n");
			}
			else if (teleports.isEmpty())
			{
				text.append("     Walk / no teleport\n");
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

	private static final class SeedOption
	{
		private final String value;
		private final String label;

		private SeedOption(String value, String label)
		{
			this.value = value;
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}
}

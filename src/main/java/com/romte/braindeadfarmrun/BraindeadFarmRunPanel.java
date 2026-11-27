package com.romte.braindeadfarmrun;

import com.romte.braindeadfarmrun.domain.patch.PatchType;
import com.romte.braindeadfarmrun.runtime.RunSession;
import java.awt.Color;
import java.awt.Component;
import java.util.EnumSet;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.PluginPanel;

/**
 * Minimal run panel: pick the run types, press START RUN, and the session sends each stop to
 * Shortest Path.
 */
class BraindeadFarmRunPanel extends PluginPanel
{
	private final RunSession session;
	private final JButton startButton = new JButton("START RUN");
	private final JCheckBox herbs = checkbox("Herbs");
	private final JCheckBox trees = checkbox("Trees");
	private final JCheckBox fruitTrees = checkbox("Fruit trees");
	private final JCheckBox allotments = checkbox("Allotments");
	private final JLabel status = new JLabel("Select runs and press START RUN.");
	private boolean wasActive;

	BraindeadFarmRunPanel(RunSession session)
	{
		super(false);
		this.session = session;
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JLabel title = new JLabel("Braindead Farm Run");
		title.setAlignmentX(Component.CENTER_ALIGNMENT);
		title.setForeground(Color.WHITE);
		add(title);
		add(Box.createVerticalStrut(12));

		startButton.setAlignmentX(Component.CENTER_ALIGNMENT);
		startButton.addActionListener(event -> toggle());
		add(startButton);
		add(Box.createVerticalStrut(12));

		add(herbs);
		add(trees);
		add(fruitTrees);
		add(allotments);
		herbs.addActionListener(event -> updateSelection());
		trees.addActionListener(event -> updateSelection());
		fruitTrees.addActionListener(event -> updateSelection());
		allotments.addActionListener(event -> updateSelection());
		updateSelection();
		add(Box.createVerticalStrut(12));

		status.setAlignmentX(Component.CENTER_ALIGNMENT);
		status.setForeground(Color.LIGHT_GRAY);
		add(status);

		refresh();
	}

	private static JCheckBox checkbox(String text)
	{
		JCheckBox box = new JCheckBox(text, true);
		box.setOpaque(false);
		box.setForeground(Color.LIGHT_GRAY);
		box.setAlignmentX(Component.LEFT_ALIGNMENT);
		return box;
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
			if (active)
			{
				String next = session.nextStopName();
				status.setText(next == null ? "Route active."
					: "<html>Next: " + next + "<br>" + session.remainingStops()
						+ " stops left</html>");
			}
			else if (wasActive)
			{
				status.setText("Run finished. Select runs and press START RUN.");
			}
			wasActive = active;
		});
	}
}

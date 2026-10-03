package com.attempttracker.ui;

import com.attempttracker.core.FishingSession;
import javax.swing.JButton;
import java.awt.Component;
import java.awt.Container;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JList;
import javax.swing.JToggleButton;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttemptTrackerPanelTest
{
	@Test public void focusedTwoTickPercentageIgnoresAdaptiveRangeAndFitsSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			FishingSession session = session("mixed", 100); session.measuredCatches = 10;
			session.adaptiveCatches = 50; session.adaptiveFailureUpper = 100; session.adaptiveTiming = true;
			session.twoTickCatches = 40; session.twoTickFailures = 10; session.twoTickTiming = true;
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {}); panel.refresh(Collections.singletonList(session), "Fishing", "");
			assertEquals("10", AttemptTrackerPanel.failures(session)); assertEquals("80.0%", AttemptTrackerPanel.rates(session));
			assertTrue(hasLabel(panel, "Inferred 2t: 40 of 100 catches")); assertFalse(hasLabel(panel, "Adaptive timing: possible range"));
			javax.swing.JLabel note = findLabel(panel, "Inferred 2t: 40 of 100 catches");
			assertTrue(note.getToolTipText().contains("50 qualified, inferred attempts")); assertTrue(note.getToolTipText().contains("Server rolls are not directly verified"));
			JList<?> list = find(panel, JList.class); AtomicInteger changes = countModelEvents(list);
			session.loggedMillis = 1000; session.fishingMillis = 1000; panel.refresh(Collections.singletonList(session), "Fishing", "");
			assertEquals(0, changes.get());
			session.twoTickFailures = 11; panel.refresh(Collections.singletonList(session), "Fishing", ""); assertEquals(1, changes.get());
			assertEquals("11", AttemptTrackerPanel.failures((FishingSession) list.getModel().getElementAt(0)));
			panel.setSize(225, 900); for (int i = 0; i < 3; i++) { layout(panel); } assertVisibleLabelsFit(panel);
		});
	}
	@Test public void adaptiveRateAndPossibleFailuresFitAtNormalSidebarWidth() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			FishingSession session = new FishingSession(); session.catches = session.adaptiveCatches = 30; session.adaptiveFailureUpper = 60; session.adaptiveTiming = true;
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {}); panel.refresh(Collections.singletonList(session), "Fishing", "2t interactions (rolls unverified)");
			assertEquals("0-60", AttemptTrackerPanel.failures(session)); assertEquals("33.3%-100.0%", AttemptTrackerPanel.rates(session));
			assertTrue(hasLabel(panel, "Adaptive timing: possible range"));
			panel.setSize(225, 900); for (int i = 0; i < 3; i++) { layout(panel); } assertVisibleLabelsFit(panel);
		});
	}
	@Test
	public void hiddenTickUpdatesLeaveSwingComponentsAloneAndReopenWithLatestData() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			panel.refresh(Collections.singletonList(session("one", 2)), "Fishing", "Tick 1");
			JList<?> list = find(panel, JList.class);
			AtomicInteger changes = countModelEvents(list);
			JTextArea details = find(panel, JTextArea.class);
			AtomicInteger edits = countDocumentEvents(details);
			panel.onDeactivate();
			for (int i = 3; i <= 200; i++)
			{
				panel.refresh(Collections.singletonList(session("one", i)), "Paused", "Tick " + i,
					new LureDisplay("5 per catch", "Detected automatically", "Live choice"));
			}
			panel.refreshLures(new LureDisplay("None", "No lures available", "No supply"));
			assertEquals(0, changes.get()); assertEquals(0, edits.get());
			assertTrue(hasLabel(panel, "2")); assertFalse(hasLabel(panel, "None"));
			panel.onActivate();
			assertTrue(hasLabel(panel, "200")); assertTrue(hasLabel(panel, "Paused"));
			assertTrue(hasLabel(panel, "None")); assertEquals(1, list.getModel().getSize());
		});
	}

	@Test
	public void clockTicksDoNotRebuildHistoryOrChangeSelection() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			FishingSession current = session("one", 7), older = session("older", 3);
			panel.refresh(Arrays.asList(current, older), "Fishing", "Tick 1");
			JList<?> list = find(panel, JList.class);
			AtomicInteger modelChanges = countModelEvents(list), selectionChanges = new AtomicInteger();
			list.addListSelectionListener(event -> selectionChanges.incrementAndGet());
			current.loggedMillis = 9000; current.fishingMillis = 6000; current.fishingTicks = 10;
			panel.refresh(Arrays.asList(current, older), "Fishing", "Tick 2");
			assertEquals(0, modelChanges.get()); assertEquals(0, selectionChanges.get());
			assertTrue(hasLabel(panel, "00:00:09")); assertTrue(hasLabel(panel, "00:00:06"));
			assertTrue(hasLabel(panel, "Fishing ticks: 10"));
			list.setSelectedIndex(1); list.setSelectedIndex(0);
			assertTrue(hasLabel(panel, "00:00:09"));
			current.catches++; current.measuredCatches++;
			panel.refresh(Arrays.asList(current, older), "Fishing", "Tick 3");
			assertEquals(1, modelChanges.get()); assertTrue(hasLabel(panel, "8"));
		});
	}

	@Test
	public void foldedDetailsAvoidDocumentEditsAndExpandedDetailsDoNotAutoScroll() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			JTextArea details = find(panel, JTextArea.class);
			AtomicInteger edits = countDocumentEvents(details);
			panel.refresh(Collections.singletonList(session("one", 1)), "Fishing", "Tick 1");
			panel.refresh(Collections.singletonList(session("one", 1)), "Fishing", "Tick 2");
			assertEquals(0, edits.get());
			findToggle(panel, "Details").doClick();
			assertEquals("Tick 2", details.getText());
			assertEquals(javax.swing.text.DefaultCaret.NEVER_UPDATE, ((javax.swing.text.DefaultCaret) details.getCaret()).getUpdatePolicy());
			int before = edits.get();
			panel.refresh(Collections.singletonList(session("one", 1)), "Fishing", "Tick 2");
			assertEquals(before, edits.get());
			panel.refresh(Collections.singletonList(session("one", 1)), "Fishing", "Tick 3");
			assertEquals("Tick 3", details.getText());
		});
	}

	private static AttemptTrackerPanel activePanel(Runnable reset, Runnable export)
	{
		AttemptTrackerPanel panel = new AttemptTrackerPanel(reset, export); panel.onActivate(); return panel;
	}
	private static AtomicInteger countModelEvents(JList<?> list)
	{
		AtomicInteger changes = new AtomicInteger();
		list.getModel().addListDataListener(new javax.swing.event.ListDataListener()
		{
			public void intervalAdded(javax.swing.event.ListDataEvent event) { changes.incrementAndGet(); }
			public void intervalRemoved(javax.swing.event.ListDataEvent event) { changes.incrementAndGet(); }
			public void contentsChanged(javax.swing.event.ListDataEvent event) { changes.incrementAndGet(); }
		});
		return changes;
	}
	private static AtomicInteger countDocumentEvents(JTextArea details)
	{
		AtomicInteger edits = new AtomicInteger();
		details.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			public void insertUpdate(javax.swing.event.DocumentEvent event) { edits.incrementAndGet(); }
			public void removeUpdate(javax.swing.event.DocumentEvent event) { edits.incrementAndGet(); }
			public void changedUpdate(javax.swing.event.DocumentEvent event) { edits.incrementAndGet(); }
		});
		return edits;
	}
	private static JToggleButton findToggle(Container root, String text)
	{
		for (Component component : root.getComponents())
		{
			if (component instanceof JToggleButton && text.equals(((JToggleButton) component).getText())) { return (JToggleButton) component; }
			if (component instanceof Container) { JToggleButton match = findToggle((Container) component, text); if (match != null) { return match; } }
		}
		return null;
	}
	@Test
	public void liveLureChangesKeepHistoricalSelectionAndFitSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			panel.refresh(Arrays.asList(session("current", 1), session("older", 20)), "Fishing", "", new LureDisplay("1 per catch", "Detected automatically", "Live choice"));
			JList<?> list = find(panel, JList.class); list.setSelectedIndex(1);
			for (String amount : new String[]{"3 per catch", "5 per catch", "None", "Unknown", "--"})
			{
				panel.refreshLures(new LureDisplay(amount, amount.equals("None") ? "No lures available" : "Restart harpooning for attempts", "Live choice"));
				assertTrue(hasLabel(panel, amount)); assertTrue(hasLabel(panel, "20"));
				assertEquals("older", ((FishingSession) list.getSelectedValue()).id);
				panel.setSize(225, 1000); for (int i = 0; i < 3; i++) { layout(panel); }
				assertVisibleLabelsFit(panel);
			}
		});
	}
	@Test
	public void partialSessionShowsRateAndMeasuredSample() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			FishingSession session = session("partial", 15); session.measuredCatches = 14;
			session.minimumFailures = session.maximumFailures = 5; session.variableTiming = true;
			assertEquals("73.7%", AttemptTrackerPanel.rates(session));
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			panel.refresh(Collections.singletonList(session), "Paused", "");
			assertTrue(hasLabel(panel, "73.7%")); assertTrue(hasLabel(panel, "Rate uses 14 of 15 catches"));
			panel.setSize(225, 1000); for (int i = 0; i < 3; i++) { layout(panel); }
			assertVisibleLabelsFit(panel);
		});
	}
	private static boolean hasLabel(Container root, String text)
	{
		for (Component component : root.getComponents())
		{
			if (component instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) component).getText())) { return true; }
			if (component instanceof Container && hasLabel((Container) component, text)) { return true; }
		}
		return false;
	}
	private static javax.swing.JLabel findLabel(Container root, String text)
	{
		for (Component component : root.getComponents())
		{
			if (component instanceof javax.swing.JLabel && text.equals(((javax.swing.JLabel) component).getText())) { return (javax.swing.JLabel) component; }
			if (component instanceof Container) { javax.swing.JLabel found = findLabel((Container) component, text); if (found != null) { return found; } }
		}
		return null;
	}
	@Test
	public void tickRefreshPreservesHistorySelectionAndResetOnlyRunsOnClick() throws Exception
	{
		AtomicInteger callbacks = new AtomicInteger();
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(callbacks::incrementAndGet, () -> {});
			panel.refresh(Arrays.asList(session("latest", 1), session("older", 2)), "Fishing", "Fishing");
			JList<?> list = find(panel, JList.class);
			list.setSelectedIndex(1);
			panel.refresh(Arrays.asList(session("new", 3), session("latest", 5), session("older", 2)), "Paused", "Paused");
			assertEquals("older", ((FishingSession) list.getSelectedValue()).id);
			JButton reset = find(panel, JButton.class);
			assertEquals("Reset session", reset.getText());
			assertEquals(0, callbacks.get());
			reset.doClick();
			assertEquals(1, callbacks.get());
		});
	}

	@Test
	public void followsLatestUntilTheUserSelectsHistory() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			panel.refresh(Collections.singletonList(session("one", 1)), "Fishing", "Fishing");
			panel.refresh(Arrays.asList(session("two", 2), session("one", 1)), "Fishing", "Cooking");
			JList<?> list = find(panel, JList.class);
			assertEquals("two", ((FishingSession) list.getSelectedValue()).id);
			list.setSelectedIndex(1);
			panel.refresh(Arrays.asList(session("three", 3), session("two", 2), session("one", 1)), "Fishing", "Mining");
			assertEquals("one", ((FishingSession) list.getSelectedValue()).id);
			list.setSelectedIndex(0);
			panel.refresh(Arrays.asList(session("four", 4), session("three", 3), session("two", 2), session("one", 1)), "Fishing", "Fishing");
			assertEquals("four", ((FishingSession) list.getSelectedValue()).id);
		});
	}

	@Test
	public void narrowSidebarKeepsMetricsReadable() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = activePanel(() -> {}, () -> {});
			panel.refresh(Collections.singletonList(session("one", 950)), "Fishing",
				"Fishing. Expected rate: 95.0%; within the 95% confidence interval.");
			panel.setSize(225, 1000);
			for (int i = 0; i < 3; i++)
			{
				layout(panel);
			}
			assertVisibleLabelsFit(panel);
			JList<?> list = find(panel, JList.class);
			assertTrue(list.getCellBounds(0, 0).height > 0);
		});
	}

	@Test
	public void refreshDispatchesFromGameThreadAndHandlesEmptyHistory() throws Exception
	{
		AtomicReference<AttemptTrackerPanel> reference = new AtomicReference<>();
		SwingUtilities.invokeAndWait(() -> reference.set(activePanel(() -> {}, () -> {})));
		reference.get().refresh(Collections.singletonList(session("one", 10)), "Fishing", "Fishing");
		SwingUtilities.invokeAndWait(() -> assertEquals(1, find(reference.get(), JList.class).getModel().getSize()));
		reference.get().refresh(Collections.emptyList(), "Fishing", "Waiting");
		SwingUtilities.invokeAndWait(() -> assertEquals(0, find(reference.get(), JList.class).getModel().getSize()));
	}

	private static FishingSession session(String id, long successes)
	{
		FishingSession session = new FishingSession(); session.id = id; session.catches = session.measuredCatches = successes;
        session.minimumFailures = session.maximumFailures = 1; return session;
	}

	private static <T extends Component> T find(Container root, Class<T> type)
	{
		for (Component component : root.getComponents())
		{
			if (type.isInstance(component))
			{
				return type.cast(component);
			}
			if (component instanceof Container)
			{
				T match = find((Container) component, type);
				if (match != null)
				{
					return match;
				}
			}
		}
		return null;
	}

	private static void layout(Container container)
	{
		container.doLayout();
		for (Component component : container.getComponents())
		{
			if (component instanceof Container)
			{
				layout((Container) component);
			}
		}
	}

    private static void assertVisibleLabelsFit(Container container)
    {
        for (Component component : container.getComponents())
        {
            if (!component.isVisible()) { continue; }
            if (component instanceof javax.swing.JLabel) { assertTrue(((javax.swing.JLabel)component).getText(), component.getWidth() >= component.getPreferredSize().width); }
            else if (component instanceof Container) { assertVisibleLabelsFit((Container) component); }
        }
    }
}

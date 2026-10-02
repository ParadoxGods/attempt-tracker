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
	@Test
	public void liveLureChangesKeepHistoricalSelectionAndFitSidebar() throws Exception
	{
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = new AttemptTrackerPanel(() -> {}, () -> {});
			panel.refresh(Arrays.asList(session("current", 1), session("older", 20)), "Fishing", "", new LureDisplay("1 per catch", "Detected automatically", "Live choice"));
			JList<?> list = find(panel, JList.class); list.setSelectedIndex(1);
			for (String amount : new String[]{"3 per catch", "5 per catch", "Unknown", "--"})
			{
				panel.refreshLures(new LureDisplay(amount, "Restart harpooning for attempts", "Live choice"));
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
			AttemptTrackerPanel panel = new AttemptTrackerPanel(() -> {}, () -> {});
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
	@Test
	public void tickRefreshPreservesHistorySelectionAndResetOnlyRunsOnClick() throws Exception
	{
		AtomicInteger callbacks = new AtomicInteger();
		SwingUtilities.invokeAndWait(() ->
		{
			AttemptTrackerPanel panel = new AttemptTrackerPanel(callbacks::incrementAndGet, () -> {});
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
			AttemptTrackerPanel panel = new AttemptTrackerPanel(() -> {}, () -> {});
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
			AttemptTrackerPanel panel = new AttemptTrackerPanel(() -> {}, () -> {});
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
		SwingUtilities.invokeAndWait(() -> reference.set(new AttemptTrackerPanel(() -> {}, () -> {})));
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

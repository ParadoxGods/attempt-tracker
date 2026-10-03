package com.attempttracker.ui;

import com.attempttracker.core.FishingSession;
import java.awt.*;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Objects;
import javax.swing.*;
import javax.swing.text.DefaultCaret;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.PluginPanel;

/** Stable manual sessions; three primary metrics with folded history and diagnostics. */
public final class AttemptTrackerPanel extends PluginPanel
{
	private static final Color MUTED = new Color(174, 180, 191);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM d, HH:mm", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	private final JLabel status = label("Ready", MUTED, 12);
	private final JLabel lureAmount = label("--", new Color(255, 199, 81), 14);
	private final JLabel lureNote = label("Log in to detect", MUTED, 10);
	private final JLabel success = label("0", new Color(110, 204, 151), 24);
	private final JLabel failure = label("0", new Color(230, 142, 142), 24);
	private final JLabel rate = label("--", Color.WHITE, 26);
	private final JLabel loggedTime = label("00:00:00", Color.WHITE, 17);
	private final JLabel fishingTime = label("00:00:00", Color.WHITE, 17);
	private final JLabel ticks = label("Fishing ticks: 0", MUTED, 11);
	private final JLabel rangeNote = label("", MUTED, 10);
	private final JTextArea details = new JTextArea();
	private final DefaultListModel<FishingSession> historyModel = new DefaultListModel<>();
	private final JList<FishingSession> history = new JList<>(historyModel);
	private boolean updating;
	private boolean followingCurrent = true;
	private boolean active;
	private List<FishingSession> latestSessions = Collections.emptyList();
	private String latestState = "Ready";
	private String latestDiagnostic = "";
	private LureDisplay latestLures = LureDisplay.waiting();
	private final JToggleButton detailsToggle = new JToggleButton("Details");

	public AttemptTrackerPanel(Runnable reset, Runnable export)
	{
		setLayout(new BorderLayout()); setBackground(ColorScheme.DARK_GRAY_COLOR); setBorder(BorderFactory.createEmptyBorder(14, 10, 14, 10));
		JPanel content = vertical(); content.add(label("Attempt Tracker", Color.WHITE, 19)); content.add(Box.createVerticalStrut(5));
		content.add(status); content.add(Box.createVerticalStrut(9));
		content.add(metric("Detected lures", lureAmount)); content.add(lureNote); content.add(Box.createVerticalStrut(12));
		JPanel card = vertical(); card.setOpaque(true); card.setBackground(ColorScheme.DARKER_GRAY_COLOR); card.setBorder(BorderFactory.createEmptyBorder(12, 10, 12, 10));
		card.add(metric("Catch success", success)); card.add(Box.createVerticalStrut(14)); card.add(metric("Catch fail", failure));
		card.add(Box.createVerticalStrut(14)); card.add(timer("Catch rate", rate)); card.add(Box.createVerticalStrut(9)); card.add(rangeNote);
		content.add(card); content.add(Box.createVerticalStrut(17));
		JPanel times = new JPanel(new GridLayout(1, 2, 8, 0)); times.setOpaque(false); times.setAlignmentX(Component.LEFT_ALIGNMENT); times.add(timer("Logged in", loggedTime)); times.add(timer("Fishing", fishingTime)); content.add(times);
		content.add(Box.createVerticalStrut(9)); content.add(ticks); content.add(Box.createVerticalStrut(17));
		JButton resetButton = button("Reset session", () -> { followingCurrent = true; reset.run(); }); resetButton.setBackground(new Color(40, 110, 92)); content.add(resetButton); content.add(Box.createVerticalStrut(16));
		JToggleButton historyToggle = new JToggleButton("Session history"); style(historyToggle); content.add(historyToggle);
		history.setBackground(ColorScheme.DARKER_GRAY_COLOR); history.setForeground(Color.WHITE); history.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); history.setFixedCellHeight(44); history.setFixedCellWidth(200);
		history.setCellRenderer((list, session, index, selected, focus) ->
		{
			JPanel row = vertical(); row.setOpaque(true); row.setBackground(selected ? ColorScheme.MEDIUM_GRAY_COLOR : ColorScheme.DARKER_GRAY_COLOR); row.setBorder(BorderFactory.createEmptyBorder(5, 6, 5, 6));
			row.add(label(index == 0 ? "Current session" : DATE.format(Instant.ofEpochMilli(session.startedAt)), Color.WHITE, 11));
			row.add(label(count(session.catches) + " caught / " + failures(session) + " failed", MUTED, 10)); return row;
		});
		JScrollPane historyScroll = new JScrollPane(history); historyScroll.setAlignmentX(Component.LEFT_ALIGNMENT); historyScroll.setVisible(false); historyScroll.setPreferredSize(new Dimension(200, 135)); historyScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 135));
		historyToggle.addActionListener(event -> { historyScroll.setVisible(historyToggle.isSelected()); revalidate(); });
		history.addListSelectionListener(event -> { if (!updating && !event.getValueIsAdjusting()) { followingCurrent = history.getSelectedIndex() <= 0; showSelectedSession(); } });
		content.add(historyScroll); content.add(Box.createVerticalStrut(7));
		style(detailsToggle); content.add(detailsToggle);
		JPanel detailPanel = vertical(); detailPanel.setVisible(false); details.setEditable(false); details.setLineWrap(true); details.setWrapStyleWord(true); details.setColumns(17);
		details.setOpaque(false); details.setForeground(MUTED); details.setFont(new Font("SansSerif", Font.PLAIN, 10));
		((DefaultCaret) details.getCaret()).setUpdatePolicy(DefaultCaret.NEVER_UPDATE);
		detailPanel.add(details); detailPanel.add(Box.createVerticalStrut(8)); detailPanel.add(button("Export CSV", export));
		detailsToggle.addActionListener(event -> { detailPanel.setVisible(detailsToggle.isSelected()); refreshDetails(); revalidate(); }); content.add(detailPanel); add(content, BorderLayout.NORTH);
	}
	@Override public void onActivate() { active = true; renderLatest(); }
	@Override public void onDeactivate() { active = false; }
	public void refresh(List<FishingSession> sessions, String state, String diagnostic)
	{
		refresh(sessions, state, diagnostic, LureDisplay.waiting());
	}
	public void refresh(List<FishingSession> sessions, String state, String diagnostic, LureDisplay lures)
	{
		List<FishingSession> copies = new ArrayList<>(); for (FishingSession session : sessions) { copies.add(session.copy()); }
		Runnable update = () ->
		{
			latestSessions = copies; latestState = state; latestDiagnostic = diagnostic; latestLures = lures;
			if (active) { renderLatest(); }
		};
		if (SwingUtilities.isEventDispatchThread()) { update.run(); } else { SwingUtilities.invokeLater(update); }
	}
	public void refreshLures(LureDisplay lures)
	{
		Runnable update = () -> { latestLures = lures; if (active) { showLures(lures); } };
		if (SwingUtilities.isEventDispatchThread()) { update.run(); } else { SwingUtilities.invokeLater(update); }
	}
	private void renderLatest()
	{
		String selectedId = history.getSelectedValue() == null ? "" : history.getSelectedValue().id; updating = true;
		try
		{
			status.setText(latestState); status.setToolTipText(latestState); refreshDetails(); showLures(latestLures);
			boolean sameIds = historyModel.size() == latestSessions.size();
			for (int i = 0; sameIds && i < latestSessions.size(); i++) { sameIds = historyModel.get(i).id.equals(latestSessions.get(i).id); }
			int selected = -1;
			if (!sameIds) { historyModel.clear(); }
			for (int i = 0; i < latestSessions.size(); i++)
			{
				FishingSession session = latestSessions.get(i);
				if (!sameIds) { historyModel.addElement(session); }
				else
				{
					FishingSession old = historyModel.get(i);
					if (old.startedAt != session.startedAt || old.catches != session.catches
						|| old.minimumFailures != session.minimumFailures || old.failureUpper() != session.failureUpper()) { historyModel.set(i, session); }
				}
				if (session.id.equals(selectedId)) { selected = i; }
			}
			int index = latestSessions.isEmpty() ? -1 : followingCurrent || selected < 0 ? 0 : selected;
			if (history.getSelectedIndex() != index) { history.setSelectedIndex(index); }
			showSelectedSession();
		}
		finally { updating = false; }
	}
	private void showSelectedSession()
	{
		int index = history.getSelectedIndex();
		if (index >= 0 && index < latestSessions.size()) { showSession(latestSessions.get(index)); }
	}
	private void refreshDetails()
	{
		if (active && detailsToggle.isSelected() && !Objects.equals(details.getText(), latestDiagnostic)) { details.setText(latestDiagnostic); }
	}
	private void showLures(LureDisplay lures)
	{
		lureAmount.setText(lures.amount); lureNote.setText(lures.note);
		lureAmount.setToolTipText(lures.tooltip); lureNote.setToolTipText(lures.tooltip);
	}
	private void showSession(FishingSession session)
	{
		if (session == null) { return; }
		success.setText(count(session.catches)); failure.setText(failures(session)); rate.setText(rates(session));
		setFontSize(rate, session.minimumFailures == session.failureUpper() ? 26f : 18f);
		setFontSize(failure, session.minimumFailures == session.failureUpper() ? 24f : 17f);
		loggedTime.setText(duration(session.loggedMillis)); fishingTime.setText(duration(session.fishingMillis)); ticks.setText("Fishing ticks: " + count(session.fishingTicks));
		rangeNote.setText(session.adaptiveTiming ? "Adaptive timing: possible range" : session.catches != session.rateCatches() ? "Rate uses " + count(session.rateCatches()) + " of " + count(session.catches) + " catches" : session.variableTiming ? "Variable timing: possible range" : "Failures inferred from fishing ticks");
		rate.setToolTipText("Rate uses " + count(session.rateCatches()) + " of " + count(session.catches) + " catches. Adaptive bounds allow zero or one roll per potentially active tick; clicks and hits do not prove rolls. Unobserved or ambiguous outcomes are excluded.");
		failure.setToolTipText("Silent failures follow supported deadlines. Adaptive timing adds possible failures, not confirmed failed catches.");
	}
	private static void setFontSize(JLabel label, float size) { if (label.getFont().getSize2D() != size) { label.setFont(label.getFont().deriveFont(size)); } }
	public static String failures(FishingSession session) { return session.minimumFailures == session.failureUpper() ? count(session.minimumFailures) : count(session.minimumFailures) + "-" + count(session.failureUpper()); }
	public static String rates(FishingSession session)
	{
		double lower = session.rate(false), upper = session.rate(true); return !Double.isFinite(lower) ? "--" : Math.abs(lower - upper) < 0.00001 ? percent(lower) : percent(lower) + "-" + percent(upper);
	}
	public static String duration(long millis) { long seconds = millis / 1000; return String.format(Locale.ENGLISH, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60); }
	static String percent(double value) { return Double.isFinite(value) ? String.format(Locale.ENGLISH, "%.1f%%", value * 100) : "--"; }
	private static String count(long value) { return String.format(Locale.ENGLISH, "%,d", value); }
	private static JLabel label(String text, Color color, int size) { JLabel label = new JLabel(text); label.setForeground(color); label.setFont(new Font("SansSerif", size >= 17 ? Font.BOLD : Font.PLAIN, size)); label.setAlignmentX(Component.LEFT_ALIGNMENT); return label; }
	private static JPanel vertical() { JPanel panel = new JPanel(); panel.setOpaque(false); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); panel.setAlignmentX(Component.LEFT_ALIGNMENT); return panel; }
	private static JPanel metric(String caption, JLabel value) { JPanel row = new JPanel(new BorderLayout()); row.setOpaque(false); row.setAlignmentX(Component.LEFT_ALIGNMENT); row.add(label(caption, MUTED, 12), BorderLayout.WEST); row.add(value, BorderLayout.EAST); row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 31)); return row; }
	private static JPanel timer(String caption, JLabel value) { JPanel panel = vertical(); panel.add(label(caption, MUTED, 11)); panel.add(Box.createVerticalStrut(4)); panel.add(value); return panel; }
	private static JButton button(String text, Runnable callback) { JButton button = new JButton(text); style(button); button.addActionListener(event -> callback.run()); return button; }
	private static void style(AbstractButton button) { button.setBackground(ColorScheme.DARKER_GRAY_COLOR); button.setForeground(Color.WHITE); button.setFocusPainted(false); button.setFont(new Font("SansSerif", Font.PLAIN, 12)); button.setAlignmentX(Component.LEFT_ALIGNMENT); button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32)); }
}

package com.attempttracker.ui;

import com.attempttracker.core.FishingSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public final class AttemptTrackerOverlay extends OverlayPanel
{
	private final Supplier<FishingSession> session;
	private final BooleanSupplier visible;
	private final Supplier<String> status;
	public AttemptTrackerOverlay(Supplier<FishingSession> session, BooleanSupplier visible, Supplier<String> status)
	{
		this.session = session; this.visible = visible; this.status = status; setPosition(OverlayPosition.TOP_LEFT); panelComponent.setPreferredSize(new Dimension(220, 0));
	}
	@Override public Dimension render(Graphics2D graphics)
	{
		if (!visible.getAsBoolean()) { return null; }
		panelComponent.getChildren().clear(); panelComponent.getChildren().add(TitleComponent.builder().text("Attempt Tracker").build());
		FishingSession current = session.get();
		if (current != null)
		{
			line("Catch success", Long.toString(current.catches)); line("Catch fail", AttemptTrackerPanel.failures(current)); line("Catch rate", AttemptTrackerPanel.rates(current)); line("Fishing time", AttemptTrackerPanel.duration(current.fishingMillis));
			if (current.twoTickTiming) { line("2t sample (inferred)", Long.toString(current.twoTickAttempts()) + " attempts"); }
		}
		line(status.get(), ""); return super.render(graphics);
	}
	private void line(String left, String right) { panelComponent.getChildren().add(LineComponent.builder().left(left).right(right).leftColor(Color.LIGHT_GRAY).rightColor(Color.WHITE).build()); }
}

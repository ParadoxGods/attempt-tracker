package com.attempttracker.ui;

import com.attempttracker.core.FishingSession;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class AttemptTrackerOverlayTest
{
	@Test public void adaptiveWindowsDoNotChangeSupportedAttemptOutput()
	{
		FishingSession unknown = new FishingSession(); unknown.catches = 30;
		FishingSession adaptive = unknown.copy(); adaptive.adaptiveCatches = 30; adaptive.adaptiveFailureUpper = 60; adaptive.adaptiveTiming = true;
		assertArrayEquals(render(unknown), render(adaptive));
		FishingSession supported = unknown.copy(); supported.measuredCatches = 30;
		assertFalse("A measured sample must render differently from waiting for supported attempts", Arrays.equals(render(unknown), render(supported)));
	}
	@Test public void modeledFailureChangesAreRenderedWhileAdaptiveWindowChangesAreExcluded()
	{
		FishingSession session = new FishingSession(); session.catches = 41; session.modeledCatches = 40;
		session.modeledFailureLower = session.modeledFailureUpper = 10; session.modeledTiming = true;
		int[] initial = render(session); session.adaptiveFailureUpper = 10_000; assertArrayEquals(initial, render(session));
		session.modeledFailureUpper = 11; assertFalse(Arrays.equals(initial, render(session)));
		assertEquals("78.4%-80.0%", AttemptTrackerPanel.rates(session));
	}
	private static int[] render(FishingSession session)
	{
		BufferedImage image = new BufferedImage(300, 300, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics(); graphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
		try
		{
			AttemptTrackerOverlay overlay = new AttemptTrackerOverlay(() -> session, () -> true, () -> "Fishing");
			overlay.render(graphics); overlay.render(graphics);
			return ((DataBufferInt) image.getRaster().getDataBuffer()).getData().clone();
		}
		finally { graphics.dispose(); }
	}
}

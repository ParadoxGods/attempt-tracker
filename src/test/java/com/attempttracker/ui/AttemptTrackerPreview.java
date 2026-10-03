package com.attempttracker.ui;

import com.attempttracker.core.FishingSession;

import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.io.OutputStream;
import net.runelite.client.util.Filepath;
import java.util.Arrays;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** Headless visual QA of the real Swing panel and RuneLite overlay using demonstration data. */
public final class AttemptTrackerPreview
{
	private AttemptTrackerPreview()
	{
	}

	public static void main(String[] args) throws Exception
	{
		Path requested = Paths.get(args.length == 0 ? "build/preview/attempt-tracker.png" : args[0]).toAbsolutePath();
		Filepath directory = Filepath.Unchecked.getRooted(requested.getParent());
		directory.createDirectories();
		Filepath destination = directory.joinSegment(requested.getFileName().toString());
		SwingUtilities.invokeAndWait(() ->
		{
			try
			{
				render(destination);
			}
			catch (IOException ex)
			{
				throw new IllegalStateException(ex);
			}
		});
		System.out.println("Preview: " + destination);
	}

	private static void render(Filepath destination) throws IOException
	{
		UIManager.put("Panel.background", new Color(40, 43, 48));
		UIManager.put("Label.font", new Font("SansSerif", Font.PLAIN, 12));
		UIManager.put("Button.font", new Font("SansSerif", Font.PLAIN, 12));
		long time = 1_790_900_000_000L;
        FishingSession current = new FishingSession(); current.catches = 951; current.measuredCatches = 950;
        current.minimumFailures = current.maximumFailures = 50; current.loggedMillis = 3_600_000; current.fishingMillis = 3_000_000; current.fishingTicks = 5000;
        FishingSession previous = current.copy(); previous.id = "previous"; previous.startedAt -= 3_600_000; previous.endedAt = time;
        AttemptTrackerPanel panel = new AttemptTrackerPanel(() -> {}, () -> {});
        panel.onActivate();
        panel.refresh(Arrays.asList(current, previous), "Fishing", "Counting completed 5-tick cycles.", new LureDisplay("3 per catch", "Detected automatically", "Live lure choice"));
		panel.setSize(new Dimension(225, 1000));
		for (int i = 0; i < 3; i++)
		{
			layout(panel);
		}
		BufferedImage image = new BufferedImage(1035, 720, BufferedImage.TYPE_INT_ARGB);
		Graphics2D graphics = image.createGraphics();
		graphics.setColor(new Color(25, 28, 34));
		graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
		graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		graphics.setColor(Color.WHITE);
		graphics.setFont(new Font("SansSerif", Font.BOLD, 13));
		graphics.drawString("Attempt Tracker | demonstration data", 20, 25);
		Graphics2D panelGraphics = (Graphics2D) graphics.create(20, 42, 225, 1000);
		panel.printAll(panelGraphics);
		panelGraphics.dispose();
		AttemptTrackerOverlay overlay = new AttemptTrackerOverlay(() -> current, () -> true,
			() -> "Fishing");
		Graphics2D overlayGraphics = (Graphics2D) graphics.create(305, 42, 245, 400);
		overlayGraphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
		// RuneLite panels use their previous frame's measured size for the background.
		overlay.render(overlayGraphics);
		overlay.render(overlayGraphics);
		overlayGraphics.dispose();
		AttemptTrackerOverlay waiting = new AttemptTrackerOverlay(() -> null, () -> true,
			() -> "Awaiting harpoon start message.");
		Graphics2D waitingGraphics = (Graphics2D) graphics.create(305, 270, 245, 100);
		waitingGraphics.setFont(new Font("SansSerif", Font.PLAIN, 12));
		waiting.render(waitingGraphics);
		waiting.render(waitingGraphics);
		waitingGraphics.dispose();
        FishingSession variable = current.copy(); variable.minimumFailures = 40; variable.maximumFailures = 55; variable.variableTiming = true;
        AttemptTrackerPanel variablePanel = new AttemptTrackerPanel(() -> {}, () -> {});
        variablePanel.onActivate();
        variablePanel.refresh(Arrays.asList(variable), "Fishing", "Variable timing: possible range.", new LureDisplay("5 per catch", "Restart harpooning for attempts", "Live lure choice")); variablePanel.setSize(225, 650);
        for (int i = 0; i < 3; i++) { layout(variablePanel); }
        Graphics2D variableGraphics = (Graphics2D) graphics.create(535, 42, 225, 650);
        variablePanel.printAll(variableGraphics); variableGraphics.dispose();
		FishingSession adaptive = new FishingSession(); adaptive.catches = adaptive.adaptiveCatches = 30;
		adaptive.adaptiveFailureUpper = 60; adaptive.adaptiveTiming = true; adaptive.loggedMillis = 180000; adaptive.fishingMillis = 54000; adaptive.fishingTicks = 90;
		AttemptTrackerPanel adaptivePanel = new AttemptTrackerPanel(() -> {}, () -> {}); adaptivePanel.onActivate();
		adaptivePanel.refresh(Arrays.asList(adaptive), "Fishing", "Adaptive timing: possible attempt range. 2t interactions (rolls unverified)", new LureDisplay("1 per catch", "Detected automatically", "Live lure choice"));
		adaptivePanel.setSize(225, 650); for (int i = 0; i < 3; i++) { layout(adaptivePanel); }
		Graphics2D adaptiveGraphics = (Graphics2D) graphics.create(790, 42, 225, 650); adaptivePanel.printAll(adaptiveGraphics); adaptiveGraphics.dispose();
		graphics.dispose();
		try (OutputStream output = destination.openOutputStream()) { ImageIO.write(image, "png", output); }
	}

	private static void layout(Container container)
	{
		container.doLayout();
		for (java.awt.Component component : container.getComponents())
		{
			if (component instanceof Container)
			{
				layout((Container) component);
			}
		}
	}
}

package com.attempttracker.diagnostics;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import net.runelite.client.util.Filepath;

/** Local per-tick trace, called only from the plugin's serial I/O worker. */
public final class TickTrace implements Closeable
{
	private static final int MAX_ROWS = 100_000;
	private final Filepath directory;
	private BufferedWriter writer;
	private int rows;
	public TickTrace(Filepath directory) { this.directory = directory; }
	public void append(String row) throws IOException
	{
		if (writer == null || rows == MAX_ROWS)
		{
			close(); directory.createDirectories();
			Filepath file = directory.join("ticks-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".csv");
			writer = file.openBufferedWriter(StandardOpenOption.CREATE_NEW);
			writer.write("time_utc,tick,animation,frame,skill,target,xp_delta,inventory_lures,raw_lure_setting,configured_cycle,first_roll_delay,success_messages,eligible,start_tick,timing_result,live_interaction,context_problem,start_message,start_message_tick,incoming_hits,observed_fishing_catches,interaction_rhythm,two_tick_state\r\n"); rows = 0;
		}
		writer.write(row); rows++;
		writer.flush();
	}
	public static String row(int tick, int animation, int frame, String skill, String target, int xpDelta,
		int lures, int rawSetting, int cycle, int firstDelay, int successes, boolean eligible, int startTick, String timingResult,
		String liveInteraction, String contextProblem, String startMessage, int startMessageTick)
	{
		return row(tick, animation, frame, skill, target, xpDelta, lures, rawSetting, cycle, firstDelay, successes, eligible,
			startTick, timingResult, liveInteraction, contextProblem, startMessage, startMessageTick, 0, 0, "");
	}
	public static String row(int tick, int animation, int frame, String skill, String target, int xpDelta,
		int lures, int rawSetting, int cycle, int firstDelay, int successes, boolean eligible, int startTick, String timingResult,
		String liveInteraction, String contextProblem, String startMessage, int startMessageTick,
		int incomingHits, int fishingCatches, String rhythm)
	{
		return row(tick, animation, frame, skill, target, xpDelta, lures, rawSetting, cycle, firstDelay, successes, eligible,
			startTick, timingResult, liveInteraction, contextProblem, startMessage, startMessageTick, incomingHits, fishingCatches, rhythm, "");
	}
	public static String row(int tick, int animation, int frame, String skill, String target, int xpDelta,
		int lures, int rawSetting, int cycle, int firstDelay, int successes, boolean eligible, int startTick, String timingResult,
		String liveInteraction, String contextProblem, String startMessage, int startMessageTick,
		int incomingHits, int fishingCatches, String rhythm, String twoTickState)
	{
		return Instant.now() + "," + tick + "," + animation + "," + frame + "," + quoted(skill) + "," + quoted(target)
			+ "," + xpDelta + "," + lures + "," + rawSetting + "," + cycle + "," + firstDelay + "," + successes + "," + eligible
			+ "," + startTick + "," + quoted(timingResult) + "," + quoted(liveInteraction) + "," + quoted(contextProblem)
			+ "," + quoted(startMessage) + "," + startMessageTick + "," + incomingHits + "," + fishingCatches + "," + quoted(rhythm) + "," + quoted(twoTickState) + "\r\n";
	}
	private static String quoted(String text) { String value = text == null ? "" : text; return "\"" + value.replace("\"", "\"\"") + "\""; }
	@Override public void close() throws IOException { if (writer != null) { try { writer.close(); } finally { writer = null; rows = 0; } } }
}

package com.attempttracker.diagnostics;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Local per-tick trace, called only from the plugin's serial I/O worker. */
public final class TickTrace implements Closeable
{
	private static final int MAX_ROWS = 100_000;
	private final Path directory;
	private BufferedWriter writer;
	private int rows;
	public TickTrace(Path directory) { this.directory = directory; }
	public void append(String row) throws IOException
	{
		if (writer == null || rows == MAX_ROWS)
		{
			close(); Files.createDirectories(directory);
			Path file = directory.resolve("ticks-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".csv");
			writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
			writer.write("time_utc,tick,animation,frame,skill,target,xp_delta,inventory_lures,raw_lure_setting,configured_cycle,first_roll_delay,success_messages,eligible,start_tick,timing_result,live_interaction,context_problem,start_message,start_message_tick\r\n"); rows = 0;
		}
		writer.write(row); rows++;
		writer.flush();
	}
	public static String row(int tick, int animation, int frame, String skill, String target, int xpDelta,
		int lures, int rawSetting, int cycle, int firstDelay, int successes, boolean eligible, int startTick, String timingResult,
		String liveInteraction, String contextProblem, String startMessage, int startMessageTick)
	{
		return Instant.now() + "," + tick + "," + animation + "," + frame + "," + quoted(skill) + "," + quoted(target)
			+ "," + xpDelta + "," + lures + "," + rawSetting + "," + cycle + "," + firstDelay + "," + successes + "," + eligible
			+ "," + startTick + "," + quoted(timingResult) + "," + quoted(liveInteraction) + "," + quoted(contextProblem)
			+ "," + quoted(startMessage) + "," + startMessageTick + "\r\n";
	}
	private static String quoted(String text) { String value = text == null ? "" : text; return "\"" + value.replace("\"", "\"\"") + "\""; }
	@Override public void close() throws IOException { if (writer != null) { try { writer.close(); } finally { writer = null; rows = 0; } } }
}

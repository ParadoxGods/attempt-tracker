package com.attempttracker.store;

import com.attempttracker.core.FishingSession;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.time.Instant;

/** Separate file preserves the legacy per-setup history during the UI migration. */
public final class FishingSessionStore
{
	private final Path file;
	private final Gson gson;
	private boolean preserveUnreadable;
	public FishingSessionStore(Path directory, Gson gson) { file = directory.resolve("fishing-sessions.json"); this.gson = gson; }
	public synchronized List<FishingSession> load() throws IOException
	{
		if (!Files.exists(file)) { return null; }
		preserveUnreadable = true;
		if (Files.size(file) > 2 * 1024 * 1024) { throw new IOException("Fishing session file exceeds size limit"); }
		try
		{
			List<FishingSession> saved = gson.fromJson(Files.readString(file, StandardCharsets.UTF_8), new TypeToken<List<FishingSession>>() {}.getType());
			if (saved == null) { throw new IllegalArgumentException("Expected a session array"); }
			preserveUnreadable = false; return saved;
		}
		catch (RuntimeException ex) { throw new IOException("Fishing sessions could not be read", ex); }
	}
	public synchronized void save(List<FishingSession> sessions) throws IOException
	{
		byte[] bytes = gson.toJson(sessions.subList(0, Math.min(200, sessions.size()))).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > 2 * 1024 * 1024) { throw new IOException("Fishing session file exceeds size limit"); }
		Files.createDirectories(file.getParent());
		if (preserveUnreadable && Files.exists(file))
		{
			Path backup = Files.createTempFile(file.getParent(), "fishing-sessions.unreadable-", ".json");
			try { Files.copy(file, backup, StandardCopyOption.REPLACE_EXISTING); }
			catch (IOException ex) { Files.deleteIfExists(backup); throw ex; }
			preserveUnreadable = false;
		}
		SessionStore.writeAtomically(file, bytes);
	}
	public void exportCsv(List<FishingSession> sessions, Path destination) throws IOException
	{
		StringBuilder csv = new StringBuilder("session_id,started_at_utc,ended_at_utc,catch_success,measured_catches,catch_fail_min,catch_fail_max,catch_rate_min,catch_rate_max,logged_in_ms,fishing_ms,fishing_ticks,variable_timing\r\n");
		for (FishingSession session : sessions)
		{
			csv.append(SessionStore.csvText(session.id)).append(',')
				.append(SessionStore.csvText(Instant.ofEpochMilli(session.startedAt).toString())).append(',')
				.append(SessionStore.csvText(session.endedAt == 0 ? "" : Instant.ofEpochMilli(session.endedAt).toString())).append(',')
				.append(session.catches).append(',').append(session.measuredCatches).append(',')
				.append(session.minimumFailures).append(',').append(session.maximumFailures).append(',')
				.append(rate(session.rate(false))).append(',').append(rate(session.rate(true))).append(',')
				.append(session.loggedMillis).append(',').append(session.fishingMillis).append(',')
				.append(session.fishingTicks).append(',').append(session.variableTiming).append("\r\n");
		}
		Path target = destination.toAbsolutePath(); Files.createDirectories(target.getParent());
		SessionStore.writeAtomically(target, csv.toString().getBytes(StandardCharsets.UTF_8));
	}
	private static String rate(double value) { return Double.isFinite(value) ? Double.toString(value) : ""; }
}

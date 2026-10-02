package com.attempttracker.store;

import com.attempttracker.core.AttemptSession;
import com.attempttracker.core.TrackingMethod;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Local, bounded session persistence. The tracking engine validates restored sessions. */
public final class SessionStore
{
	private static final int FORMAT_VERSION = 1;
	private static final int MAX_SESSIONS = 200;
	private static final int MAX_FILE_BYTES = 2 * 1024 * 1024;
	private final Path directory;
	private final Gson gson;
	private String lastLoadWarning = "";
	private boolean preserveUnreadableHistory;

	public SessionStore(Path directory, Gson gson)
	{
		this.directory = Objects.requireNonNull(directory);
		this.gson = Objects.requireNonNull(gson);
	}

	/** Corrupt or unsupported data returns an empty history and leaves the source file intact. */
	public synchronized List<AttemptSession> load() throws IOException
	{
		lastLoadWarning = "";
		preserveUnreadableHistory = false;
		Path file = directory.resolve("sessions.json");
		if (!Files.exists(file))
		{
			return Collections.emptyList();
		}
		if (Files.size(file) > MAX_FILE_BYTES)
		{
			lastLoadWarning = "Saved history exceeds the size limit.";
			preserveUnreadableHistory = true;
			return Collections.emptyList();
		}
		byte[] bytes;
		try (InputStream stream = Files.newInputStream(file))
		{
			bytes = stream.readNBytes(MAX_FILE_BYTES + 1);
		}
		if (bytes.length > MAX_FILE_BYTES)
		{
			lastLoadWarning = "Saved history exceeds the size limit.";
			preserveUnreadableHistory = true;
			return Collections.emptyList();
		}
		try
		{
			JsonElement parsed = new JsonParser().parse(new String(bytes, StandardCharsets.UTF_8));
			if (!parsed.isJsonObject())
			{
				throw new JsonParseException("Expected a history object");
			}
			JsonObject root = parsed.getAsJsonObject();
			if (!root.has("version") || root.get("version").getAsInt() != FORMAT_VERSION
				|| !root.has("sessions") || !root.get("sessions").isJsonArray())
			{
				lastLoadWarning = "Saved history has an unsupported format.";
				preserveUnreadableHistory = true;
				return Collections.emptyList();
			}
			List<AttemptSession> sessions = new ArrayList<>();
			for (JsonElement element : root.getAsJsonArray("sessions"))
			{
				if (sessions.size() >= MAX_SESSIONS)
				{
					break;
				}
				if (!element.isJsonObject())
				{
					lastLoadWarning = "Some invalid history entries were skipped.";
					preserveUnreadableHistory = true;
					continue;
				}
				try
				{
					AttemptSession session = gson.fromJson(element, AttemptSession.class);
					if (session != null)
					{
						sessions.add(session);
					}
				}
				catch (RuntimeException ex)
				{
					lastLoadWarning = "Some invalid history entries were skipped.";
					preserveUnreadableHistory = true;
				}
			}
			return sessions;
		}
		catch (RuntimeException ex)
		{
			lastLoadWarning = "Saved history could not be read; a new session will be started.";
			preserveUnreadableHistory = true;
			return Collections.emptyList();
		}
	}

	public synchronized String getLastLoadWarning()
	{
		return lastLoadWarning;
	}

	public synchronized void save(List<AttemptSession> sessions) throws IOException
	{
		JsonObject root = new JsonObject();
		root.addProperty("version", FORMAT_VERSION);
		JsonArray items = new JsonArray();
		for (AttemptSession session : bounded(sessions))
		{
			items.add(gson.toJsonTree(session));
		}
		root.add("sessions", items);
		byte[] bytes = gson.toJson(root).getBytes(StandardCharsets.UTF_8);
		if (bytes.length > MAX_FILE_BYTES)
		{
			throw new IOException("Session history exceeds the size limit");
		}
		Files.createDirectories(directory);
		Path destination = directory.resolve("sessions.json");
		if (preserveUnreadableHistory && Files.exists(destination))
		{
			// Copy before replacing so a failed backup never destroys the original history.
			Path backup = Files.createTempFile(directory, "sessions.unreadable-" + System.currentTimeMillis() + "-", ".json");
			try
			{
				Files.copy(destination, backup, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException ex)
			{
				Files.deleteIfExists(backup);
				throw ex;
			}
			preserveUnreadableHistory = false;
		}
		writeAtomically(destination, bytes);
	}

	/** CSV rates are proportions (0..1). Text is protected against spreadsheet formulas. */
	public void exportCsv(List<AttemptSession> sessions, Path destination) throws IOException
	{
		StringBuilder csv = new StringBuilder("session_id,activity,setup,method,started_at_utc,updated_at_utc,attempts,successes,failures,success_rate,ci95_lower,ci95_upper,cycle_ticks,excluded_windows\r\n");
		for (AttemptSession session : bounded(sessions))
		{
			boolean successesOnly = session.getMethod() == TrackingMethod.SUCCESS_ONLY;
			csv.append(csvText(session.getId())).append(',')
				.append(csvText(session.getActivity())).append(',')
				.append(csvText(session.getSetup())).append(',')
				.append(csvText(session.getMethod() == null ? "" : session.getMethod().name())).append(',')
				.append(csvText(Instant.ofEpochMilli(session.getStartedAt()).toString())).append(',')
				.append(csvText(Instant.ofEpochMilli(session.getUpdatedAt()).toString())).append(',')
				.append(successesOnly ? "" : Long.toString(session.getAttempts())).append(',')
				.append(session.getSuccesses()).append(',')
				.append(successesOnly ? "" : Long.toString(session.getFailures())).append(',')
				.append(csvRate(session.getSuccessRate())).append(',')
				.append(csvRate(session.getConfidenceLower())).append(',')
				.append(csvRate(session.getConfidenceUpper())).append(',')
				.append(session.getCycleTicks()).append(',')
				.append(session.getExcludedWindows()).append("\r\n");
		}
		Path target = Objects.requireNonNull(destination).toAbsolutePath();
		Files.createDirectories(target.getParent());
		writeAtomically(target, csv.toString().getBytes(StandardCharsets.UTF_8));
	}

	static String csvText(String text)
	{
		String value = text == null ? "" : text;
		int first = 0;
		while (first < value.length() && Character.isWhitespace(value.charAt(first)))
		{
			first++;
		}
		if ((first < value.length() && "=+-@".indexOf(value.charAt(first)) >= 0)
			|| (!value.isEmpty() && (value.charAt(0) == '\t' || value.charAt(0) == '\r' || value.charAt(0) == '\n')))
		{
			value = "'" + value;
		}
		return '"' + value.replace("\"", "\"\"") + '"';
	}

	private static String csvRate(double rate)
	{
		return Double.isFinite(rate) ? Double.toString(rate) : "";
	}

	private static List<AttemptSession> bounded(List<AttemptSession> sessions)
	{
		Objects.requireNonNull(sessions);
		List<AttemptSession> result = new ArrayList<>();
		for (AttemptSession session : sessions)
		{
			if (result.size() >= MAX_SESSIONS)
			{
				break;
			}
			if (session != null)
			{
				result.add(session.copy());
			}
		}
		return result;
	}

	static void writeAtomically(Path destination, byte[] bytes) throws IOException
	{
		Path temporary = Files.createTempFile(destination.toAbsolutePath().getParent(), "attempt-tracker-", ".tmp");
		try
		{
			Files.write(temporary, bytes);
			try
			{
				Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (AtomicMoveNotSupportedException ex)
			{
				Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
			}
		}
		finally
		{
			Files.deleteIfExists(temporary);
		}
	}
}

package com.attempttracker.store;

import com.attempttracker.core.AttemptSession;
import com.attempttracker.core.TrackingMethod;
import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import net.runelite.client.util.Filepath;
import static com.attempttracker.FilepathTestSupport.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SessionStoreTest
{
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void legacyCsvExportAlsoAcceptsAFileScopedSelection() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		Filepath selected = directory.join("legacy.csv").rooted();
		new SessionStore(directory, new Gson()).exportCsv(Collections.singletonList(session("one", "Fishing", "", 9, 1)), selected);
		assertTrue(readString(selected).contains(",10,9,1,0.9,"));
	}

	@Test
	public void roundTripPreservesAllSessionFieldsAndLeavesNoTemporaryFiles() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		SessionStore store = new SessionStore(directory, new Gson());
		AttemptSession original = session("one", "Shark fishing", "Crystal harpoon; 3 lures", 950, 50);
		store.save(Collections.singletonList(original));
		AttemptSession restored = store.load().get(0);
		assertEquals(original.getId(), restored.getId());
		assertEquals(original.getActivity(), restored.getActivity());
		assertEquals(original.getSetup(), restored.getSetup());
		assertEquals(original.getMethod(), restored.getMethod());
		assertEquals(original.getStartedAt(), restored.getStartedAt());
		assertEquals(original.getUpdatedAt(), restored.getUpdatedAt());
		assertEquals(1000, restored.getAttempts());
		assertEquals(950, restored.getSuccesses());
		assertEquals(50, restored.getFailures());
		assertEquals(2, restored.getExcludedWindows());
		assertEquals(5, restored.getCycleTicks());
		assertEquals("", store.getLastLoadWarning());
		store.save(Collections.singletonList(session("two", "Cooking", "", 1, 2)));
		assertEquals("two", store.load().get(0).getId());
		try (Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
		{
			assertEquals(1, files.count());
		}
	}

	@Test
	public void corruptOrUnsupportedHistoryDoesNotCrashOrChangeSource() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		SessionStore store = new SessionStore(directory, new Gson());
		Filepath source = directory.join("sessions.json");
		for (String invalid : Arrays.asList("{broken", "null", "[]", "{\"version\":99,\"sessions\":[]}"))
		{
			source.write(invalid.getBytes(StandardCharsets.UTF_8));
			assertTrue(store.load().isEmpty());
			assertFalse(store.getLastLoadWarning().isEmpty());
			assertEquals(invalid, new String(readBytes(source), StandardCharsets.UTF_8));
		}
	}

	@Test
	public void oversizedFilesAreRejectedAndSessionHistoryIsBounded() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		SessionStore store = new SessionStore(directory, new Gson());
		directory.join("sessions.json").write( new byte[2 * 1024 * 1024 + 1]);
		assertTrue(store.load().isEmpty());
		assertTrue(store.getLastLoadWarning().contains("size limit"));
		List<AttemptSession> sessions = new ArrayList<>();
		for (int i = 0; i < 250; i++)
		{
			sessions.add(session("id-" + i, "Fishing", "", 1, 0));
		}
		store.save(sessions);
		assertEquals(200, store.load().size());
		assertEquals("id-0", store.load().get(0).getId());
	}

	@Test
	public void invalidEntriesAreSkippedWithoutDiscardingValidEntries() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		SessionStore store = new SessionStore(directory, new Gson());
		String valid = new Gson().toJson(session("ok", "Fishing", "", 1, 0));
		directory.join("sessions.json").write(
			("{\"version\":1,\"sessions\":[null,42,{\"successes\":\"bad\"}," + valid + "]}").getBytes(StandardCharsets.UTF_8));
		assertEquals(1, store.load().size());
		assertEquals("ok", store.load().get(0).getId());
		assertFalse(store.getLastLoadWarning().isEmpty());
	}

	@Test
	public void unreadableHistoryIsBackedUpBeforeTheFirstReplacement() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		Filepath source = directory.join("sessions.json");
		String corrupt = "{recover this history manually";
		source.write(corrupt.getBytes(StandardCharsets.UTF_8));
		SessionStore store = new SessionStore(directory, new Gson());
		assertTrue(store.load().isEmpty());
		store.save(Collections.singletonList(session("new", "Fishing", "", 1, 0)));
		List<Filepath> backups = new ArrayList<>();
		try (Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
		{
			files.filter(path -> path.getFileName().startsWith("sessions.unreadable-")).forEach(backups::add);
		}
		assertEquals(1, backups.size());
		assertEquals(corrupt, new String(readBytes(backups.get(0)), StandardCharsets.UTF_8));
		assertEquals("new", store.load().get(0).getId());
		store.save(Collections.singletonList(session("newer", "Fishing", "", 2, 0)));
		try (Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
		{
			assertEquals(2, files.count());
		}
	}

	@Test
	public void successesOnlyCsvLeavesUnknownCountsAndRatesBlank() throws IOException
	{
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		Filepath exported = directory.join("successes.csv");
		AttemptSession catches = new AttemptSession("only", "Fishing", "", TrackingMethod.SUCCESS_ONLY,
			1_790_900_000_000L, 1_790_900_000_000L, 42, 0, 0, 0);
		new SessionStore(directory, new Gson()).exportCsv(Collections.singletonList(catches), exported);
		String row = readLines(exported).get(1);
		String[] columns = row.split(",", -1);
		assertEquals(14, columns.length);
		assertEquals("", columns[6]);
		assertEquals("42", columns[7]);
		for (int i = 8; i <= 11; i++)
		{
			assertEquals("", columns[i]);
		}
	}

	@Test
	public void csvEscapesQuotesNewlinesAndNeutralizesSpreadsheetFormulas() throws IOException
	{
		assertEquals("\"'  =SUM(1,2)\"", SessionStore.csvText("  =SUM(1,2)"));
		assertEquals("\"'+cmd\"", SessionStore.csvText("+cmd"));
		assertEquals("\"'-cmd\"", SessionStore.csvText("-cmd"));
		assertEquals("\"'@SUM(1)\"", SessionStore.csvText("@SUM(1)"));
		assertEquals("\"'\tvalue\"", SessionStore.csvText("\tvalue"));
		assertEquals("\"line\n\"\"quoted\"\"\"", SessionStore.csvText("line\n\"quoted\""));
		Filepath directory = Filepath.Unchecked.getRooted(temporary.newFolder().toPath());
		Filepath exported = directory.join("export.csv");
		new SessionStore(directory, new Gson()).exportCsv(Arrays.asList(
			session("id", "=1+1", "Lures, \"three\"\nline", 95, 5),
			session("empty", "Fishing", "", 0, 0)), exported);
		String csv = new String(readBytes(exported), StandardCharsets.UTF_8);
		assertTrue(csv.contains("\"'=1+1\""));
		assertTrue(csv.contains("\"Lures, \"\"three\"\"\nline\""));
		assertTrue(csv.contains(",100,95,5,0.95,"));
		assertTrue(csv.contains(",0,0,0,,,,5,2\r\n"));
		assertFalse(csv.contains("NaN"));
	}

	private static AttemptSession session(String id, String activity, String setup, long successes, long failures)
	{
		return new AttemptSession(id, activity, setup, TrackingMethod.TIMED,
			1_790_900_000_000L, 1_790_900_600_000L, successes, failures, 2, 5);
	}
}

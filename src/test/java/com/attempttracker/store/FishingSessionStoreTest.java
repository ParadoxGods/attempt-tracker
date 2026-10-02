package com.attempttracker.store;

import com.attempttracker.core.FishingSession;
import com.google.gson.Gson;
import net.runelite.client.util.Filepath;
import static com.attempttracker.FilepathTestSupport.*;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FishingSessionStoreTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	@Test public void csvExportAcceptsAFileScopedChooserSelection() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		Filepath selected = directory.join("selected.csv").rooted();
		assertTrue(selected.isRoot());
		FishingSession session = new FishingSession();
		session.catches = session.measuredCatches = 8;
		new FishingSessionStore(directory, new Gson()).exportCsv(Collections.singletonList(session), selected);
		assertTrue(readString(selected).contains(",8,8,0,0,1.0,1.0,"));
		try (java.util.stream.Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
		{
			assertEquals("Only the user-selected CSV may be created", 1, files.count());
		}
	}
	@Test public void oversizedHistoryRemainsIntactUntilItIsBackedUp() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath());
		Filepath history = directory.join("fishing-sessions.json");
		history.write(new byte[2 * 1024 * 1024 + 1]);
		FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		try { store.load(); fail("Oversized history must be rejected"); }
		catch (java.io.IOException expected) { assertTrue(expected.getMessage().contains("size limit")); }
		assertEquals(2 * 1024 * 1024 + 1, history.size());
		store.save(Collections.singletonList(new FishingSession()));
		try (java.util.stream.Stream<Filepath> files = directory.walk(1))
		{
			Filepath backup = files.filter(path -> path.getFileName().startsWith("fishing-sessions.unreadable-")).findFirst().get();
			assertEquals(2 * 1024 * 1024 + 1, backup.size());
		}
		assertEquals(1, store.load().size());
	}
	@Test public void roundTripKeepsCurrentSessionAndBothClocks() throws Exception
	{
		FishingSessionStore store = new FishingSessionStore(Filepath.Unchecked.getRooted(folder.getRoot().toPath()), new Gson()); assertNull(store.load());
		FishingSession session = new FishingSession(); session.catches = session.measuredCatches = 12; session.minimumFailures = 1; session.maximumFailures = 3;
		session.loggedMillis = 2000; session.fishingMillis = 1000; store.save(Collections.singletonList(session));
		FishingSession restored = store.load().get(0); assertEquals(session.id, restored.id); assertEquals(2000, restored.loggedMillis); assertEquals(1000, restored.fishingMillis); assertEquals(3, restored.maximumFailures);
		Filepath csv = Filepath.Unchecked.getRooted(folder.getRoot().toPath()).join("export.csv"); store.exportCsv(Collections.singletonList(restored), csv);
		String text = readString(csv); assertTrue(text.contains("catch_fail_min,catch_fail_max")); assertTrue(text.contains(",12,12,1,3,0.8,")); assertTrue(text.contains(",2000,1000,0,false"));
	}
	@Test public void malformedHistoryIsBackedUpBeforeNewSave() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath()), file = directory.join("fishing-sessions.json"); file.write("broken");
		FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		try { store.load(); fail("Malformed history must report failure"); } catch (java.io.IOException expected) { }
		store.save(Collections.singletonList(new FishingSession()));
		try (java.util.stream.Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
		{
			Filepath backup = files.filter(path -> path.getFileName().startsWith("fishing-sessions.unreadable-")).findFirst().get(); assertEquals("broken", readString(backup));
		}
	}
}

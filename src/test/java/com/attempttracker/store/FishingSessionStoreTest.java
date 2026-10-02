package com.attempttracker.store;

import com.attempttracker.core.FishingSession;
import com.google.gson.Gson;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class FishingSessionStoreTest
{
	@Rule public TemporaryFolder folder = new TemporaryFolder();
	@Test public void roundTripKeepsCurrentSessionAndBothClocks() throws Exception
	{
		FishingSessionStore store = new FishingSessionStore(folder.getRoot().toPath(), new Gson()); assertNull(store.load());
		FishingSession session = new FishingSession(); session.catches = session.measuredCatches = 12; session.minimumFailures = 1; session.maximumFailures = 3;
		session.loggedMillis = 2000; session.fishingMillis = 1000; store.save(Collections.singletonList(session));
		FishingSession restored = store.load().get(0); assertEquals(session.id, restored.id); assertEquals(2000, restored.loggedMillis); assertEquals(1000, restored.fishingMillis); assertEquals(3, restored.maximumFailures);
		Path csv = folder.getRoot().toPath().resolve("export.csv"); store.exportCsv(Collections.singletonList(restored), csv);
		String text = Files.readString(csv); assertTrue(text.contains("catch_fail_min,catch_fail_max")); assertTrue(text.contains(",12,12,1,3,0.8,")); assertTrue(text.contains(",2000,1000,0,false"));
	}
	@Test public void malformedHistoryIsBackedUpBeforeNewSave() throws Exception
	{
		Path directory = folder.getRoot().toPath(), file = directory.resolve("fishing-sessions.json"); Files.writeString(file, "broken");
		FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		try { store.load(); fail("Malformed history must report failure"); } catch (java.io.IOException expected) { }
		store.save(Collections.singletonList(new FishingSession()));
		try (java.util.stream.Stream<Path> files = Files.list(directory))
		{
			Path backup = files.filter(path -> path.getFileName().toString().startsWith("fishing-sessions.unreadable-")).findFirst().get(); assertEquals("broken", Files.readString(backup));
		}
	}
}

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
	@Test public void twoTickHistoryAndCsvRetainFocusedSampleAndLegacyColumns() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath()); FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		FishingSession session = new FishingSession(); session.catches = 100; session.measuredCatches = 10; session.minimumFailures = session.maximumFailures = 5;
		session.adaptiveCatches = 50; session.adaptiveFailureUpper = 100; session.adaptiveTiming = true;
		session.twoTickCatches = 40; session.twoTickFailures = 10; session.twoTickTiming = true;
		store.save(Collections.singletonList(session)); FishingSession restored = store.load().get(0);
		assertEquals(40, restored.twoTickCatches); assertEquals(10, restored.twoTickFailures); assertTrue(restored.twoTickTiming);
		assertEquals(0.8, restored.rate(false), 0);
		Filepath csv = directory.join("two-tick.csv"); store.exportCsv(Collections.singletonList(restored), csv); String text = readString(csv);
		assertTrue(text.contains("rate_catches,two_tick_catches,two_tick_failures,two_tick_timing,modeled_catches"));
		assertTrue(text.contains(",100,10,10,10,0.8,0.8,")); assertTrue(text.contains(",50,100,true,40,40,10,true,0,0,0,false\r\n"));
		// Before the new fields existed, persisted JSON contained only the strict and adaptive cohorts.
		directory.join("fishing-sessions.json").write("[{\"id\":\"legacy\",\"startedAt\":1,\"catches\":8,\"measuredCatches\":8}]");
		com.attempttracker.core.FishingSessions book = new com.attempttracker.core.FishingSessions(); book.restore(store.load());
		assertEquals("legacy", book.current().id); assertEquals(0, book.current().twoTickAttempts()); assertFalse(book.current().twoTickTiming);
		assertEquals(0, book.current().modeledAttempts(true)); assertFalse(book.current().modeledTiming);
		assertEquals(1, book.current().rate(false), 0);
	}
	@Test public void adaptiveHistoryAndExportRetainSeparateEvidenceAndBounds() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath()); FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		FishingSession session = new FishingSession(); session.catches = 10; session.measuredCatches = 4;
		session.minimumFailures = session.maximumFailures = 2; session.adaptiveCatches = 6; session.adaptiveFailureUpper = 8; session.adaptiveTiming = true;
		store.save(Collections.singletonList(session)); FishingSession restored = store.load().get(0);
		assertEquals(6, restored.adaptiveCatches); assertEquals(8, restored.adaptiveFailureUpper); assertTrue(restored.adaptiveTiming);
		com.attempttracker.core.FishingSessions book = new com.attempttracker.core.FishingSessions(); book.restore(store.load());
		assertEquals(session.id, book.current().id); assertEquals(4.0 / 6, book.current().rate(false), 0);
		Filepath csv = directory.join("adaptive.csv"); store.exportCsv(book.snapshots(), csv); String text = readString(csv);
		assertTrue(text.contains("adaptive_catches,adaptive_fail_upper,adaptive_timing,rate_catches"));
		assertTrue(text.contains(",10,4,2,2,0.6666666666666666,")); assertTrue(text.contains(",6,8,true,4,0,0,false,0,0,0,false\r\n"));
		book.reset(); assertEquals(6, book.snapshots().get(1).adaptiveCatches); assertEquals(0, book.current().adaptiveCatches);
	}
	@Test public void modeledHistoryAndCsvRetainTimerBoundsAndExcludeAdaptiveWindowsFromPrimaryRate() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath()); FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		FishingSession session = new FishingSession(); session.catches = 100; session.measuredCatches = 10;
		session.minimumFailures = session.maximumFailures = 3;
		session.twoTickCatches = 20; session.twoTickFailures = 5; session.twoTickTiming = true;
		session.adaptiveCatches = 30; session.adaptiveFailureUpper = 500; session.adaptiveTiming = true;
		session.modeledCatches = 40; session.modeledFailureLower = 10; session.modeledFailureUpper = 11; session.modeledTiming = true;
		store.save(Collections.singletonList(session));
		com.attempttracker.core.FishingSessions book = new com.attempttracker.core.FishingSessions(); book.restore(store.load());
		FishingSession restored = book.current(); assertEquals(session.id, restored.id); assertTrue(restored.modeledTiming);
		assertEquals(40, restored.modeledCatches); assertEquals(10, restored.modeledFailureLower); assertEquals(11, restored.modeledFailureUpper);
		assertEquals(40.0 / 51, restored.rate(false), 0); assertEquals(0.8, restored.rate(true), 0); assertEquals(500, restored.adaptiveFailureUpper);
		Filepath csv = directory.join("modeled.csv"); store.exportCsv(book.snapshots(), csv); String text = readString(csv);
		assertTrue(text.contains("modeled_catches,modeled_fail_min,modeled_fail_max,modeled_timing\r\n"));
		assertTrue(text.contains(",100,10,10,11,0.7843137254901961,0.8,"));
		assertTrue(text.contains(",30,500,true,40,20,5,true,40,10,11,true\r\n"));
		book.reset(); store.save(book.snapshots());
		assertEquals(0, store.load().get(0).modeledCatches); assertEquals(40, store.load().get(1).modeledCatches);
	}
	@Test public void adaptiveOnlyCsvRetainsRawEvidenceWithoutInventingRate() throws Exception
	{
		Filepath directory = Filepath.Unchecked.getRooted(folder.getRoot().toPath()); FishingSessionStore store = new FishingSessionStore(directory, new Gson());
		FishingSession session = new FishingSession(); session.catches = session.adaptiveCatches = 30;
		session.adaptiveFailureUpper = 60; session.adaptiveTiming = true;
		Filepath csv = directory.join("unknown.csv"); store.exportCsv(Collections.singletonList(session), csv); String text = readString(csv);
		assertTrue(text.contains(",30,0,0,0,,,")); assertTrue(text.contains(",30,60,true,0,0,0,false,0,0,0,false\r\n"));
	}
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

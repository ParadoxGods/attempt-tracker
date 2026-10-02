package com.attempttracker;

import com.attempttracker.core.AttemptSession;
import com.attempttracker.core.AttemptTrackerEngine;
import com.attempttracker.store.SessionStore;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import net.runelite.client.callback.ClientThread;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Regression checks for delayed I/O and callbacks surviving a disable/re-enable. */
public class AttemptTrackerLifecycleTest
{
	@Rule
	public TemporaryFolder temporary = new TemporaryFolder();

	@Test
	public void shutdownCancelsOlderSaveAndPersistsTheFinalExcludedWindow() throws Exception
	{
		Path directory = temporary.newFolder().toPath();
		SessionStore store = new SessionStore(directory, new Gson());
		ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
		AttemptTrackerPlugin plugin = initialized(store, worker);
		try
		{
			AttemptTrackerEngine engine = (AttemptTrackerEngine) get(plugin, "engine");
			engine.startTimed(100, "Fishing: Shark", "Crystal harpoon", 5);
			engine.observeTimed(100, "Fishing: Shark", "Crystal harpoon", 5, true, 0);
			engine.observeTimed(101, "Fishing: Shark", "Crystal harpoon", 5, true, 0);
			invoke(plugin, "publish");
			ScheduledFuture<?> olderSave = (ScheduledFuture<?>) get(plugin, "pendingSave");
			plugin.shutDown();
			assertTrue(olderSave.isCancelled());
			assertTrue(worker.isTerminated());
			AttemptSession saved = store.load().get(0);
			assertEquals(1, saved.getExcludedWindows());
			assertEquals(0, saved.getAttempts());
		}
		finally
		{
			worker.shutdownNow();
			SwingUtilities.invokeAndWait(() -> {});
		}
	}

	@Test
	public void queuedClientActionCannotClearOrPauseANewRun() throws Exception
	{
		AttemptTrackerPlugin plugin = new AttemptTrackerPlugin();
		ClientThread clientThread = mock(ClientThread.class);
		AtomicReference<Runnable> queued = new AtomicReference<>();
		doAnswer(invocation -> { queued.set(invocation.getArgument(0)); return null; })
			.when(clientThread).invoke(any(Runnable.class));
		set(plugin, "clientThread", clientThread);
		set(plugin, "running", true);
		set(plugin, "generation", 1L);
		AtomicInteger mutations = new AtomicInteger();
		invoke(plugin, "invokeClient", new Class<?>[]{long.class, Runnable.class}, 1L, (Runnable) mutations::incrementAndGet);
		set(plugin, "generation", 2L);
		queued.get().run();
		assertEquals(0, mutations.get());
		invoke(plugin, "invokeClient", new Class<?>[]{long.class, Runnable.class}, 2L, (Runnable) mutations::incrementAndGet);
		queued.get().run();
		assertEquals(1, mutations.get());
		invoke(plugin, "invokeClient", new Class<?>[]{long.class, Runnable.class}, 2L, (Runnable) mutations::incrementAndGet);
		set(plugin, "running", false);
		queued.get().run();
		assertEquals(1, mutations.get());
	}

	@Test
	public void queuedIoFromAnOldGenerationIsSkippedAndStoppedWorkerIsSafe() throws Exception
	{
		AttemptTrackerPlugin plugin = new AttemptTrackerPlugin();
		ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
		CountDownLatch started = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		AtomicInteger actions = new AtomicInteger();
		set(plugin, "running", true);
		set(plugin, "generation", 1L);
		set(plugin, "io", worker);
		try
		{
			worker.execute(() ->
			{
				started.countDown();
				try { release.await(2, TimeUnit.SECONDS); }
				catch (InterruptedException ex) { Thread.currentThread().interrupt(); }
			});
			assertTrue(started.await(2, TimeUnit.SECONDS));
			invoke(plugin, "submitIo", new Class<?>[]{long.class, ScheduledExecutorService.class, Runnable.class},
				1L, worker, (Runnable) actions::incrementAndGet);
			set(plugin, "generation", 2L);
			release.countDown();
			worker.shutdown();
			assertTrue(worker.awaitTermination(2, TimeUnit.SECONDS));
			assertEquals(0, actions.get());
			invoke(plugin, "submitIo", new Class<?>[]{long.class, ScheduledExecutorService.class, Runnable.class},
				2L, worker, (Runnable) actions::incrementAndGet);
			assertEquals(0, actions.get());
		}
		finally
		{
			release.countDown();
			worker.shutdownNow();
		}
	}

	private static AttemptTrackerPlugin initialized(SessionStore store, ScheduledExecutorService worker) throws Exception
	{
		AttemptTrackerPlugin plugin = new AttemptTrackerPlugin();
		set(plugin, "config", mock(AttemptTrackerConfig.class));
		set(plugin, "store", store);
		set(plugin, "io", worker);
		set(plugin, "running", true);
		set(plugin, "generation", 1L);
		return plugin;
	}

	private static Object get(Object target, String name) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static void set(Object target, String name, Object value) throws Exception
	{
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(target, value);
	}

	private static void invoke(Object target, String name) throws Exception
	{
		invoke(target, name, new Class<?>[0]);
	}

	private static void invoke(Object target, String name, Class<?>[] types, Object... arguments) throws Exception
	{
		Method method = target.getClass().getDeclaredMethod(name, types);
		method.setAccessible(true);
		method.invoke(target, arguments);
	}
}

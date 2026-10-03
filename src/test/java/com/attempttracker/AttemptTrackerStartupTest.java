package com.attempttracker;

import com.attempttracker.core.FishingSession;
import com.attempttracker.core.FishingSessions;
import com.attempttracker.store.FishingSessionStore;
import com.attempttracker.ui.LureDisplay;
import com.google.gson.Gson;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Actual startup on the EDT with client-thread assertions, including in-game hot reload. */
public class AttemptTrackerStartupTest
{
	@Test
	public void enablingWhileLoggedInCreatesSidebarBeforeClientOnlyReads() throws Exception
	{
		Fixture fixture = new Fixture();
		try
		{
			fixture.start();
			assertTrue((Boolean) get(fixture.plugin, "running"));
			assertNotNull(get(fixture.plugin, "panel"));
			verify(fixture.client, never()).getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY);
			ArgumentCaptor<NavigationButton> button = ArgumentCaptor.forClass(NavigationButton.class);
			verify(fixture.toolbar).addNavigation(button.capture());
			assertEquals(32, button.getValue().getIcon().getWidth());
			assertEquals(32, button.getValue().getIcon().getHeight());
			assertNotNull(button.getValue().getPanel());
			assertEquals("--", ((LureDisplay) get(fixture.plugin, "lureSnapshot")).amount);
			fixture.runClientCallback();
			assertEquals("3 per catch", ((LureDisplay) get(fixture.plugin, "lureSnapshot")).amount);
			FishingSessions sessions = (FishingSessions) get(fixture.plugin, "fishingSessions");
			assertEquals(fixture.saved.id, sessions.current().id);
			assertEquals(7, sessions.current().catches);
			assertEquals(2, sessions.current().minimumFailures);
		}
		finally { fixture.stop(); }
	}

	@Test
	public void staleStartupCallbackCannotReadGameStateOrOverwriteReenabledPlugin() throws Exception
	{
		Fixture fixture = new Fixture();
		try
		{
			fixture.start();
			Runnable oldStartup = fixture.clientActions.remove();
			fixture.stop();
			fixture.start();
			oldStartup.run();
			verify(fixture.client, never()).getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY);
			assertEquals("--", ((LureDisplay) get(fixture.plugin, "lureSnapshot")).amount);
			fixture.runClientCallback();
			assertEquals("3 per catch", ((LureDisplay) get(fixture.plugin, "lureSnapshot")).amount);
			assertEquals(fixture.saved.id, ((FishingSessions) get(fixture.plugin, "fishingSessions")).current().id);
			assertNotNull(get(fixture.plugin, "navigation"));
			verify(fixture.toolbar, times(2)).addNavigation(any(NavigationButton.class));
		}
		finally { fixture.stop(); }
	}

	@Test
	public void runeLiteSettingsDescriptorStillContainsTheVisibleControls() throws Exception
	{
		ConfigManager manager = mock(ConfigManager.class, CALLS_REAL_METHODS);
		AttemptTrackerConfig config = mock(AttemptTrackerConfig.class, CALLS_REAL_METHODS);
		ConfigDescriptor descriptor = manager.getConfigDescriptor(config);
		List<String> visible = descriptor.getItems().stream().filter(item -> !item.getItem().hidden())
			.map(item -> item.getItem().keyName()).collect(Collectors.toList());
		assertTrue(visible.contains("showOverlay"));
		assertTrue(visible.contains("diagnostics"));
		assertTrue(visible.contains("saveTrace"));
		assertFalse(descriptor.getSections().stream().anyMatch(section -> section.getSection().name().equals("Fishing")));
	}

	@PluginDescriptor(name = "Attempt Tracker startup test", internalName = "attempt-tracker-startup-test")
	public static class StartupPlugin extends AttemptTrackerPlugin { }

	private static final class Fixture
	{
		final StartupPlugin plugin = new StartupPlugin();
		final Client client = mock(Client.class);
		final ClientToolbar toolbar = mock(ClientToolbar.class);
		final ConcurrentLinkedQueue<Runnable> clientActions = new ConcurrentLinkedQueue<>();
		final FishingSession saved = new FishingSession();

		Fixture() throws Exception
		{
			// Gradle isolates the real SDK data-directory API from the player's files.
			assertTrue("Run startup tests with Gradle's isolated test home", System.getProperty("user.home").endsWith("test-home"));
			Filepath directory = Filepath.Unchecked.getRooted(RuneLite.PLUGIN_DATA).join("attempt-tracker-startup-test").rooted();
			saved.catches = saved.measuredCatches = 7; saved.minimumFailures = saved.maximumFailures = 2;
			saved.loggedMillis = 5000; saved.fishingMillis = 3000;
			new FishingSessionStore(directory, new Gson()).save(Collections.singletonList(saved));
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenAnswer(call ->
			{
				if (SwingUtilities.isEventDispatchThread()) { throw new IllegalStateException("must be called on client thread"); }
				return 3;
			});
			ItemContainer inventory = mock(ItemContainer.class);
			when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SHARK_LURE, 300)});
			when(inventory.count(ItemID.SHARK_LURE)).thenReturn(300);
			when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
			ClientThread clientThread = mock(ClientThread.class);
			doAnswer(call -> { clientActions.add(call.getArgument(0)); return null; }).when(clientThread).invoke(any(Runnable.class));
			set(plugin, "client", client); set(plugin, "clientThread", clientThread);
			set(plugin, "config", mock(AttemptTrackerConfig.class, CALLS_REAL_METHODS));
			set(plugin, "itemManager", mock(ItemManager.class)); set(plugin, "gson", new Gson());
			set(plugin, "clientToolbar", toolbar); set(plugin, "overlayManager", mock(OverlayManager.class));
		}

		void start() throws Exception
		{
			SwingUtilities.invokeAndWait(() -> { try { plugin.startUp(); } catch (Exception ex) { throw new IllegalStateException(ex); } });
			SwingUtilities.invokeAndWait(() -> {});
		}
		void runClientCallback() throws Exception
		{
			assertFalse(SwingUtilities.isEventDispatchThread()); clientActions.remove().run();
			SwingUtilities.invokeAndWait(() -> {});
		}
		void stop() throws Exception
		{
			SwingUtilities.invokeAndWait(plugin::shutDown); SwingUtilities.invokeAndWait(() -> {});
		}
	}

	private static Object get(AttemptTrackerPlugin plugin, String name) throws Exception
	{
		Field field = AttemptTrackerPlugin.class.getDeclaredField(name); field.setAccessible(true); return field.get(plugin);
	}
	private static void set(AttemptTrackerPlugin plugin, String name, Object value) throws Exception
	{
		Field field = AttemptTrackerPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
	}
}

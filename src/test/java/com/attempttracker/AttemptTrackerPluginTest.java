package com.attempttracker;

import com.attempttracker.core.AttemptSession;
import com.attempttracker.core.AttemptTrackerEngine;
import com.attempttracker.core.TrackingMethod;
import com.attempttracker.diagnostics.TickTrace;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import net.runelite.client.util.Filepath;
import static com.attempttracker.FilepathTestSupport.*;
import java.util.EnumMap;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemManager;
import org.junit.Test;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Packet/chat-before-GameTick fixtures for the real client adapter. */
public class AttemptTrackerPluginTest
{
	@Rule
	public final TemporaryFolder temporaryFolder = new TemporaryFolder();

	@Test
	public void explicitSharkStartCountsDueSuccessAndSilentFailure() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5);
		AttemptSession session = h.engine.getCurrentSession();
		assertEquals(TrackingMethod.TIMED, session.getMethod());
		assertEquals("Fishing: Shark", session.getActivity());
		assertEquals(5, session.getCycleTicks());
		assertCounts(session, 1, 1, 0);
		assertEquals(0.5, session.getSuccessRate(), 0.0);
	}

	@Test
	public void explicitStartCountsEveryFiveTickAttemptWithoutAnyCatches() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		assertCounts(h.engine.getCurrentSession(), 0, 0, 0);
		h.ticks(1, 4);
		assertCounts(h.engine.getCurrentSession(), 0, 0, 0);
		h.ticks(5, 15);
		assertCounts(h.engine.getCurrentSession(), 0, 3, 0);
	}

	@Test
	public void catchesAtFiveAndFifteenProduceThreeAttemptsWithOneFailure() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15, 5, 15);
		assertCounts(h.engine.getCurrentSession(), 2, 1, 0);
		assertEquals(2.0 / 3.0, h.engine.getCurrentSession().getSuccessRate(), 0.0);
	}

	@Test
	public void liveIslandNpcAndAutomaticFirstDelayCountTheActualFourThenFiveTickPhase() throws Exception
	{
		Harness h = new Harness(true);
		h.liveIslandSharks();
		h.startSharks();
		h.tick(0);
		for (int tick = 1; tick <= 19; tick++)
		{
			if (tick == 4 || tick == 9 || tick == 19) { h.liveIslandCatch(); }
			h.tick(tick);
		}
		AttemptSession session = h.find(TrackingMethod.TIMED);
		assertEquals("Fishing: Shark", session.getActivity());
		assertEquals(5, session.getCycleTicks());
		assertTrue(session.getSetup().contains("region: 10274"));
		assertTrue(session.getSetup().contains("first roll delay: 4"));
		assertCounts(session, 3, 1, 0);
		assertEquals(Integer.valueOf(66), h.experience.get(Skill.FISHING));
		assertEquals(291, h.inventory.count(ItemID.SHARK_LURE));
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void automaticLiveIslandPhaseCountsThreeSilentFailuresByFourteenAndStops() throws Exception
	{
		Harness h = new Harness(true);
		h.liveIslandSharks();
		h.startSharks();
		h.tick(0);
		h.ticks(1, 3);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 0);
		h.ticks(4, 14);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 3, 0);
		when(h.player.getAnimation()).thenReturn(-1);
		when(h.player.getInteracting()).thenReturn(null);
		h.ticks(15, 25);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 3, 0);
		assertFalse(h.engine.isTimingActive());
		assertEquals(300, h.inventory.count(ItemID.SHARK_LURE));
	}

	@Test
	public void automaticFirstDelayAlsoAppliesToRuneLitesKnownSharkSpots() throws Exception
	{
		Harness h = new Harness(true);
		when(h.config.sharkFirstRollDelay()).thenReturn(0);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 9, 4);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void liveIslandNpcInteractionCanSupplyTheCachedStartTarget() throws Exception
	{
		Harness h = new Harness(true);
		h.liveIslandSharks();
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 14);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 3, 0);
	}

	@Test
	public void liveIslandNpcMenuCanSupplyTheCachedStartTargetAndKeepTheObservedPhase() throws Exception
	{
		Harness h = new Harness(true);
		h.liveIslandSharks();
		when(h.player.getInteracting()).thenReturn(null);
		h.clickNpc(h.spot, "Harpoon");
		h.startSharks();
		h.tick(0);
		for (int tick = 1; tick <= 14; tick++)
		{
			if (tick == 4 || tick == 9) { h.liveIslandCatch(); }
			h.tick(tick);
		}
		assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
		assertEquals(Integer.valueOf(44), h.experience.get(Skill.FISHING));
		assertEquals(294, h.inventory.count(ItemID.SHARK_LURE));
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void clearedLiveInteractionDoesNotStopAnActiveFishingSchedule() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 5, 5);
		when(h.player.getInteracting()).thenReturn(null);
		h.interaction(h.player, null);
		h.ticks(6, 15, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void cachedFishingInteractionCanAnchorWhenTheLiveTargetIsAlreadyNull() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.interaction(h.player, null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15, 5, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void clickedFishingNpcCanIdentifyTheTargetWithoutALiveInteraction() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getInteracting()).thenReturn(null);
		h.clickNpc(h.spot, "Harpoon");
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 3, 0);
	}

	@Test
	public void matchingGearAndRegionCannotSupplyAnUnknownFishingTarget() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(2560, 2176, 0));
		ItemContainer equipment = mock(ItemContainer.class);
		when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.CRYSTAL_HARPOON, 1)});
		when(h.client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void despawnedCachedFishingNpcCannotProduceLaterSilentFailures() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		h.plugin.onNpcDespawned(new NpcDespawned(h.spot));
		h.ticks(7, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void movedCachedFishingNpcCancelsItsScheduleAndCannotBeReused() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		when(h.spot.getWorldLocation()).thenReturn(new WorldPoint(3200, 3202, 0));
		h.tick(7);
		h.startSharks();
		h.tick(8);
		h.ticks(9, 18);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void switchingToAnotherValidCachedNpcRequiresANewStart() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		NPC replacement = mock(NPC.class);
		when(replacement.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]);
		when(replacement.getName()).thenReturn("Fishing spot");
		when(replacement.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
		when(replacement.getWorldView()).thenReturn(h.worldView);
		h.interaction(h.player, replacement);
		h.ticks(7, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void explicitStartOnANewCachedNpcBeginsItsOwnPhaseAndRetainsCompletedAttempts() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		NPC replacement = mock(NPC.class);
		when(replacement.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]);
		when(replacement.getName()).thenReturn("Fishing spot");
		when(replacement.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
		when(replacement.getWorldView()).thenReturn(h.worldView);
		h.interaction(h.player, replacement);
		h.startSharks();
		h.tick(7);
		h.ticks(8, 11);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		h.ticks(12, 17, 12);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 1);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void cachedNpcInAnotherWorldViewCannotAnchorATimer() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.spot.getWorldView()).thenReturn(mock(WorldView.class));
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void distantCachedNpcCannotAnchorATimerFromAStartMessageAlone() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3203, 3200, 0));
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void walkingToTheClickedNpcKeepsTheCandidateUntilTheActualStart() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.player.getAnimation()).thenReturn(-1);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3203, 3200, 0));
		h.clickNpc(h.spot, "Harpoon");
		h.tick(0);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3202, 3200, 0));
		h.tick(1);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
		when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_HARPOON_CRYSTAL);
		h.startSharks();
		h.tick(2);
		h.ticks(3, 12, 7);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
	}

	@Test
	public void unsupportedNpcInteractionClearsTheCachedFishingTarget() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		NPC unrelated = mock(NPC.class);
		when(unrelated.getId()).thenReturn(123456);
		h.interaction(h.player, unrelated);
		h.ticks(7, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void anotherActorsInteractionDoesNotClearTheLocalFishingTarget() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.interaction(mock(Player.class), mock(NPC.class));
		h.ticks(1, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 3, 0);
	}

	@Test
	public void fishingMenuTextCannotIdentifyAnUnsupportedClickedNpc() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getInteracting()).thenReturn(null);
		NPC unrelated = mock(NPC.class);
		when(unrelated.getId()).thenReturn(123456);
		h.clickNpc(unrelated, "Harpoon");
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
	}

	@Test
	public void unrelatedMenuActionDiscardsTheCachedFishingTarget() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		MenuEntry entry = mock(MenuEntry.class);
		when(entry.getType()).thenReturn(MenuAction.WALK);
		when(entry.getOption()).thenReturn("Walk here");
		when(entry.getTarget()).thenReturn("");
		h.plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
		h.startSharks();
		h.tick(7);
		h.ticks(8, 17);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void cachedTargetPreservesTheFinalDueCatchAfterBothLiveFieldsClear() throws Exception
	{
		Harness h = new Harness(true);
		h.interaction(h.player, h.spot);
		when(h.player.getInteracting()).thenReturn(null);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 4);
		when(h.player.getAnimation()).thenReturn(-1);
		h.catchMessage();
		h.chat(ChatMessageType.SPAM, "Your inventory is too full to hold any more fish.");
		h.tick(5);
		h.ticks(6, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
		assertEquals(0L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void catchesWithoutAStartRemainSuccessOnlyEvenWithFixedTimingEnabled() throws Exception
	{
		Harness h = new Harness(true);
		h.tick(0);
		h.ticks(1, 15, 5, 15);
		assertSuccessOnly(h.engine.getCurrentSession(), 2);
		assertFalse(h.engine.isTimingActive());
		assertEquals(1, h.engine.getSessions().size());
	}

	@Test
	public void repeatedStartResetsPhaseWithoutCountingTheStartAsAnAttempt() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 2);
		h.startSharks();
		h.tick(3);
		h.ticks(4, 7);
		assertCounts(h.engine.getCurrentSession(), 0, 0, 1);
		h.ticks(8, 13, 8);
		assertCounts(h.engine.getCurrentSession(), 1, 1, 1);
	}

	@Test
	public void playerChatCannotForgeStartsCatchOrCookingOutcomes() throws Exception
	{
		Harness h = new Harness(true);
		h.chat(ChatMessageType.PRIVATECHAT, "You start harpooning fish.");
		h.chat(ChatMessageType.PUBLICCHAT, "You cast out your line.");
		h.chat(ChatMessageType.PRIVATECHAT, "You catch a shark.");
		h.chat(ChatMessageType.PUBLICCHAT, "You successfully cook a shark.");
		h.ticks(0, 15);
		assertTrue(h.engine.getSessions().isEmpty());
	}

	@Test
	public void sharksAutomaticallyUseSupportedTimingWithoutAdvancedEstimateSwitch() throws Exception
	{
		Harness h = new Harness(false);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5, 10);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 0, 0);
	}

	@Test
	public void liveExclamationSharkCatchRecordsSuccessThroughTheWholeAdapter() throws Exception
	{
		Harness h = new Harness(false);
		h.tick(0);
		h.chat(ChatMessageType.SPAM, "You catch a shark!");
		h.experience.put(Skill.FISHING, 110);
		h.tick(1);
		AttemptSession session = h.engine.getCurrentSession();
		assertNotNull("The live catch message must create a visible session", session);
		assertEquals("Fishing: Shark", session.getActivity());
		assertSuccessOnly(session, 1);
	}

	@Test
	public void variableSharkLureModesStaySuccessOnlyEvenWithAllFishingEnabled() throws Exception
	{
		for (AttemptTrackerConfig.SharkLures mode : new AttemptTrackerConfig.SharkLures[]{
			AttemptTrackerConfig.SharkLures.ONE, AttemptTrackerConfig.SharkLures.FIVE})
		{
			Harness h = new Harness(true);
			when(h.config.sharkLures()).thenReturn(mode);
			when(h.config.sharksOnly()).thenReturn(false);
			when(h.config.fishingCycle()).thenReturn(5);
			h.startSharks();
			h.tick(0);
			h.ticks(1, 10, 5, 10);
			assertSuccessOnly(h.engine.getCurrentSession(), 2);
		}
	}

	@Test
	public void fullInventoryMessageAfterCatchDoesNotDeleteVisibleSuccess() throws Exception
	{
		Harness h = new Harness(false);
		h.tick(0);
		h.catchMessage();
		h.chat(ChatMessageType.SPAM, "Your inventory is too full to hold any more fish.");
		when(h.player.getAnimation()).thenReturn(-1);
		h.tick(1);
		assertSuccessOnly(h.engine.getCurrentSession(), 1);
	}

	@Test
	public void dueCatchAndFullInventoryInTheSamePacketCountTheTerminalSuccess() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 4);
		h.catchMessage();
		h.chat(ChatMessageType.SPAM, "Your inventory is too full to hold any more fish.");
		when(h.player.getAnimation()).thenReturn(-1);
		when(h.player.getInteracting()).thenReturn(null);
		h.tick(5);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
		assertEquals(0L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
		h.ticks(6, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
	}

	@Test
	public void interruptedCatchRetainsItsSetupAtMessageTime() throws Exception
	{
		Harness h = new Harness(false);
		h.tick(0);
		h.catchMessage();
		when(h.client.getBoostedSkillLevel(Skill.FISHING)).thenReturn(100);
		h.chat(ChatMessageType.SPAM, "Your inventory is too full to hold any more fish.");
		h.tick(1);
		AttemptSession session = h.engine.getCurrentSession();
		assertSuccessOnly(session, 1);
		assertTrue(session.getSetup().contains("boosted 99"));
		assertFalse(session.getSetup().contains("boosted 100"));
	}

	@Test
	public void offDeadlineCatchOnSpotDespawnPreservesBothCatchesWithoutFalseTrials() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		h.catchMessage();
		h.plugin.onNpcDespawned(new NpcDespawned(h.spot));
		when(h.player.getAnimation()).thenReturn(-1);
		when(h.player.getInteracting()).thenReturn(null);
		h.tick(7);
		assertEquals(2L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		h.ticks(8, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
	}

	@Test
	public void dueCatchOnSpotDespawnCountsAndStopsTheSchedule() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 9, 5);
		h.catchMessage();
		h.plugin.onNpcDespawned(new NpcDespawned(h.spot));
		when(h.player.getAnimation()).thenReturn(-1);
		when(h.player.getInteracting()).thenReturn(null);
		h.ticks(10, 20);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 0, 0);
		assertEquals(0L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void multipleCatchMessagesStayVisibleAndRequireANewExplicitStart() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 4);
		h.catchMessage();
		h.catchMessage();
		h.tick(5);
		assertEquals(2L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		h.ticks(6, 15, 10);
		assertEquals(3L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		h.startSharks();
		h.tick(16);
		h.ticks(17, 26, 21, 26);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 0, 1);
	}

	@Test
	public void multipleCatchAmbiguityRemovesEarlierAssumedTrialsButPreservesAllVisibleCatches() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
		h.ticks(11, 14);
		h.catchMessage();
		h.catchMessage();
		h.tick(15);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		assertEquals(3L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void finalDueCatchCountsWhenAnimationAlreadyEnded() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 4);
		when(h.player.getAnimation()).thenReturn(-1);
		h.catchMessage();
		h.tick(5);
		assertCounts(h.engine.getCurrentSession(), 1, 0, 0);
		h.ticks(6, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
	}

	@Test
	public void unknownXpGainInvalidatesRatherThanCountingAFailure() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 2);
		h.experience.put(Skill.FISHING, 220);
		h.tick(3);
		h.ticks(4, 5, 5);
		assertEquals(1L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		h.startSharks();
		h.tick(6);
		h.ticks(7, 11, 11);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
	}

	@Test
	public void unknownXpAmbiguityRemovesEarlierAssumedTrialsAndPreservesTheKnownCatch() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
		h.experience.put(Skill.FISHING, 220);
		h.tick(11);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		assertEquals(1L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
		h.ticks(12, 20);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		assertEquals(1L, h.successOnlyCatchCount());
	}

	@Test
	public void offDeadlineCatchInvalidatesScheduleInsteadOfBecomingANewAnchor() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 5, 5);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
		h.ticks(6, 15, 9, 14);
		assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
		assertEquals(3L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void movementExcludesThePartialCycleAndRequiresAnotherStart() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
		h.tick(7);
		h.ticks(8, 12, 12);
		assertEquals(1L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
		h.startSharks();
		h.tick(13);
		h.ticks(14, 18, 18);
		assertCounts(h.find(TrackingMethod.TIMED), 2, 0, 1);
	}

	@Test
	public void movementOnDueCatchTickCountsTheKnownSuccessAndStopsFutureTrials() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 4);
		h.catchMessage();
		when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0));
		h.tick(5);
		assertEquals(0L, h.successOnlyCatchCount());
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
		h.ticks(6, 15);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 0);
	}

	@Test
	public void idleAndStoppedPlayersNeverAccumulateSilentFailures() throws Exception
	{
		Harness idle = new Harness(true);
		when(idle.player.getAnimation()).thenReturn(-1);
		when(idle.player.getInteracting()).thenReturn(null);
		idle.ticks(0, 15);
		assertTrue(idle.engine.getSessions().isEmpty());

		Harness stopped = new Harness(true);
		stopped.startSharks();
		stopped.tick(0);
		stopped.ticks(1, 5, 5);
		when(stopped.player.getAnimation()).thenReturn(-1);
		when(stopped.player.getInteracting()).thenReturn(null);
		stopped.ticks(6, 20);
		assertCounts(stopped.find(TrackingMethod.TIMED), 1, 0, 0);
		assertFalse(stopped.engine.isTimingActive());
	}

	@Test
	public void newSessionCannotReuseThePreviousStart() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		String previousId = h.engine.getCurrentSession().getId();
		h.engine.startNewSession();
		invoke(h.plugin, "breakTiming");
		h.ticks(7, 11, 11);
		assertNotEquals(previousId, h.engine.getCurrentSession().getId());
		assertSuccessOnly(h.engine.getCurrentSession(), 1);
		assertEquals(2, h.engine.getSessions().size());
		assertCounts(h.find(TrackingMethod.TIMED), 1, 0, 1);
	}

	@Test
	public void levelChangeSplitsProfilesAndRequiresANewStart() throws Exception
	{
		Harness h = new Harness(true);
		when(h.client.getRealSkillLevel(Skill.FISHING)).thenReturn(98);
		when(h.client.getBoostedSkillLevel(Skill.FISHING)).thenReturn(98);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 6, 5);
		when(h.client.getRealSkillLevel(Skill.FISHING)).thenReturn(99);
		when(h.client.getBoostedSkillLevel(Skill.FISHING)).thenReturn(99);
		h.ticks(7, 12, 12);
		assertSuccessOnly(h.engine.getCurrentSession(), 1);
		assertTrue(h.engine.getCurrentSession().getSetup().contains("Fishing 99"));
		h.startSharks();
		h.tick(13);
		h.ticks(14, 18, 18);
		assertCounts(h.engine.getCurrentSession(), 1, 0, 0);
		assertTrue(h.engine.getCurrentSession().getSetup().contains("Fishing 99"));
		assertCounts(h.find(TrackingMethod.TIMED, "Fishing 98"), 1, 0, 1);
		assertEquals(1L, h.successOnlyCatchCount());
	}

	@Test
	public void knownNoLuresUseSixTickCyclesInsteadOfConfiguredThreeLureCycle() throws Exception
	{
		Harness h = new Harness(true);
		when(h.config.sharkFirstRollDelay()).thenReturn(6);
		when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0);
		when(h.inventory.getItems()).thenReturn(new Item[0]);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 12, 6);
		AttemptSession session = h.engine.getCurrentSession();
		assertEquals(6, session.getCycleTicks());
		assertCounts(session, 1, 1, 0);
	}

	@Test
	public void unknownTackleBoxSuppliesCannotBeAssignedAFixedLureCycle() throws Exception
	{
		Harness h = new Harness(true);
		when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0);
		when(h.inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.TACKLE_BOX, 1)});
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5, 10);
		assertSuccessOnly(h.engine.getCurrentSession(), 2);
		assertTrue(h.engine.getCurrentSession().getSetup().contains("contents unverified"));
	}

	@Test
	public void declaredNoLuresCannotOverrideUnknownTackleBoxContents() throws Exception
	{
		Harness h = new Harness(true);
		when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.NONE);
		when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0);
		when(h.inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.TACKLE_BOX, 1)});
		h.startSharks();
		h.tick(0);
		h.ticks(1, 10, 5, 10);
		assertSuccessOnly(h.engine.getCurrentSession(), 2);
		assertTrue(h.engine.getCurrentSession().getSetup().contains("contents unverified"));
	}

	@Test
	public void lureSettingChangeDoesNotInvalidateUnrelatedMiningEstimate() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_MINING_RUNE_PICKAXE);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.config.miningCycle()).thenReturn(4);
		set(h.plugin, "selectedObject", new WorldPoint(3200, 3201, 0));
		set(h.plugin, "selectedTarget", "Iron rocks");
		h.chat(ChatMessageType.SPAM, "You swing your pick at the rock.");
		h.tick(0);
		for (int tick = 1; tick <= 8; tick++)
		{
			if (tick == 5) { when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(2); }
			if (tick == 4)
			{
				h.chat(ChatMessageType.SPAM, "You manage to mine some iron.");
				h.experience.put(Skill.MINING, 35);
			}
			h.tick(tick);
		}
		assertEquals("Mining: Iron rocks", h.engine.getCurrentSession().getActivity());
		assertCounts(h.engine.getCurrentSession(), 1, 1, 0);
	}

	@Test
	public void changingRawLureSettingRequiresRenewedConfirmationAndAStart() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 2);
		when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(2);
		h.ticks(3, 13, 5, 10);
		for (AttemptSession session : h.engine.getSessions())
		{
			if (session.getMethod() == TrackingMethod.TIMED) { assertEquals(0L, session.getAttempts()); }
		}
		assertEquals(2L, h.successOnlyCatchCount());
	}

	@Test
	public void explicitFixedTimingConfirmationStillNeedsANewStart() throws Exception
	{
		Harness h = new Harness(true);
		h.startSharks();
		h.tick(0);
		h.ticks(1, 2);
		when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(2);
		h.tick(3);
		h.ticks(4, 10, 5, 10);
		assertEquals(2L, h.successOnlyCatchCount());
		ConfigChanged changed = new ConfigChanged();
		changed.setGroup(AttemptTrackerConfig.GROUP);
		changed.setKey("fixedTiming");
		changed.setNewValue("true");
		h.plugin.onConfigChanged(changed);
		h.ticks(11, 15, 15);
		assertEquals(3L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
		h.startSharks();
		h.tick(16);
		h.ticks(17, 26, 21, 26);
		assertEquals(TrackingMethod.TIMED, h.engine.getCurrentSession().getMethod());
		assertCounts(h.engine.getCurrentSession(), 2, 0, 0);
	}

	@Test
	public void builtinSuccessCannotContaminateACustomTimerWithTheSameSkill() throws Exception
	{
		Harness h = new Harness(true);
		h.enableCustom();
		h.startCustom();
		h.tick(0);
		h.ticks(1, 10, 5, 10);
		for (AttemptSession session : h.engine.getSessions())
		{
			if (session.getActivity().equals("Custom shark") && session.getMethod() == TrackingMethod.TIMED)
			{
				assertEquals("Built-in catches must not enter the custom estimator", 0L, session.getAttempts());
			}
		}
		assertEquals(2L, h.successOnlyCatchCount());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void customStartAndSuccessWithoutItsAnimationCannotAnchorBuiltinFishing() throws Exception
	{
		Harness h = new Harness(true);
		h.enableCustom();
		when(h.config.customAnimations()).thenReturn("123456");
		set(h.plugin, "custom", new CustomActivity(h.config));
		h.startCustom();
		h.tick(0);
		h.chat(ChatMessageType.SPAM, "A custom success occurred.");
		h.tick(1);
		AttemptSession session = h.engine.getCurrentSession();
		assertSuccessOnly(session, 1);
		assertEquals("Custom shark", session.getActivity());
		assertTrue(session.getSetup().contains("custom definition:"));
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void builtinStartCannotActivateACustomTimerSharingTheAnimation() throws Exception
	{
		Harness h = new Harness(true);
		h.enableCustom();
		h.startSharks();
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void castingLineStartCannotActivateTheSharkHarpoonTimer() throws Exception
	{
		Harness h = new Harness(true);
		h.chat(ChatMessageType.SPAM, "You cast out your line.");
		h.tick(0);
		h.ticks(1, 15);
		assertTrue(h.engine.getSessions().isEmpty());
		assertFalse(h.engine.isTimingActive());
	}

	@Test
	public void castingStartKeepsItsPhaseWhenTheRodChangesFromCastingToFishingPose() throws Exception
	{
		Harness h = new Harness(true);
		when(h.config.sharksOnly()).thenReturn(false);
		when(h.config.fishingCycle()).thenReturn(5);
		when(h.spot.getId()).thenReturn(FishingSpot.SALMON.getIds()[0]);
		when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_FISHING_CASTING);
		h.chat(ChatMessageType.GAMEMESSAGE, "You cast out your line.");
		h.tick(0);
		when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_FISH_ONSPOT);
		h.ticks(1, 4);
		h.chat(ChatMessageType.SPAM, "You catch a trout.");
		h.experience.put(Skill.FISHING, 50);
		h.tick(5);
		h.ticks(6, 10);
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void woodcuttingStartCountsCyclesUnderAStableTargetLabel() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_WOODCUTTING_RUNE_AXE);
		when(h.player.getInteracting()).thenReturn(null);
		when(h.config.woodcuttingCycle()).thenReturn(4);
		set(h.plugin, "selectedObject", new WorldPoint(3200, 3201, 0));
		set(h.plugin, "selectedTarget", "Oak");
		h.chat(ChatMessageType.SPAM, "You swing your axe at the tree.");
		h.tick(0);
		h.ticks(1, 3);
		h.chat(ChatMessageType.SPAM, "You get some oak logs.");
		h.experience.put(Skill.WOODCUTTING, 37);
		h.tick(4);
		h.ticks(5, 8);
		assertEquals("Woodcutting: Oak", h.engine.getCurrentSession().getActivity());
		assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void customTimerCountsFromItsOwnExplicitStartAndOutcomes() throws Exception
	{
		Harness h = new Harness(true);
		h.enableCustom();
		h.startCustom();
		h.tick(0);
		for (int tick = 1; tick <= 15; tick++)
		{
			if (tick == 5 || tick == 15)
			{
				h.chat(ChatMessageType.SPAM, "A custom success occurred.");
				h.experience.put(Skill.FISHING, h.experience.getOrDefault(Skill.FISHING, 0) + 110);
			}
			h.tick(tick);
		}
		AttemptSession session = h.engine.getCurrentSession();
		assertEquals("Custom shark", session.getActivity());
		assertEquals(TrackingMethod.TIMED, session.getMethod());
		assertTrue(session.getSetup().contains("custom definition:"));
		assertCounts(session, 2, 1, 0);
	}

	@Test
	public void customTimerCanUseAFourTickFirstRollFollowedByFiveTickCycles() throws Exception
	{
		Harness h = new Harness(true);
		h.enableCustom();
		when(h.config.customFirstRollDelay()).thenReturn(4);
		set(h.plugin, "custom", new CustomActivity(h.config));
		h.startCustom();
		h.tick(0);
		h.ticks(1, 3);
		h.chat(ChatMessageType.SPAM, "A custom success occurred.");
		h.experience.put(Skill.FISHING, 110);
		h.tick(4);
		h.ticks(5, 9);
		AttemptSession session = h.find(TrackingMethod.TIMED);
		assertEquals("Custom shark", session.getActivity());
		assertEquals(5, session.getCycleTicks());
		assertCounts(session, 1, 1, 0);
		assertEquals(0L, h.successOnlyCatchCount());
	}

	@Test
	public void unsupportedAnimationStillPreservesRecognizedCatchAsSuccessOnly() throws Exception
	{
		Harness h = new Harness(true);
		when(h.player.getAnimation()).thenReturn(-1);
		h.ticks(0, 0, 0);
		assertSuccessOnly(h.engine.getCurrentSession(), 1);
	}

	@Test
	public void enabledTraceWritesTheActualUnsupportedAnimationAndMissingContextRows() throws Exception
	{
		Harness h = new Harness(true);
		Filepath directory = Filepath.Unchecked.getRooted(temporaryFolder.newFolder("trace").toPath());
		TickTrace trace = new TickTrace(directory);
		ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1);
		try
		{
			set(h.plugin, "io", worker);
			set(h.plugin, "trace", trace);
			when(h.config.saveTrace()).thenReturn(true);
			when(h.player.getInteracting()).thenReturn(null);
			when(h.player.getAnimation()).thenReturn(123456);
			h.startSharks();
			h.tick(0);
			when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_HARPOON_CRYSTAL);
			h.tick(1);
			// The single worker runs this barrier after both actual adapter writes.
			worker.submit(() -> { trace.close(); return null; }).get(5, TimeUnit.SECONDS);

			List<Filepath> csvFiles;
			try (Stream<Filepath> files = directory.walk(1).filter(path -> !path.equals(directory)))
			{
				csvFiles = files.filter(path -> path.getFileName().endsWith(".csv"))
					.collect(Collectors.toList());
			}
			assertEquals(1, csvFiles.size());
			List<String> lines = readLines(csvFiles.get(0));
			assertEquals("Both rejected ticks must still be written", 3, lines.size());
			assertTrue(lines.get(0).contains("timing_result,live_interaction,context_problem"));
			assertTrue(lines.get(1).contains(",0,123456,"));
			assertTrue(lines.get(1).contains("\"NO_CONTEXT\""));
			assertTrue(lines.get(1).contains("\"Unsupported animation 123456\""));
			assertTrue(lines.get(2).contains(",1," + AnimationID.HUMAN_HARPOON_CRYSTAL + ","));
			assertTrue(lines.get(2).contains("\"NO_CONTEXT\""));
			assertTrue(lines.get(2).contains("\"No valid fishing NPC\""));
		}
		finally
		{
			worker.shutdown();
			assertTrue("Trace worker must terminate", worker.awaitTermination(5, TimeUnit.SECONDS));
			trace.close();
		}
	}

    @Test public void allCataloguedFishingSpotsCanRunClockWithoutSharkChatOrSchedule() throws Exception
    {
        for (FishingSpot spot : FishingSpot.values())
        {
            Harness h = new Harness(false);
            when(h.spot.getId()).thenReturn(spot.getIds()[0]);
            when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_SMALLNET);
            // Far-away regions must work just like the original shark test location.
            when(h.player.getWorldLocation()).thenReturn(new WorldPoint(1200, 3100, 0));
            when(h.spot.getWorldLocation()).thenReturn(new WorldPoint(1200, 3101, 0));
            h.clickNpc(h.spot, "Net"); h.tick(0); h.ticks(1, 8);
            com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
            assertEquals(spot.name(), 8, book.current().fishingTicks);
            assertEquals(spot.name(), 0, book.current().maximumFailures);
            assertFalse(h.engine.isTimingActive()); assertTrue(h.engine.getSessions().isEmpty());
        }
    }

    @Test public void fishingClockWaitsForArrivalThenPausesWithoutResetting() throws Exception
    {
        Harness h = new Harness(false);
        java.util.concurrent.atomic.AtomicLong nanos = new java.util.concurrent.atomic.AtomicLong();
        com.attempttracker.core.FishingSessions book = new com.attempttracker.core.FishingSessions(nanos::get);
        set(h.plugin, "fishingSessions", book);
        when(h.player.getAnimation()).thenReturn(-1);
        when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3195, 3200, 0));
        h.clickNpc(h.spot, "Harpoon"); h.tick(0);
        nanos.set(600_000_000L); when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3199, 3200, 0)); h.tick(1);
        nanos.set(1_200_000_000L); when(h.player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0)); h.tick(2);
        assertEquals(0, book.current().fishingMillis); assertEquals(0, book.current().fishingTicks);
        nanos.set(1_800_000_000L); when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_HARPOON_CRYSTAL); h.tick(3);
        nanos.set(2_400_000_000L); h.tick(4);
        assertEquals(600, book.current().fishingMillis); assertEquals(1, book.current().fishingTicks);
        nanos.set(3_000_000_000L); when(h.player.getAnimation()).thenReturn(-1); h.tick(5);
        long stopped = book.current().fishingMillis; String id = book.current().id;
        nanos.set(30_000_000_000L); h.tick(6);
        assertEquals(stopped, book.current().fishingMillis); assertEquals(id, book.current().id);
        assertEquals(30_000, book.current().loggedMillis); assertEquals(0, book.current().maximumFailures);
    }

    @Test public void interactingFishingSpotStartsRodTimerAndCastingTransitionKeepsIt() throws Exception
    {
        Harness h = new Harness(false); when(h.spot.getId()).thenReturn(FishingSpot.SHRIMP.getIds()[0]);
        when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_FISHING_CASTING);
        h.interaction(h.player, h.spot); h.tick(0); h.tick(1);
        when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_FISH_ONSPOT);
        net.runelite.api.events.AnimationChanged animation = new net.runelite.api.events.AnimationChanged(); animation.setActor(h.player);
        h.plugin.onAnimationChanged(animation); h.tick(2);
        when(h.player.getInteracting()).thenReturn(null); h.interaction(h.player, null); h.ticks(3, 5);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(5, book.current().fishingTicks); assertEquals(0, book.current().maximumFailures);
    }

    @Test public void newNamedSpotNeedsFishingActionsAndCannotInventFailures() throws Exception
    {
        Harness h = new Harness(false); when(h.spot.getId()).thenReturn(999999);
        net.runelite.api.NPCComposition definition = mock(net.runelite.api.NPCComposition.class);
        when(h.spot.getTransformedComposition()).thenReturn(definition);
        when(definition.getActions()).thenReturn(new String[]{"Bait", null, "Lure"});
        h.clickNpc(h.spot, "Bait"); h.tick(0); h.ticks(1, 5);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(5, book.current().fishingTicks); assertEquals(0, book.current().maximumFailures);
        when(h.spot.getName()).thenReturn("Suspicious person"); h.clickNpc(h.spot, "Bait"); h.ticks(6, 9);
        assertEquals(5, book.current().fishingTicks);
        when(h.spot.getName()).thenReturn("Fishing spot"); when(definition.getActions()).thenReturn(new String[]{"Talk-to"});
        h.clickNpc(h.spot, "Bait"); h.ticks(10, 12); assertEquals(5, book.current().fishingTicks);
    }

    @Test public void movedLiveSpotPausesClockUntilAnotherInteraction() throws Exception
    {
        Harness h = new Harness(false); h.clickNpc(h.spot, "Harpoon"); h.tick(0); h.ticks(1, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        when(h.spot.getWorldLocation()).thenReturn(new WorldPoint(3201, 3200, 0)); h.ticks(5, 9);
        assertEquals(4, book.current().fishingTicks);
        h.clickNpc(h.spot, "Harpoon"); h.tick(10); h.ticks(11, 13); assertEquals(7, book.current().fishingTicks);
        h.plugin.onNpcDespawned(new NpcDespawned(h.spot)); h.ticks(14, 20); assertEquals(7, book.current().fishingTicks);
    }

    @Test public void aerialAndBarehandFishingAnimationsRunActivityClock() throws Exception
    {
        Harness h = new Harness(false); when(h.spot.getId()).thenReturn(FishingSpot.COMMON_TENCH.getIds()[0]);
        when(h.spot.getWorldLocation()).thenReturn(new WorldPoint(3207, 3204, 0));
        when(h.player.getAnimation()).thenReturn(AnimationID.AERIAL_FISHING_LAUNCH);
        h.clickNpc(h.spot, "Fish"); h.tick(0); h.ticks(1, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(4, book.current().fishingTicks);
        when(h.spot.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]);
        when(h.spot.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, 0));
        when(h.player.getAnimation()).thenReturn(AnimationID.BRUT_PLAYER_HAND_FISHING_START);
        h.clickNpc(h.spot, "Harpoon"); h.tick(5);
        when(h.player.getAnimation()).thenReturn(AnimationID.BRUT_PLAYER_HAND_FISHING_READY);
        net.runelite.api.events.AnimationChanged animation = new net.runelite.api.events.AnimationChanged(); animation.setActor(h.player);
        h.plugin.onAnimationChanged(animation); h.ticks(6, 8); assertEquals(7, book.current().fishingTicks);
        assertEquals(0, book.current().maximumFailures);
    }

    @Test public void invalidAttemptPhaseDoesNotPauseOngoingFishingClock() throws Exception
    {
        Harness h = new Harness(true); h.startSharks(); h.tick(0); h.ticks(1, 2, 2); h.ticks(3, 8);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(8, book.current().fishingTicks); assertFalse(h.engine.isTimingActive());
        assertEquals(1, book.current().catches); assertEquals(0, book.current().maximumFailures);
        invoke(h.plugin, "resetSession"); h.ticks(9, 12); assertEquals(0, book.current().fishingTicks);
    }

    @Test public void startChatBeforeFishingInteractionStillAnchorsOneLureAttempts() throws Exception
    {
        Harness h = new Harness(false); when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.interaction(h.player, h.spot);
        h.tick(0); h.ticks(1, 14, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(14, book.current().fishingTicks); assertEquals(1, book.current().measuredCatches);
        assertTrue(book.isVariableActive()); assertTrue(book.current().minimumFailures > 0);
    }

    @Test public void generalFishingClockStopsOnWorldChangeFullInventoryAndLogout() throws Exception
    {
        Harness h = new Harness(false); when(h.spot.getId()).thenReturn(FishingSpot.KARAMBWAN.getIds()[0]);
        when(h.player.getAnimation()).thenReturn(AnimationID.HUMAN_OCTOPUS_POT);
        h.clickNpc(h.spot, "Fish"); h.tick(0); h.ticks(1, 3);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        when(h.player.getWorldView()).thenReturn(mock(WorldView.class)); h.ticks(4, 6); assertEquals(3, book.current().fishingTicks);
        when(h.player.getWorldView()).thenReturn(h.worldView); h.clickNpc(h.spot, "Fish"); h.tick(7); h.ticks(8, 9);
        Item[] items = new Item[28]; java.util.Arrays.fill(items, new Item(ItemID.SHARK, 1)); when(h.inventory.getItems()).thenReturn(items);
        h.ticks(10, 12); assertEquals(5, book.current().fishingTicks);
        when(h.inventory.getItems()).thenReturn(new Item[0]); h.clickNpc(h.spot, "Fish"); h.tick(13); h.tick(14);
        String id = book.current().id; when(h.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        net.runelite.api.events.GameStateChanged state = new net.runelite.api.events.GameStateChanged(); state.setGameState(GameState.LOGIN_SCREEN);
        h.plugin.onGameStateChanged(state); h.ticks(15, 18);
        assertEquals(6, book.current().fishingTicks); assertEquals(id, book.current().id); assertEquals(0, book.current().maximumFailures);
    }

    @Test public void autoDetectsOneBeforeFirstCatchDespiteOldThreeDeclaration() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive()); h.ticks(1, 14, 4);
        assertEquals(1, book.current().measuredCatches); assertTrue(book.current().minimumFailures > 0);
        assertTrue(h.engine.getCurrentSession().getSetup().contains("lure mode: ONE"));
    }

    @Test public void autoDetectsThreeDespiteOldOneDeclaration() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 14, 4, 14);
        assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
        assertTrue(h.engine.getCurrentSession().getSetup().contains("lure mode: THREE"));
    }

    @Test public void autoFiveReplaysVerifiedFiveLureConsumptionTrace() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(5);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.currentTick = 539; h.startSharks(); h.tick(539);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive());
        h.ticks(540, 566, 543, 548, 552, 557, 562, 566);
        assertEquals(6, book.current().catches); assertEquals(6, book.current().measuredCatches);
        assertEquals(0, book.current().minimumFailures); assertEquals(0, book.current().maximumFailures);
        assertTrue(h.engine.getCurrentSession().getSetup().contains("lure mode: FIVE"));
    }

    @Test public void autoModeChangeKeepsSessionAndClockButRequiresFreshAttemptStart() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 5, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        String id = book.current().id;
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3); h.ticks(6, 8);
        assertFalse(book.isVariableActive()); assertFalse(h.engine.isTimingActive());
        assertEquals(8, book.current().fishingTicks); assertEquals(id, book.current().id);
        h.startSharks(); h.tick(9); h.ticks(10, 18, 13);
        assertEquals(2, book.current().measuredCatches); assertEquals(1, book.current().minimumFailures);
        assertEquals(18, book.current().fishingTicks); assertEquals(id, book.current().id);
    }

    @Test public void autoUnknownModeDoesNotGuessFailuresAndClockStillWorks() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(2);
        h.startSharks(); h.tick(0); h.ticks(1, 12, 5);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(12, book.current().fishingTicks); assertEquals(1, book.current().catches);
        assertEquals(0, book.current().measuredCatches); assertEquals(0, book.current().maximumFailures);
    }

    @Test public void autoNoSupplyUsesNoLureScheduleButHiddenSupplyStaysUnverified() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0);
        when(h.inventory.getItems()).thenReturn(new Item[0]); h.startSharks(); h.tick(0); h.ticks(1, 17, 5, 17);
        assertEquals(6, h.find(TrackingMethod.TIMED).getCycleTicks()); assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
        Harness hidden = new Harness(false); when(hidden.config.autoDetectLures()).thenReturn(true);
        when(hidden.inventory.count(ItemID.SHARK_LURE)).thenReturn(0);
        when(hidden.inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.TACKLE_BOX, 1)});
        hidden.startSharks(); hidden.tick(0); hidden.ticks(1, 10, 5);
        assertSuccessOnly(hidden.engine.getCurrentSession(), 1);
    }

    @Test public void autoIgnoresChangesToUnusedManualLureDeclaration() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 2);
        when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.FIVE);
        ConfigChanged change = new ConfigChanged(); change.setGroup(AttemptTrackerConfig.GROUP); change.setKey("sharkLures");
        h.plugin.onConfigChanged(change); h.ticks(3, 14, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive()); assertEquals(14, book.current().fishingTicks);
        assertEquals(1, book.current().measuredCatches); assertTrue(book.current().minimumFailures > 0);
    }

    @Test public void lureDisplayUpdatesBeforeTickWithoutChangingSessionOrInventingAnchor() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 5, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        String id = book.current().id; long measured = book.current().measuredCatches;
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(5);
        net.runelite.api.events.VarbitChanged change = new net.runelite.api.events.VarbitChanged(); change.setVarbitId(VarbitID.SHARK_LURE_USE_QUANTITY);
        h.plugin.onVarbitChanged(change);
        com.attempttracker.ui.LureDisplay shown = (com.attempttracker.ui.LureDisplay) get(h.plugin, "lureSnapshot");
        assertEquals("5 per catch", shown.amount); assertEquals("Restart harpooning for attempts", shown.note);
        assertEquals(id, book.current().id); assertEquals(measured, book.current().measuredCatches);
        h.tick(6); assertFalse(book.isVariableActive()); assertFalse(h.engine.isTimingActive());
        h.startSharks(); h.tick(7); h.ticks(8, 11, 11);
        shown = (com.attempttracker.ui.LureDisplay) get(h.plugin, "lureSnapshot");
        assertEquals("5 per catch", shown.amount); assertEquals("Detected automatically", shown.note);
        assertEquals(measured + 1, book.current().measuredCatches); assertEquals(id, book.current().id);
    }

    @Test public void containingVarpUpdateRefreshesLureDisplayWhileIdle() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true); h.tick(0);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3);
        net.runelite.api.events.VarbitChanged change = new net.runelite.api.events.VarbitChanged(); change.setVarbitId(-1);
        h.plugin.onVarbitChanged(change);
        assertEquals("3 per catch", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
        assertTrue(h.engine.getSessions().isEmpty());
    }

    @Test public void inventoryChangesImmediatelyShowNoLuresAndRestockedQuantityWithoutResettingSession() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 4, 4);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions)get(h.plugin, "fishingSessions");
        String id = book.current().id; long catches = book.current().catches;
        when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0); when(h.inventory.getItems()).thenReturn(new Item[0]);
        h.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(InventoryID.INV, h.inventory));
        com.attempttracker.ui.LureDisplay shown = (com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot");
        assertEquals("None", shown.amount); assertEquals("No lures available", shown.note);
        assertEquals(id, book.current().id); assertEquals(catches, book.current().catches);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(5);
        net.runelite.api.events.VarbitChanged choice = new net.runelite.api.events.VarbitChanged(); choice.setVarbitId(VarbitID.SHARK_LURE_USE_QUANTITY);
        h.plugin.onVarbitChanged(choice);
        assertEquals("None", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
        when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(300); when(h.inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SHARK_LURE, 300)});
        h.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(InventoryID.INV, h.inventory));
        assertEquals("5 per catch", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
        assertEquals(id, book.current().id); assertEquals(catches, book.current().catches);
    }

    @Test public void unrelatedContainerChangesAndLogoutDoNotReportMissingLures() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3); h.tick(0);
        when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0); when(h.inventory.getItems()).thenReturn(new Item[0]);
        h.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(InventoryID.BANK, h.inventory));
        assertEquals("3 per catch", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
        when(h.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        net.runelite.api.events.GameStateChanged logout = new net.runelite.api.events.GameStateChanged(); logout.setGameState(GameState.LOGIN_SCREEN);
        h.plugin.onGameStateChanged(logout);
        h.plugin.onItemContainerChanged(new net.runelite.api.events.ItemContainerChanged(InventoryID.INV, h.inventory));
        assertEquals("--", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
    }

    @Test public void noLureDisplayDoesNotDependOnARecognizedSavedChoice() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(999);
        when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0); when(h.inventory.getItems()).thenReturn(new Item[0]); h.tick(0);
        assertEquals("None", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).amount);
    }

    @Test public void lureDisplaySeparatesKnownChoiceFromMissingAndHiddenSupply() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3);
        when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(0); when(h.inventory.getItems()).thenReturn(new Item[0]); h.tick(0);
        com.attempttracker.ui.LureDisplay shown = (com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot");
        assertEquals("None", shown.amount); assertEquals("No lures available", shown.note);
        when(h.inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.TACKLE_BOX, 1)}); h.tick(1);
        shown = (com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot");
        assertEquals("Unknown", shown.amount); assertEquals("Tackle box supply unverified", shown.note);
        when(h.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN); h.tick(2);
        shown = (com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot");
        assertEquals("--", shown.amount); assertEquals("Log in to detect", shown.note);
    }

    @Test public void missingInventoryNeverBecomesAFalseNoLureEstimate() throws Exception
    {
        Harness h = new Harness(false); when(h.config.autoDetectLures()).thenReturn(true);
        when(h.client.getItemContainer(InventoryID.INV)).thenReturn(null); h.startSharks(); h.tick(0); h.ticks(1, 12, 5);
        assertFalse(h.engine.isTimingActive()); assertSuccessOnly(h.engine.getCurrentSession(), 1);
        assertEquals("Inventory loading", ((com.attempttracker.ui.LureDisplay)get(h.plugin, "lureSnapshot")).note);
    }

    @Test public void fullInventoryWithoutChatPausesAndDoesNotAddSilentFailures() throws Exception
    {
        Harness h = new Harness(true); h.startSharks(); h.tick(0); h.ticks(1, 3);
        Item[] items = new Item[28]; java.util.Arrays.fill(items, new Item(ItemID.SHARK, 1)); when(h.inventory.getItems()).thenReturn(items);
        h.tick(4); h.ticks(5, 15); assertFalse(h.engine.isTimingActive()); assertCounts(h.find(TrackingMethod.TIMED), 0, 0, 1);
    }

    @Test public void displayChangesDoNotPauseFishing() throws Exception
    {
        Harness h = new Harness(true); h.startSharks(); h.tick(0); h.ticks(1, 3);
        for (String key : new String[]{"showOverlay", "diagnostics", "saveTrace"})
        {
            ConfigChanged event = new ConfigChanged(); event.setGroup(AttemptTrackerConfig.GROUP); event.setKey(key);
            h.plugin.onConfigChanged(event); assertTrue(h.engine.isTimingActive());
        }
        h.ticks(4, 10, 5); assertCounts(h.find(TrackingMethod.TIMED), 1, 1, 0);
    }

    @Test public void automaticNoLurePhaseIsFiveThenSixTicks() throws Exception
    {
        Harness h = new Harness(false); when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.NONE); when(h.config.sharkFirstRollDelay()).thenReturn(0);
        h.startSharks(); h.tick(0); h.ticks(1, 17, 5, 17); assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
        assertEquals(6, h.find(TrackingMethod.TIMED).getCycleTicks());
    }

    @Test public void inventoryAndSkillsTabsPreserveTheAnchoredCycle() throws Exception
    {
        Harness h = new Harness(false); when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 3);
        for (MenuAction action : new MenuAction[]{MenuAction.CC_OP, MenuAction.WIDGET_TYPE_5})
        {
            MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(action); when(entry.getOption()).thenReturn("Skills");
            h.plugin.onMenuOptionClicked(new MenuOptionClicked(entry)); assertTrue(h.engine.isTimingActive());
        }
        h.ticks(4, 14, 4, 9); assertCounts(h.find(TrackingMethod.TIMED), 2, 1, 0);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(2, book.current().catches); assertEquals(1, book.current().minimumFailures); assertEquals(14, book.current().fishingTicks);
    }

    @Test public void logoutLoginRetainTotalsAndManualResetArchivesThem() throws Exception
    {
        Harness h = new Harness(true); h.startSharks(); h.tick(0); h.ticks(1, 10, 5);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions"); String id = book.current().id;
        when(h.client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        net.runelite.api.events.GameStateChanged state = new net.runelite.api.events.GameStateChanged(); state.setGameState(GameState.LOGIN_SCREEN); h.plugin.onGameStateChanged(state);
        h.ticks(11, 12); assertEquals(id, book.current().id); assertEquals(1, book.current().catches); assertEquals(1, book.current().minimumFailures);
        when(h.client.getGameState()).thenReturn(GameState.LOGGED_IN);
        state.setGameState(GameState.LOGGED_IN); h.plugin.onGameStateChanged(state);
        h.ticks(13, 14); assertEquals(id, book.current().id); assertFalse(h.engine.isTimingActive());
        invoke(h.plugin, "resetSession"); assertNotEquals(id, book.current().id); assertEquals(0, book.current().catches);
        assertEquals(1, book.snapshots().get(1).catches); assertEquals(1, book.snapshots().get(1).minimumFailures);
    }

    @Test public void choosingOneInPluginBeforeGameDoesNotLatchSuccessesOnly() throws Exception
    {
        Harness h = new Harness(true); when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3);
        when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 2);
        when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        ConfigChanged change = new ConfigChanged(); change.setGroup(AttemptTrackerConfig.GROUP); change.setKey("sharkLures"); h.plugin.onConfigChanged(change);
        h.tick(3); when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(1); h.tick(4);
        h.startSharks(); h.tick(5); h.ticks(6, 19, 9);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertFalse((Boolean)get(h.plugin, "sharkTimingInvalidated")); assertTrue(book.isVariableActive());
        assertEquals(1, book.current().catches); assertEquals(1, book.current().minimumFailures); assertEquals(2, book.current().maximumFailures);
        assertEquals(16, book.current().fishingTicks); assertEquals(1, book.current().measuredCatches);
    }

    @Test public void choosingOneInGameBeforePluginAlsoStartsVariableFailures() throws Exception
    {
        Harness h = new Harness(true); when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(3); h.tick(0);
        when(h.client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(1); h.tick(1);
        assertTrue((Boolean)get(h.plugin, "sharkTimingInvalidated"));
        when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        ConfigChanged change = new ConfigChanged(); change.setGroup(AttemptTrackerConfig.GROUP); change.setKey("sharkLures"); h.plugin.onConfigChanged(change);
        h.startSharks(); h.tick(2); h.ticks(3, 13);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive()); assertEquals(2, book.current().minimumFailures); assertEquals(2, book.current().maximumFailures);
        assertEquals(11, book.current().fishingTicks);
    }

    @Test public void replaysActualOneLureCatchTraceWithSilentFailures() throws Exception
    {
        Harness h = new Harness(true); when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        // Live raw=1 consumed one lure per catch; first 4 ticks, then gaps 5,6,11,12.
        h.currentTick = 1036; h.startSharks(); h.tick(1036);
        for (int tick = 1037; tick <= 1074; tick++)
        {
            if (tick == 1040 || tick == 1045 || tick == 1051 || tick == 1062 || tick == 1074)
            {
                h.chat(ChatMessageType.SPAM, "You catch a shark!"); h.experience.put(Skill.FISHING, h.experience.getOrDefault(Skill.FISHING,0) + 27);
                int left = h.inventory.count(ItemID.SHARK_LURE) - 1; when(h.inventory.count(ItemID.SHARK_LURE)).thenReturn(left);
            }
            h.tick(tick);
        }
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive()); assertEquals(5, book.current().catches); assertEquals(5, book.current().measuredCatches);
        assertEquals(2, book.current().minimumFailures); assertEquals(2, book.current().maximumFailures); assertEquals(38, book.current().fishingTicks);
        assertEquals(5.0 / 7.0, book.current().rate(false), 0.000001); assertEquals(295, h.inventory.count(ItemID.SHARK_LURE));
    }

    @Test public void oneLureAcceptsBothFiveAndSixTickGapsAndKeepsCounts() throws Exception
    {
        Harness h = new Harness(false); when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        h.startSharks(); h.tick(0); h.ticks(1, 21, 4, 10, 15, 21);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertTrue(book.isVariableActive()); assertEquals(4, book.current().catches); assertEquals(4, book.current().measuredCatches);
        assertEquals(0, book.current().minimumFailures); assertEquals(0, book.current().maximumFailures); assertEquals(1.0, book.current().rate(false), 0);
        h.ticks(22, 31); assertEquals(1, book.current().minimumFailures); assertEquals(2, book.current().maximumFailures);
        when(h.player.getAnimation()).thenReturn(-1); h.tick(32); long pausedTicks = book.current().fishingTicks;
        h.ticks(33, 42); assertFalse(book.isVariableActive()); assertEquals(pausedTicks, book.current().fishingTicks); assertEquals(4, book.current().catches);
    }

    @Test public void oneLureOffScheduleCatchRetainsSuccessButRemovesUnsupportedFailures() throws Exception
    {
        Harness h = new Harness(true); when(h.config.sharkLures()).thenReturn(AttemptTrackerConfig.SharkLures.ONE);
        h.startSharks(); h.tick(0); h.ticks(1, 5, 4); h.ticks(6, 6, 6);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertFalse(book.isVariableActive()); assertEquals(2, book.current().catches); assertEquals(0, book.current().measuredCatches);
        assertEquals(0, book.current().maximumFailures); assertTrue(Double.isNaN(book.current().rate(false)));
    }

    @Test public void animationChangePreservesInventoryFillingCatchAndPauses() throws Exception
    {
        Harness h = new Harness(true); when(h.config.sharkFirstRollDelay()).thenReturn(0); h.startSharks(); h.tick(0); h.ticks(1, 3);
        h.catchMessage(); when(h.player.getAnimation()).thenReturn(-1);
        net.runelite.api.events.AnimationChanged animation = new net.runelite.api.events.AnimationChanged(); animation.setActor(h.player); h.plugin.onAnimationChanged(animation);
        h.chat(ChatMessageType.GAMEMESSAGE, "Your inventory is too full to hold any more fish."); h.tick(4); h.ticks(5, 15);
        com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions");
        assertEquals(1, book.current().catches); assertEquals(0, book.current().minimumFailures); assertEquals(4, book.current().fishingTicks);
        assertFalse(h.engine.isTimingActive());
    }

    @Test public void eatingIsAnActionChangeButDroppingIsNot() throws Exception
    {
        Harness h = new Harness(true); h.startSharks(); h.tick(0); h.ticks(1, 3);
        MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(MenuAction.CC_OP); when(entry.getOption()).thenReturn("Drop");
        h.plugin.onMenuOptionClicked(new MenuOptionClicked(entry)); assertTrue(h.engine.isTimingActive());
        when(entry.getOption()).thenReturn("Eat"); h.plugin.onMenuOptionClicked(new MenuOptionClicked(entry)); assertFalse(h.engine.isTimingActive());
        h.ticks(4, 15); com.attempttracker.core.FishingSessions book = (com.attempttracker.core.FishingSessions) get(h.plugin, "fishingSessions"); assertEquals(3, book.current().fishingTicks);
    }

	private static void assertSuccessOnly(AttemptSession session, long successes)
	{
		assertNotNull(session);
		assertEquals(TrackingMethod.SUCCESS_ONLY, session.getMethod());
		assertEquals(successes, session.getSuccesses());
		assertEquals(0L, session.getFailures());
		assertEquals(0L, session.getAttempts());
		assertTrue(Double.isNaN(session.getSuccessRate()));
		assertTrue(Double.isNaN(session.getConfidenceLower()));
		assertTrue(Double.isNaN(session.getConfidenceUpper()));
	}

	private static void assertCounts(AttemptSession session, long successes, long failures,
		int excluded)
	{
		assertNotNull(session);
		assertEquals(successes, session.getSuccesses());
		assertEquals(failures, session.getFailures());
		assertEquals(successes + failures, session.getAttempts());
		assertEquals(excluded, session.getExcludedWindows());
	}

	private static void set(Object object, String name, Object value) throws Exception
	{
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		field.set(object, value);
	}

	private static Object get(Object object, String name) throws Exception
	{
		Field field = object.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(object);
	}

	private static void optionalSet(Object object, String name, Object value) throws Exception
	{
		try { set(object, name, value); }
		catch (NoSuchFieldException ignored) { /* New optional injected service. */ }
	}

	private static void invoke(Object object, String name) throws Exception
	{
		Method method = object.getClass().getDeclaredMethod(name);
		method.setAccessible(true);
		method.invoke(object);
	}

	private static final class Harness
	{
		private final AttemptTrackerPlugin plugin = new AttemptTrackerPlugin();
		private final Client client = mock(Client.class);
		private final Player player = mock(Player.class);
		private final NPC spot = mock(NPC.class);
		private final WorldView worldView = mock(WorldView.class);
		private final AttemptTrackerConfig config = mock(AttemptTrackerConfig.class, CALLS_REAL_METHODS);
		private final ItemManager itemManager = mock(ItemManager.class);
		private final ClientThread clientThread = mock(ClientThread.class);
		private final ItemContainer inventory = mock(ItemContainer.class);
		private final EnumMap<Skill, Integer> experience = new EnumMap<>(Skill.class);
		private final AttemptTrackerEngine engine;
		private int currentTick;

		private Harness(boolean fixedTiming) throws Exception
		{
			when(client.getLocalPlayer()).thenReturn(player);
			when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getTickCount()).thenAnswer(call -> currentTick);
			when(client.getSkillExperience(any(Skill.class))).thenAnswer(call -> experience.getOrDefault(call.getArgument(0), 0));
			when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99);
			when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
			when(client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(1);
			when(player.getName()).thenReturn("Attempt Tester");
			when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
			when(player.getWorldView()).thenReturn(worldView);
			when(player.getAnimation()).thenReturn(AnimationID.HUMAN_HARPOON_CRYSTAL);
			when(player.getInteracting()).thenReturn(spot);
			when(spot.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]);
			when(spot.getName()).thenReturn("Fishing spot");
			when(spot.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, 0));
			when(spot.getWorldView()).thenReturn(worldView);
			when(config.fixedTiming()).thenReturn(fixedTiming);
			// Historical fixtures exercise their explicit manual timing declarations.
			when(config.autoDetectLures()).thenReturn(false);
			// Earlier phase fixtures deliberately configure a five-tick first roll.
			// Live/default fixtures explicitly restore automatic mode (zero).
			when(config.sharkFirstRollDelay()).thenReturn(5);
			when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SHARK_LURE, 300)});
			when(inventory.count(ItemID.SHARK_LURE)).thenReturn(300);
			when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
			ItemContainer equipment = mock(ItemContainer.class);
			when(equipment.getItems()).thenReturn(new Item[0]);
			when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
			ItemComposition lure = mock(ItemComposition.class);
			when(lure.getName()).thenReturn("Shark lure");
			when(itemManager.getItemComposition(anyInt())).thenReturn(lure);
			set(plugin, "client", client);
			set(plugin, "config", config);
			set(plugin, "itemManager", itemManager);
			doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; })
				.when(clientThread).invoke(any(Runnable.class));
			set(plugin, "clientThread", clientThread);
			optionalSet(plugin, "configManager", mock(ConfigManager.class));
			set(plugin, "custom", new CustomActivity(config));
			set(plugin, "running", true);
			set(plugin, "lastAccount", player.getName());
			engine = (AttemptTrackerEngine) get(plugin, "engine");
		}

		private void enableCustom() throws Exception
		{
			when(config.customEnabled()).thenReturn(true);
			when(config.customMode()).thenReturn(AttemptTrackerConfig.CustomMode.TIMED);
			when(config.customName()).thenReturn("Custom shark");
			when(config.customSkill()).thenReturn(Skill.FISHING);
			when(config.customSuccess()).thenReturn("A custom success");
			when(config.customStart()).thenReturn("A custom start");
			when(config.customAnimations()).thenReturn(Integer.toString(AnimationID.HUMAN_HARPOON_CRYSTAL));
			when(config.customCycle()).thenReturn(5);
			set(plugin, "custom", new CustomActivity(config));
		}

		private void chat(ChatMessageType type, String text)
		{
			ChatMessage message = new ChatMessage();
			message.setType(type);
			message.setMessage(text);
			plugin.onChatMessage(message);
		}

		private void catchMessage()
		{
			chat(ChatMessageType.SPAM, "You catch a shark.");
			experience.put(Skill.FISHING, experience.getOrDefault(Skill.FISHING, 0) + 110);
		}

		private void liveIslandSharks()
		{
			// Exact live NPC: gameval NpcID._0_40_34_MEMBERFISH, omitted by FishingSpot.
			when(spot.getId()).thenReturn(16335);
			when(spot.getWorldLocation()).thenReturn(new WorldPoint(2576, 2219, 0));
			when(player.getWorldLocation()).thenReturn(new WorldPoint(2575, 2219, 0));
			when(config.sharkFirstRollDelay()).thenReturn(0);
			ItemContainer equipment = mock(ItemContainer.class);
			when(equipment.getItems()).thenReturn(new Item[]{new Item(ItemID.CRYSTAL_HARPOON, 1)});
			when(client.getItemContainer(InventoryID.WORN)).thenReturn(equipment);
		}

		private void liveIslandCatch()
		{
			chat(ChatMessageType.SPAM, "You catch a shark!");
			experience.put(Skill.FISHING, experience.getOrDefault(Skill.FISHING, 0) + 22);
			int remaining = inventory.count(ItemID.SHARK_LURE) - 3;
			when(inventory.count(ItemID.SHARK_LURE)).thenReturn(remaining);
			when(inventory.getItems()).thenReturn(new Item[]{new Item(ItemID.SHARK_LURE, remaining)});
		}

		private void startSharks()
		{
			chat(ChatMessageType.SPAM, "You start harpooning fish.");
		}

		private void startCustom()
		{
			chat(ChatMessageType.SPAM, "A custom start occurred.");
		}

		private void interaction(Actor source, Actor target)
		{
			plugin.onInteractingChanged(new InteractingChanged(source, target));
		}

		private void clickNpc(NPC npc, String option)
		{
			MenuEntry entry = mock(MenuEntry.class);
			when(entry.getType()).thenReturn(MenuAction.NPC_SECOND_OPTION);
			when(entry.getOption()).thenReturn(option);
			when(entry.getTarget()).thenReturn("<col=ffff00>Fishing spot</col>");
			when(entry.getNpc()).thenReturn(npc);
			plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
		}

		private void tick(int tick)
		{
			assertEquals("Harness must observe every tick in order", currentTick, tick);
			plugin.onGameTick(new GameTick());
			// Hooks.tick increments only after GameTick subscribers have run. Chat
			// packets before the next GameTick therefore see its current counter.
			currentTick = tick + 1;
		}

		private void ticks(int first, int last, int... successfulTicks)
		{
			for (int tick = first; tick <= last; tick++)
			{
				for (int successfulTick : successfulTicks)
				{
					if (tick == successfulTick) { catchMessage(); }
				}
				tick(tick);
			}
		}

		private AttemptSession find(TrackingMethod method)
		{
			for (AttemptSession session : engine.getSessions())
			{
				if (session.getMethod() == method) { return session; }
			}
			fail("Missing " + method + " session");
			return null;
		}

		private AttemptSession find(TrackingMethod method, String setupFragment)
		{
			for (AttemptSession session : engine.getSessions())
			{
				if (session.getMethod() == method && session.getSetup().contains(setupFragment)) { return session; }
			}
			fail("Missing " + method + " session with setup " + setupFragment);
			return null;
		}

		private long successOnlyCatchCount()
		{
			long count = 0;
			for (AttemptSession session : engine.getSessions())
			{
				if (session.getMethod() == TrackingMethod.SUCCESS_ONLY) { count += session.getSuccesses(); }
			}
			return count;
		}
	}
}

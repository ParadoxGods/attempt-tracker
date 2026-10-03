package com.attempttracker;

import com.attempttracker.activity.ActivityMessages;
import com.attempttracker.activity.ActivityOutcome;
import com.attempttracker.activity.ActivityStarts;
import com.attempttracker.core.AttemptSession;
import com.attempttracker.core.AttemptTrackerEngine;
import com.attempttracker.core.AttemptTrackerEngine.TimedResult;
import com.attempttracker.core.TrackingMethod;
import com.attempttracker.core.FishingSession;
import com.attempttracker.core.FishingSessions;
import com.attempttracker.core.AdaptiveFishingSample;
import com.attempttracker.core.TwoTickFishingTracker;
import com.attempttracker.core.TwoTickFishingTracker.Result;
import com.attempttracker.diagnostics.TickTrace;
import com.attempttracker.store.SessionStore;
import com.attempttracker.store.FishingSessionStore;
import com.attempttracker.ui.AttemptTrackerOverlay;
import com.attempttracker.ui.AttemptTrackerPanel;
import com.attempttracker.ui.LureDisplay;
import com.google.gson.Gson;
import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Filepath;
import net.runelite.client.util.Text;
import net.runelite.client.util.ImageUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Attempt Tracker",
	internalName = "attempt-tracker",
	legacyDataDirectory = "attempt-tracker",
	description = "Track fishing catches, estimated failures, catch rate, and session time",
	tags = {"fishing", "shark", "lures", "success", "statistics"}
)
public class AttemptTrackerPlugin extends Plugin
{
	private static final Logger LOG = LoggerFactory.getLogger(AttemptTrackerPlugin.class);
	@Inject private Client client;
	@Inject private AttemptTrackerConfig config;
	@Inject private ClientThread clientThread;
	@Inject private ItemManager itemManager;
	@Inject private OverlayManager overlayManager;
	@Inject private ClientToolbar clientToolbar;
	@Inject private Gson gson;

	private final AttemptTrackerEngine engine = new AttemptTrackerEngine();
	private final FishingSessions fishingSessions = new FishingSessions();
	private final FishingActivityTracker fishingActivity = new FishingActivityTracker();
	private final AdaptiveFishingActivity adaptiveActivity = new AdaptiveFishingActivity();
	private final AdaptiveFishingSample adaptiveSample = new AdaptiveFishingSample();
	private int adaptiveCatchesThisTick;
	private boolean adaptiveInvalidated;
	private long lastStrictCatches;
	private boolean adaptiveFishing;
	private int incomingHitsThisTick;
	private final TwoTickFishingTracker twoTickTracker = new TwoTickFishingTracker();
	private NPC twoTickSpot;
	private WorldPoint twoTickSpotPosition, twoTickPlayerPosition;
	private boolean twoTickIdleBefore, twoTickBlockedBefore;
	private Integer twoTickSetup;
	private boolean twoTickCombatHit, twoTickCombatRetarget, twoTickAcceptedFishing, twoTickConflict, twoTickOtherAnimation;
	private boolean twoTickHarpoonStart, twoTickFreshHarpoon, twoTickRefused;
	private int lastExplicitAttackTick = -100;
	private String twoTickDiagnostic = "Waiting for a catch anchor";
	private FishingSessionStore fishingStore;
	private volatile FishingSession fishingSnapshot;
	private volatile List<FishingSession> fishingHistorySnapshot = java.util.Collections.emptyList();
	private volatile String summaryStatus = "Ready";
	private volatile LureDisplay lureSnapshot = LureDisplay.waiting();
	private int lastDisplayedRawLureSetting = Integer.MIN_VALUE;
	private int fishingAnimation = -1;
	private final EnumMap<Skill, Integer> lastExperience = new EnumMap<>(Skill.class);
	private AttemptTrackerPanel panel;
	private AttemptTrackerOverlay overlay;
	private NavigationButton navigation;
	private SessionStore store;
	private ScheduledExecutorService io;
	private ScheduledFuture<?> pendingSave;
	private final Object lifecycleLock = new Object();
	private final Object persistenceLock = new Object();
	private volatile long generation;
	private TickTrace trace;
	private CustomActivity custom;
	private TimedContext previousContext;
	private WorldPoint previousPosition;
	private WorldPoint selectedObject;
	private NPC fishingTarget;
	private WorldPoint fishingTargetPosition;
	private String selectedTarget = "";
	private String gatherLabel = "";
	private final List<PendingSuccess> pendingSuccesses = new ArrayList<>();
	private PendingStart pendingStart;
	private int actionStartTick = -1;
	private boolean stopAfterTick;
	private boolean animationStopPending;
	private String timingProblem = "";
	private Integer lastRawLureSetting;
	private boolean sharkTimingInvalidated;
	private boolean cancelled;
	private volatile boolean running;
	private String lastAccount = "";
	private volatile long savedFingerprint;

	private volatile boolean paused;
	private volatile String status = "Waiting for a supported activity.";
	private volatile String notice = "";
	private volatile AttemptSession currentSnapshot;
	private volatile List<AttemptSession> historySnapshot = Collections.emptyList();

	@Provides
	AttemptTrackerConfig provideConfig(ConfigManager manager) { return manager.getConfig(AttemptTrackerConfig.class); }

	@Override
	protected void startUp() throws IOException
	{
		final long run;
		synchronized (lifecycleLock)
		{
		run = ++generation;
		final Filepath directory;
		synchronized (persistenceLock)
		{
		directory = getPluginDirectory();
		store = new SessionStore(directory, gson);
		try { engine.restore(store.load()); notice = store.getLastLoadWarning(); }
		catch (IOException ex) { LOG.warn("Unable to load attempt history", ex); notice = "History could not be loaded."; engine.clear(); }
		fishingStore = new FishingSessionStore(directory, gson);
		try
		{
			List<FishingSession> savedFishing = fishingStore.load(); fishingSessions.restore(savedFishing);
			if (savedFishing == null) { fishingSessions.importPrevious(engine.getSessions()); }
			else { fishingSessions.baseline(engine.getSessions()); }
		}
		catch (IOException ex) { LOG.warn("Unable to load fishing sessions", ex); notice = "Fishing history could not be loaded."; fishingSessions.restore(null); fishingSessions.baseline(engine.getSessions()); }
		}
		ScheduledThreadPoolExecutor worker = new ScheduledThreadPoolExecutor(1, task -> { Thread thread = new Thread(task, "attempt-tracker-io"); thread.setDaemon(true); return thread; });
		worker.setRemoveOnCancelPolicy(true);
		worker.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
		io = worker;
		pendingSave = null;
		savedFingerprint = Long.MIN_VALUE;
		trace = new TickTrace(directory.join("traces"));
		custom = new CustomActivity(config);
		paused = false;
		running = true;
		clearPending(); pendingStart = null; actionStartTick = -1; stopAfterTick = false; animationStopPending = false;
		timingProblem = "";
		previousContext = null; previousPosition = null; selectedObject = null; clearFishingTarget(); lastExperience.clear();
		lastRawLureSetting = null; sharkTimingInvalidated = false; selectedTarget = ""; gatherLabel = "";
		fishingAnimation = -1; fishingActivity.stop(); lastDisplayedRawLureSetting = Integer.MIN_VALUE;
		FishingSession restored = fishingSessions.current();
		adaptiveSample.restore(restored.adaptiveCatches, restored.adaptiveFailureUpper, restored.adaptiveTiming);
		lastStrictCatches = restored.measuredCatches; adaptiveCatchesThisTick = 0; adaptiveInvalidated = false;
		adaptiveActivity.stop(); adaptiveFishing = false; incomingHitsThisTick = 0;
		twoTickTracker.restore(restored.twoTickCatches, restored.twoTickFailures); stopTwoTick(); clearTwoTickFrame();
		lastExplicitAttackTick = -100;
		lureSnapshot = LureDisplay.waiting(); summaryStatus = "Ready";
		fishingSessions.advance(false, false);
		}
		SwingUtilities.invokeLater(() ->
		{
			synchronized (lifecycleLock)
			{
			if (!isCurrent(run)) { return; }
			panel = new AttemptTrackerPanel(() -> invokeClient(run, this::resetSession), () -> exportCsv(run));
			navigation = NavigationButton.builder().tooltip("Attempt Tracker").icon(createIcon()).priority(7).panel(panel).build();
			clientToolbar.addNavigation(navigation);
			panel.refresh(fishingSessions.snapshots(), simpleStatus(), statusWithNotice(), lureSnapshot);
			}
		});
		overlay = new AttemptTrackerOverlay(() -> fishingSnapshot, config::showOverlay, this::simpleStatus);
		overlayManager.add(overlay);
		// Plugin Hub can enable or replace plugins on the EDT while already logged in.
		invokeClient(run, () ->
		{
			fishingSessions.advance(client.getGameState() == GameState.LOGGED_IN, false);
			publish();
		});
	}

	@Override
	protected void shutDown()
	{
		final long run;
		final ScheduledExecutorService worker;
		final SessionStore runStore;
		final TickTrace runTrace;
		final NavigationButton oldNavigation;
		final List<AttemptSession> finalHistory;
		final List<FishingSession> finalFishing;
		final FishingSessionStore runFishingStore;
		synchronized (lifecycleLock)
		{
		run = generation;
		running = false;
		cancelPendingSave();
		breakTiming();
		finalHistory = engine.getSessions();
		fishingSessions.sync(finalHistory); fishingSessions.advance(false, false);
		finalFishing = fishingSessions.snapshots(); runFishingStore = fishingStore;
		worker = io; runStore = store; runTrace = trace;
		io = null;
		oldNavigation = navigation; navigation = null; panel = null;
		}
		if (overlay != null) { overlayManager.remove(overlay); overlay = null; }
		SwingUtilities.invokeLater(() -> { if (oldNavigation != null) { clientToolbar.removeNavigation(oldNavigation); } });
		if (worker != null && !worker.isShutdown())
		{
			worker.execute(() -> { try { save(runStore, finalHistory, run); saveFishing(runFishingStore, finalFishing, run); } finally { closeTrace(runTrace, run); } });
			worker.shutdown();
			try { if (!worker.awaitTermination(2, TimeUnit.SECONDS)) { LOG.warn("Attempt Tracker saves are still finishing in the background"); } }
			catch (InterruptedException ex) { LOG.warn("Interrupted while waiting for saves; they will finish in the background", ex); }
		}
		clearPending(); previousContext = null; previousPosition = null; lastExperience.clear();
		currentSnapshot = null;
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (!running || paused || client.getGameState() != GameState.LOGGED_IN
			|| (event.getType() != ChatMessageType.SPAM && event.getType() != ChatMessageType.GAMEMESSAGE)) { return; }
		String message = event.getMessage();
		String clean = CustomActivity.normalize(message);
		if (clean.contains("inventory is too full") || clean.contains("can't carry any more")
			|| clean.contains("don't have enough inventory space") || clean.contains("not enough space in your inventory")
			|| clean.contains("cannot carry any more") || clean.contains("don't have enough bait")
			|| clean.contains("don't have enough shark lures"))
		{
			// Resolve this at GameTick: stop/catch packets in one completed frame
			// can arrive in either order. A refusal alone is never a failed roll.
			twoTickRefused = true;
			stopAfterTick = true; animationStopPending = false; pendingStart = null; stopAdaptive(); fishingSessions.advance(true, false); return;
		}
		if (custom.matchesStart(message))
		{
			pendingStart = new PendingStart(client.getTickCount(), custom.skill, true, false);
			return;
		}
		Optional<Skill> startedSkill = ActivityStarts.parseStart(message);
		if (startedSkill.isPresent() && enabled(startedSkill.get()))
		{
			if (startedSkill.get() == Skill.FISHING && config.adaptiveTiming())
			{
				Player player = client.getLocalPlayer();
				NPC spot = player != null && player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : fishingTarget;
				adaptiveActivity.select(spot, client.getTickCount()); adaptiveActivity.accepted(client.getTickCount());
				selectTwoTickSpot(spot == null ? twoTickSpot : spot); twoTickAcceptedFishing = true;
				if (ActivityStarts.isHarpooning(message)) { twoTickHarpoonStart = true; }
				else { twoTickConflict = true; }
			}
			pendingStart = new PendingStart(client.getTickCount(), startedSkill.get(), false, ActivityStarts.isHarpooning(message));
			return;
		}
		Boolean customResult = custom.outcome(message);
		if (customResult != null)
		{
			if (custom.timed)
			{
				if (customResult) { queueSuccess(custom.name, custom.skill, true); }
				else { breakTiming(); }
			}
			else { engine.recordObserved(custom.name, setup(custom.skill, "custom", true), customResult); publish(); }
			return;
		}
		Optional<ActivityOutcome> parsed = ActivityMessages.parse(message);
		if (!parsed.isPresent()) { return; }
		ActivityOutcome outcome = parsed.get();
		Skill skill = Skill.valueOf(outcome.getSkillName());
		if (!enabled(skill)) { return; }
		if (outcome.isTimed())
		{
			if (skill == Skill.FISHING && sharksOnly() && !outcome.getActivity().equals("Fishing: Shark")) { return; }
			if (outcome.isSuccess())
			{
				if (skill == Skill.FISHING) { adaptiveCatchesThisTick++; }
				queueSuccess(outcome.getActivity(), skill, false);
			}
		}
		else
		{
			String activity = outcome.getActivity();
			if (skill == Skill.THIEVING)
			{
				// Both message forms name the victim; do not use the attacking NPC after a stun.
				String victim = pickpocketVictim(clean);
				activity += victim.isEmpty() ? "" : ": " + victim;
			}
			engine.recordObserved(activity, setup(skill, "chat outcomes", false), outcome.isSuccess());
			publish();
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!running) { return; }
		Player player = client.getLocalPlayer();
		if (paused || player == null || client.getGameState() != GameState.LOGGED_IN)
		{
			stopTwoTick(); clearTwoTickFrame();
			lastExplicitAttackTick = -100;
			stopAdaptive(); adaptiveSample.discontinuity(); adaptiveCatchesThisTick = 0; incomingHitsThisTick = 0;
			breakTiming(); fishingSessions.advance(client.getGameState() == GameState.LOGGED_IN, false);
			status = paused ? "Paused." : "Waiting for login."; publish(); return;
		}
		int observedMessagesThisTick = pendingSuccesses.size();
		if (stopAfterTick && animationStopPending && config.adaptiveTiming() && adaptiveActivity.canBridge(client.getTickCount())) { stopAfterTick = false; }
		if (inventoryFull()) { stopAfterTick = true; animationStopPending = false; stopAdaptive(); fishingSessions.advance(true, false); }
		PendingStart start = pendingStart;
		pendingStart = null;
		String account = player.getName() == null ? "" : player.getName();
		if (!account.equals(lastAccount))
		{
			stopTwoTick();
			stopAdaptive(); adaptiveSample.discontinuity();
			breakTiming(); lastAccount = account; lastExperience.clear(); lastRawLureSetting = null;
		}
		int rawLureSetting = client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY);
		if (lastRawLureSetting != null && lastRawLureSetting != rawLureSetting)
		{
			// Never continue the old roll schedule across an in-game mode change.
			sharkTimingInvalidated = true;
			if (previousContext != null && previousContext.shark)
			{
				breakTiming(false);
			}
		}
		lastRawLureSetting = rawLureSetting;
		boolean modeConfirmed = config.autoDetectLures() ? effectiveSharkLures() != null
			: (rawLureSetting == 1 && config.sharkLures() == AttemptTrackerConfig.SharkLures.ONE)
			|| (rawLureSetting == 3 && config.sharkLures() == AttemptTrackerConfig.SharkLures.THREE);
		if (sharkTimingInvalidated && modeConfirmed)
		{
			sharkTimingInvalidated = false;
		}
		WorldPoint position = player.getWorldLocation();
		boolean moved = previousPosition != null && !previousPosition.equals(position);
		if (moved)
		{
			// A selected spot can precede walking to it; movement during an active
			// fishing action still cancels that action and its remembered target.
			NPC approaching = previousContext == null && !engine.isTimingActive() ? fishingTarget : null;
			WorldPoint approachingPosition = fishingTargetPosition;
			breakTiming(fishingActivity.isActive());
			if (approaching != null) { fishingTarget = approaching; fishingTargetPosition = approachingPosition; }
		}
		previousPosition = position;
		TimedContext context = context(player);
		// A final catch may stop the animation on the same tick (for example a full inventory).
		if (context == null && pendingSuccesses.size() == 1 && !cancelled
			&& previousContext != null && pendingSuccesses.get(0).matches(previousContext))
		{
			context = previousContext;
		}
		int tick = client.getTickCount();
		int xpDelta = 0;
		Skill traceSkill = context == null ? AnimationCatalog.skillFor(player.getAnimation()) : context.skill;
		if (traceSkill == null) { traceSkill = start == null ? Skill.FISHING : start.skill; }
		int experience = client.getSkillExperience(traceSkill);
		Integer old = lastExperience.get(traceSkill);
		int traceXpDelta = old == null ? 0 : Math.max(0, experience - old);
		if (context != null) { xpDelta = traceXpDelta; }
		for (Skill skill : new Skill[]{Skill.FISHING, Skill.MINING, Skill.WOODCUTTING, custom.skill == null ? Skill.FISHING : custom.skill})
		{
			if (skill != Skill.OVERALL) { lastExperience.put(skill, client.getSkillExperience(skill)); }
		}
		if (context != null && previousContext != null && (!context.sameTarget(previousContext)
			|| !context.setup.equals(previousContext.setup) || context.cycle != previousContext.cycle))
		{
			breakTiming(false);
			if (context.skill == Skill.FISHING && context.actor instanceof NPC) { rememberFishingTarget((NPC) context.actor); }
		}
		if (start != null && start.skill == Skill.FISHING && start.tick == tick)
		{
			NPC spot = player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : fishingTarget;
			fishingActivity.select(spot);
		}
		boolean wasAwaitingTwoTickRoll = twoTickTracker.isAwaitingRoll();
		int weaponSpeed = config.adaptiveTiming() ? TwoTickFishingSignals.effectiveWeaponSpeed(client, itemManager) : 0;
		int liveSetup = twoTickSetupFingerprint(weaponSpeed, rawLureSetting);
		boolean stableSetup = twoTickSetup == null || twoTickSetup == liveSetup;
		boolean stablePosition = twoTickPlayerPosition == null || twoTickPlayerPosition.equals(position);
		boolean twoTickEligible = config.fishing() && config.adaptiveTiming() && !twoTickBlockedBefore
			&& client.getItemContainer(InventoryID.INV) != null
			&& stableSetup && stablePosition && validTwoTickSpot(player) && TwoTickFishingSignals.autoRetaliateEnabled(client)
			&& (weaponSpeed == 2 || weaponSpeed == 3);
		// Capture accepted harpooning within the frame, not the animation left
		// after it: a completed catch can clear that animation on the same tick.
		// A target change plus a stale harpoon animation alone is insufficient.
		boolean acceptedHarpoon = (twoTickHarpoonStart || twoTickAcceptedFishing && twoTickFreshHarpoon)
			&& (!sharksOnly() || twoTickSpot != null && (fishingSpot(twoTickSpot) == FishingSpot.SHARK));
		boolean idleAfter = player.getInteracting() == null;
		boolean otherActionAnimation = twoTickOtherAnimation || (player.getAnimation() >= 0 && !AnimationCatalog.isHarpoon(player.getAnimation()));
		boolean qualifiedFlinch = twoTickCombatHit && twoTickCombatRetarget && twoTickIdleBefore
			&& !otherActionAnimation && tick > lastExplicitAttackTick + 1;
		boolean unknownTwoTickOutcome = traceSkill == Skill.FISHING && traceXpDelta > 0 && adaptiveCatchesThisTick == 0;
		boolean modelConflict = twoTickConflict || (twoTickRefused && adaptiveCatchesThisTick == 0)
			|| unknownTwoTickOutcome || otherActionAnimation;
		Result twoTickResult = twoTickTracker.observe(tick, twoTickEligible, qualifiedFlinch, acceptedHarpoon,
			idleAfter, modelConflict, adaptiveCatchesThisTick);
		// A catch anchor alone must not interrupt ordinary fishing with a fast
		// weapon equipped. The model owns frames only after a qualified flinch.
		boolean twoTickOwnsFrame = wasAwaitingTwoTickRoll || twoTickResult == Result.ARMED
			|| twoTickResult == Result.COUNTED || twoTickResult == Result.INVALIDATED;
		twoTickDiagnostic = twoTickResult + "; eligible=" + twoTickEligible + "; speed=" + weaponSpeed + "; hit=" + twoTickCombatHit
			+ "; retarget=" + twoTickCombatRetarget + "; idleBefore=" + twoTickIdleBefore + "; idleAfter=" + idleAfter
			+ "; accepted=" + acceptedHarpoon + "; conflict=" + modelConflict + "; catches=" + twoTickTracker.getCatches()
			+ "; failures=" + twoTickTracker.getFailures();
		twoTickIdleBefore = idleAfter; twoTickBlockedBefore = inventoryFull(); twoTickSetup = liveSetup; twoTickPlayerPosition = position;
		if (twoTickOwnsFrame) { engine.interruptTiming(); fishingSessions.stopVariable(); adaptiveSample.discontinuity(); }
		boolean traceEligible = false;
		boolean strictStarted = false;
		boolean countingFishing = false;
		String traceResult = "NO_CONTEXT";
		if (context != null)
		{
			String activity = context.activity;
			boolean matchingOutcomes = pendingSuccesses.isEmpty()
				|| (pendingSuccesses.size() == 1 && pendingSuccesses.get(0).matches(context));
			boolean estimate = !twoTickOwnsFrame && (config.fixedTiming() || context.shark) && context.cycle > 0;
			AttemptTrackerConfig.SharkLures mode = effectiveSharkLures();
			boolean variable = !twoTickOwnsFrame && context.shark && !sharkTimingInvalidated && lureQuantity() > 0
				&& (mode == AttemptTrackerConfig.SharkLures.ONE || mode == AttemptTrackerConfig.SharkLures.FIVE);
			if ((estimate || variable) && !stopAfterTick && start != null && start.matches(context, tick))
			{
				strictStarted = true;
				if (variable) { fishingSessions.startVariable(start.tick, mode == AttemptTrackerConfig.SharkLures.ONE ? 5 : 4, mode == AttemptTrackerConfig.SharkLures.ONE ? 6 : 5); }
				else { engine.startTimed(start.tick, activity, context.setup, context.cycle, context.firstDelay); }
				actionStartTick = start.tick;
				timingProblem = "";
				cancelled = false;
			}
			// Ambiguous or missed outcomes undermine the entire current start's
			// inferred sample; an intentional stop only excludes its partial cycle.
			boolean ambiguous = !matchingOutcomes || (xpDelta > 0 && pendingSuccesses.isEmpty());
			boolean eligible = !cancelled && !ambiguous
				&& !(stopAfterTick && pendingSuccesses.isEmpty());
			TimedResult result = TimedResult.WAITING_FOR_START;
			if (variable)
			{
				if (ambiguous && fishingSessions.isVariableActive()) { fishingSessions.observeVariable(tick, 2); adaptiveInvalidated = true; }
				if (eligible && fishingSessions.isVariableActive())
				{
					boolean accepted = fishingSessions.observeVariable(tick, pendingSuccesses.size());
					if (!accepted) { adaptiveInvalidated = true; }
					status = accepted ? "Variable lure timing: possible failure range." : "Timing interrupted. Start harpooning again.";
					traceResult = accepted ? "VARIABLE_RANGE" : "INVALIDATED";
				}
				else { fishingSessions.stopVariable(); status = "Waiting for a start message."; traceResult = "WAITING_FOR_START"; }
				flushPending();
			}
			else if (estimate)
			{
				result = ambiguous ? engine.invalidateTiming(tick)
					: engine.observeTimed(tick, activity, context.setup, context.cycle, eligible,
						eligible ? pendingSuccesses.size() : 0);
				if (result == TimedResult.COUNTED) { clearPending(); }
				if (result != TimedResult.COUNTED && !pendingSuccesses.isEmpty()) { flushPending(); }
				if (result == TimedResult.INVALIDATED)
				{
					adaptiveInvalidated = true;
					timingProblem = ambiguous ? "Outcome unrecognized or ambiguous. Restart this action to anchor a new cycle."
						: eligible ? "Catch or tick outside the expected cycle. Check the first-roll delay, then restart this action."
						: "Tracking interrupted or outcome unrecognized. Restart this action to anchor a new cycle.";
					status = timingProblem;
				}
				else if (result == TimedResult.WAITING_FOR_START)
				{
					status = timingProblem.isEmpty() ? "Waiting for a start message. Stop and start this action to anchor the attempt counter." : timingProblem;
				}
				else { status = "Counting completed " + context.cycle + "-tick cycles from the start message."; }
			}
			else
			{
				engine.interruptTiming();
				flushPending();
				status = context.cycle == 0 ? "Variable or unverified timing: successes only." : "Successes only. Enable fixed-cycle estimates after validating timing.";
			}
			traceEligible = eligible;
			if (!variable) { traceResult = estimate ? result.name() : "SUCCESS_ONLY"; }
			countingFishing = context.skill == Skill.FISHING && eligible && (engine.isTimingActive() || fishingSessions.isVariableActive());
		}
		else
		{
			engine.interruptTiming();
			fishingSessions.stopVariable();
			// Preserve visible successful gathers even if this animation/method is unsupported for timing.
			flushPending();
			status = "Waiting for a supported activity.";
		}
		if (config.saveTrace())
		{
			Actor interaction = player.getInteracting();
			String liveTarget = interaction instanceof NPC ? ((NPC) interaction).getId() + " @ " + interaction.getWorldLocation()
				: interaction == null ? "none" : "non-NPC";
			final String row = TickTrace.row(tick, player.getAnimation(), player.getAnimationFrame(), traceSkill.name(),
				context == null ? "" : context.target, traceXpDelta, lureQuantity(), rawLureSetting,
				context == null ? 0 : context.cycle, context == null ? 0 : context.firstDelay, observedMessagesThisTick, traceEligible, actionStartTick, traceResult,
				liveTarget, context == null ? contextProblem(player) : "", start == null ? "" : start.label(), start == null ? -1 : start.tick,
				incomingHitsThisTick, adaptiveCatchesThisTick, adaptiveActivity.rhythm(), twoTickDiagnostic);
			final long run = generation;
			final TickTrace runTrace = trace;
			submitIo(run, io, () -> { try { runTrace.append(row); } catch (IOException ex) { reportIoError("Tick trace could not be saved", ex, run); } });
		}
		previousContext = context;
		// The activity clock does not depend on a valid shark failure schedule.
		boolean finalFishingCatch = stopAfterTick && adaptiveCatchesThisTick == 1
			&& (countingFishing || twoTickResult == Result.COUNTED);
		boolean previouslyAdaptiveFishing = adaptiveFishing;
		countingFishing = config.fishing() && !stopAfterTick && fishingActivity.update(player, tick);
		adaptiveFishing = config.fishing() && config.adaptiveTiming() && !stopAfterTick && adaptiveActivity.observe(player, tick)
			&& (!sharksOnly() || adaptiveActivity.isSharkMethod());
		fishingSessions.sync(engine.getSessions());
		long strictCatches = fishingSessions.current().measuredCatches;
		if (config.adaptiveTiming())
		{
			boolean unknownOutcome = traceSkill == Skill.FISHING && traceXpDelta > 0 && adaptiveCatchesThisTick == 0;
			boolean includedMethod = !sharksOnly() || (context != null && context.shark) || adaptiveActivity.isSharkMethod();
			adaptiveSample.observe(tick, !twoTickOwnsFrame && includedMethod && !unknownOutcome && (adaptiveFishing || countingFishing), adaptiveCatchesThisTick,
				engine.isTimingActive() || fishingSessions.isVariableActive(), strictStarted,
				adaptiveInvalidated || strictCatches < lastStrictCatches, (int) Math.min(Integer.MAX_VALUE, Math.max(0, strictCatches - lastStrictCatches)));
			fishingSessions.adaptiveSample(adaptiveSample.getCatches(), adaptiveSample.getFailuresUpper(), adaptiveSample.isUsed());
		}
		boolean focusTwoTick = fishingSessions.current().twoTickTiming || twoTickResult == Result.COUNTED;
		if (twoTickAcceptedFishing && !twoTickOwnsFrame) { focusTwoTick = false; }
		fishingSessions.twoTickSample(twoTickTracker.getCatches(), twoTickTracker.getFailures(), focusTwoTick);
		lastStrictCatches = strictCatches;
		countingFishing |= adaptiveFishing;
		if ((countingFishing || finalFishingCatch) && (fishingActivity.getStartedTick() != tick || previouslyAdaptiveFishing)) { fishingSessions.fishingTick(); }
		if (stopAfterTick) { breakTiming(); status = "Action stopped; incomplete cycle excluded."; }
		fishingSessions.advance(true, countingFishing && !stopAfterTick);
		fishingAnimation = countingFishing && !stopAfterTick ? player.getAnimation() : -1;
		if (countingFishing && !engine.isTimingActive() && !fishingSessions.isVariableActive())
		{
			status = adaptiveFishing ? "Adaptive timing: possible attempt range. " + adaptiveActivity.rhythm()
				: "Fishing timer active; catch attempts are not anchored for this method.";
		}
		if (twoTickResult == Result.COUNTED) { status = "Counting qualified 2t harpoon attempts (inferred)."; }
		else if (twoTickResult == Result.ARMED) { status = "2t flinch qualified; awaiting next-tick harpooning."; }
		else if (twoTickResult == Result.INVALIDATED) { status = "2t phase contradicted; current inferred segment excluded."; }
		if (sharkTimingInvalidated && context != null && context.shark)
		{
			status = config.autoDetectLures() ? "Unrecognized lure setting; fishing time and catches are still tracked." : "Lure setting changed. Match Shark lures per catch to the game setting, then restart harpooning.";
		}
		if (config.customEnabled() && !custom.problem.isEmpty()) { status = custom.problem; }
		if (config.diagnostics())
		{
			AttemptTrackerConfig.SharkLures mode = effectiveSharkLures();
			status += " Lures: " + (mode == null ? "unverified" : mode.toString()) + (config.autoDetectLures() ? " (automatic)." : " (manual).");
			status += " Tick " + tick + "; animation " + player.getAnimation() + "; frame " + player.getAnimationFrame()
				+ "; target " + (player.getInteracting() instanceof NPC ? player.getInteracting().getName() + " (" + ((NPC) player.getInteracting()).getId() + ")"
					: fishingTarget == null ? selectedTarget : "remembered NPC " + fishingTarget.getId())
				+ (context == null ? "; " + contextProblem(player) : "") + ".";
			status += " 2t: " + twoTickDiagnostic + ".";
		}
		clearPending(); cancelled = false; stopAfterTick = false; animationStopPending = false; adaptiveCatchesThisTick = 0; adaptiveInvalidated = false; incomingHitsThisTick = 0;
		clearTwoTickFrame();
		publish();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!running) { return; }
		String rawOption = event.getMenuOption() == null ? "" : event.getMenuOption().toLowerCase(java.util.Locale.ROOT);
		if (event.getMenuEntry() != null && event.getMenuEntry().isItemOp() && !rawOption.equals("drop") && !rawOption.startsWith("examine")) { twoTickConflict = true; }
		if (!changesGameAction(event)) { return; }
		String adaptiveOption = event.getMenuOption() == null ? "" : event.getMenuOption().toLowerCase(java.util.Locale.ROOT);
		NPC clicked = event.getMenuEntry() == null ? null : event.getMenuEntry().getNpc();
		boolean sameTileWalk = TwoTickFishingSignals.sameTileWalk(client.getLocalPlayer(), event);
		if (FishingActivityTracker.isFishingOption(adaptiveOption)) { selectTwoTickSpot(clicked); }
		else if (!sameTileWalk) { twoTickConflict = true; }
		if (adaptiveOption.equals("attack")) { lastExplicitAttackTick = client.getTickCount(); }
		if (config.adaptiveTiming() && FishingActivityTracker.isFishingOption(adaptiveOption) && FishingActivityTracker.isFishingSpot(clicked))
		{
			adaptiveActivity.select(clicked, client.getTickCount());
		}
		else if (config.adaptiveTiming() && (sameTileWalk || adaptiveOption.equals("eat") || adaptiveOption.equals("drink")
			|| event.getMenuAction().name().startsWith("ITEM_USE_ON_") || event.getMenuAction().name().startsWith("WIDGET_TARGET_ON_")))
		{
			adaptiveActivity.manipulation(client.getTickCount());
		}
		else { stopAdaptive(); }
		breakTiming();
		selectedTarget = event.getMenuTarget() == null ? "" : Text.removeTags(event.getMenuTarget());
		String option = event.getMenuOption().toLowerCase(java.util.Locale.ROOT);
		selectedObject = (option.equals("mine") || option.equals("chop down") || option.equals("chop"))
			? WorldPoint.fromScene(client, event.getParam0(), event.getParam1(), client.getPlane()) : null;
		if (event.getMenuEntry() != null)
		{
			NPC npc = event.getMenuEntry().getNpc(); rememberFishingTarget(npc);
			if (FishingActivityTracker.isFishingOption(option)) { fishingActivity.select(npc); }
		}
		gatherLabel = "";
	}

	private boolean changesGameAction(MenuOptionClicked event)
	{
		MenuAction action = event.getMenuAction(); if (action == null) { return false; }
		String name = action.name(); String option = event.getMenuOption() == null ? "" : event.getMenuOption().toLowerCase(java.util.Locale.ROOT);
		if (option.equals("drop") || option.startsWith("examine")) { return false; }
		if (option.equals("eat") || option.equals("drink") || option.equals("wield") || option.equals("wear")
			|| option.equals("equip") || option.equals("remove") || option.equals("cast")) { return true; }
		return action == MenuAction.WALK || name.startsWith("NPC_") || name.startsWith("GAME_OBJECT_") || name.startsWith("PLAYER_")
			|| name.startsWith("GROUND_ITEM_") || name.startsWith("ITEM_USE_ON_") || name.startsWith("WIDGET_TARGET_ON_");
	}

	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!running || event.getActor() != client.getLocalPlayer()) { return; }
		if (AnimationCatalog.isHarpoon(event.getActor().getAnimation())) { twoTickFreshHarpoon = true; }
		if (event.getActor().getAnimation() >= 0 && !AnimationCatalog.isHarpoon(event.getActor().getAnimation())) { twoTickOtherAnimation = true; }
		if (fishingAnimation < 0) { return; }
		if (event.getActor().getAnimation() != fishingAnimation)
		{
			if (AnimationCatalog.skillFor(event.getActor().getAnimation()) == Skill.FISHING
				&& AnimationCatalog.methodFor(event.getActor().getAnimation()).equals(AnimationCatalog.methodFor(fishingAnimation))) { return; }
			if (config.adaptiveTiming() && adaptiveActivity.canBridge(client.getTickCount())) { return; }
			// Defer outcome handling to GameTick so an inventory-filling catch is retained.
			stopAfterTick = true; animationStopPending = true; fishingSessions.advance(client.getGameState() == GameState.LOGGED_IN, false);
		}
	}

	private void resetSession()
	{
		breakTiming(); fishingSessions.sync(engine.getSessions()); fishingSessions.reset(); engine.startNewSession();
		stopAdaptive(); adaptiveSample.restore(0, 0, false); lastStrictCatches = 0; adaptiveCatchesThisTick = 0; adaptiveInvalidated = false;
		stopTwoTick(); twoTickTracker.reset(); clearTwoTickFrame();
		status = "Session reset. Interact with a fishing spot to begin."; publish();
	}

	@Subscribe
	public void onInteractingChanged(InteractingChanged event)
	{
		if (!running || event.getSource() != client.getLocalPlayer()) { return; }
		if (event.getTarget() == null) { return; }
		if (isCombatTarget(event.getTarget())) { twoTickCombatRetarget = true; }
		if (!(event.getTarget() instanceof NPC) || !FishingActivityTracker.isFishingSpot((NPC) event.getTarget()))
		{
			breakTiming();
			return;
		}
		NPC npc = (NPC) event.getTarget();
		selectTwoTickSpot(npc); twoTickAcceptedFishing = true;
		if (config.adaptiveTiming()) { adaptiveActivity.select(npc, client.getTickCount()); adaptiveActivity.accepted(client.getTickCount()); }
		if (!fishingActivity.targets(npc))
		{
			// A server tick can deliver its start message before the interaction event.
			PendingStart start = pendingStart; breakTiming();
			if (start != null && start.skill == Skill.FISHING && start.tick == client.getTickCount()) { pendingStart = start; }
		}
		rememberFishingTarget(npc); fishingActivity.select(npc);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		if (twoTickSpot == event.getNpc()) { stopTwoTick(); }
		if (adaptiveActivity.targets(event.getNpc())) { stopAdaptive(); }
		if (fishingActivity.targets(event.getNpc()) || fishingTarget == event.getNpc() || (previousContext != null && previousContext.actor == event.getNpc())) { breakTiming(); }
	}

	@Subscribe
	public void onGameObjectDespawned(GameObjectDespawned event)
	{
		if (selectedObject != null && selectedObject.equals(event.getGameObject().getWorldLocation())) { breakTiming(); }
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (!running) { return; }
		if (event.getGameState() != GameState.LOGGED_IN) { stopTwoTick(); clearTwoTickFrame(); lastExplicitAttackTick = -100; stopAdaptive(); adaptiveCatchesThisTick = 0; breakTiming(); adaptiveSample.discontinuity(); previousPosition = null; lastExperience.clear(); }
		fishingSessions.advance(event.getGameState() == GameState.LOGGED_IN, false); publish();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN) { return; }
		// Some updates arrive as a containing varp instead of a specific varbit.
		if (event.getVarbitId() != VarbitID.SHARK_LURE_USE_QUANTITY && event.getVarbitId() != -1) { return; }
		if (lastDisplayedRawLureSetting == client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)) { return; }
		refreshLureDisplay();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!running || client.getGameState() != GameState.LOGGED_IN || event.getContainerId() != InventoryID.INV) { return; }
		refreshLureDisplay();
	}

	private void refreshLureDisplay()
	{
		LureDisplay display = liveLureDisplay(); lureSnapshot = display;
		final long run = generation;
		SwingUtilities.invokeLater(() -> { synchronized (lifecycleLock) { if (panel != null && isCurrent(run)) { panel.refreshLures(display); } } });
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!AttemptTrackerConfig.GROUP.equals(event.getGroup()) || !running) { return; }
		final long run = generation;
		invokeClient(run, () ->
		{
			boolean ignoredManualLureChoice = config.autoDetectLures() && "sharkLures".equals(event.getKey());
			boolean displayOnly = ignoredManualLureChoice || "showOverlay".equals(event.getKey()) || "diagnostics".equals(event.getKey()) || "saveTrace".equals(event.getKey());
			if (!displayOnly) { custom = new CustomActivity(config); stopTwoTick(); clearTwoTickFrame(); stopAdaptive(); breakTiming(); adaptiveSample.discontinuity(); }
			if ((!config.autoDetectLures() && "sharkLures".equals(event.getKey())) || ("fixedTiming".equals(event.getKey()) && config.fixedTiming()))
			{
				sharkTimingInvalidated = false;
				lastRawLureSetting = client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY);
			}
			if (!config.saveTrace()) { final TickTrace runTrace = trace; submitIo(run, io, () -> closeTrace(runTrace, run)); }
			publish();
		});
	}

	private TimedContext context(Player player)
	{
		int animation = player.getAnimation();
		if (custom.usable() && custom.timed && custom.animations.contains(animation))
		{
			return new TimedContext(custom.skill, custom.name, setup(custom.skill, "custom", true), custom.cycle,
				custom.firstDelay, player.getInteracting(), "Custom", true, false, "custom");
		}
		Skill skill = AnimationCatalog.skillFor(animation);
		if (skill == null || !enabled(skill)) { return null; }
		String method = AnimationCatalog.methodFor(animation);
		Actor target = player.getInteracting();
		String activity;
		int cycle;
		String targetKey;
		boolean shark = false;
		if (skill == Skill.FISHING)
		{
			target = resolveFishingTarget(player);
			if (!(target instanceof NPC)) { return null; }
			FishingSpot spot = fishingSpot((NPC) target);
			if (spot == null) { return null; }
			shark = spot == FishingSpot.SHARK && AnimationCatalog.isHarpoon(animation);
			if (sharksOnly() && !shark) { return null; }
			activity = sharksOnly() ? "Fishing: Shark" : "Fishing: " + spot.getName();
			cycle = shark ? (sharkTimingInvalidated ? 0 : effectiveSharkCycle()) : config.fishingCycle();
			targetKey = ((NPC) target).getId() + " @ " + target.getWorldLocation();
		}
		else
		{
			activity = (skill == Skill.MINING ? "Mining" : "Woodcutting") + (selectedTarget.isEmpty() ? "" : ": " + selectedTarget);
			cycle = skill == Skill.MINING ? config.miningCycle() : config.woodcuttingCycle();
			targetKey = selectedTarget + " @ " + selectedObject;
			if (selectedObject == null) { return null; }
		}
		cycle = Math.max(0, Math.min(100, cycle));
		int firstDelay = shark ? sharkFirstRollDelay(cycle) : cycle;
		return new TimedContext(skill, activity, setup(skill, method, false) + "; first roll delay: " + firstDelay,
			cycle, firstDelay, target, targetKey, false, shark, method);
	}

	private static FishingSpot fishingSpot(NPC npc)
	{
		if (npc == null) { return null; }
		// RuneLite's spot catalog omits this member-fish spot. Its shark output,
		// crystal-harpoon animation and three-lure use were verified in-game.
		return npc.getId() == NpcID._0_40_34_MEMBERFISH ? FishingSpot.SHARK : FishingSpot.findSpot(npc.getId());
	}

	private int sharkFirstRollDelay(int cycle)
	{
		int override = config.sharkFirstRollDelay();
		if (override > 0) { return Math.min(100, override); }
		// A live three-lure trace starts at T and rolls at T+4, T+9, T+14.
		return cycle > 0 ? cycle - 1 : 0;
	}

	private void rememberFishingTarget(NPC npc)
	{
		if (fishingSpot(npc) != null)
		{
			fishingTarget = npc;
			fishingTargetPosition = npc.getWorldLocation();
		}
	}

	private NPC resolveFishingTarget(Player player)
	{
		Actor interaction = player.getInteracting();
		if (interaction != null)
		{
			if (!(interaction instanceof NPC) || fishingSpot((NPC) interaction) == null) { return null; }
			rememberFishingTarget((NPC) interaction);
			return (NPC) interaction;
		}
		if (fishingTarget == null || fishingTargetPosition == null
			|| !fishingTargetPosition.equals(fishingTarget.getWorldLocation())
			|| player.getWorldView() != fishingTarget.getWorldView()
			|| player.getWorldLocation().distanceTo(fishingTargetPosition) > 1) { return null; }
		return fishingTarget;
	}

	private void clearFishingTarget() { fishingTarget = null; fishingTargetPosition = null; }

	private String contextProblem(Player player)
	{
		Skill skill = AnimationCatalog.skillFor(player.getAnimation());
		if (skill == null) { return "Unsupported animation " + player.getAnimation(); }
		if (!enabled(skill)) { return "Skill tracking disabled"; }
		if (skill == Skill.FISHING)
		{
			if (player.getInteracting() instanceof NPC && fishingSpot((NPC) player.getInteracting()) == null)
			{
				return "Unknown fishing NPC " + ((NPC) player.getInteracting()).getId();
			}
			NPC target = resolveFishingTarget(player);
			if (target == null) { return "No valid fishing NPC"; }
			FishingSpot spot = fishingSpot(target);
			if (spot == null) { return "Unknown fishing NPC " + target.getId(); }
			if (sharksOnly() && (spot != FishingSpot.SHARK || !AnimationCatalog.isHarpoon(player.getAnimation()))) { return "Method or spot excluded by shark filter"; }
		}
		return "No selected gathering target";
	}

	private String setup(Skill skill, String method, boolean isCustom)
	{
		Player player = client.getLocalPlayer();
		StringBuilder result = new StringBuilder();
		result.append("Character: ").append(player == null ? "Unknown" : player.getName())
			.append("; ").append(skill.getName()).append(" ").append(client.getRealSkillLevel(skill))
			.append(" (boosted ").append(client.getBoostedSkillLevel(skill)).append("); method: ").append(method);
		if (player != null) { result.append("; region: ").append(player.getWorldLocation().getRegionID()); }
		result.append("; world types: ").append(client.getWorldType());
		TreeSet<Integer> gear = new TreeSet<>();
		ItemContainer equipment = client.getItemContainer(InventoryID.WORN);
		if (equipment != null) { for (Item item : equipment.getItems()) { if (item != null && item.getId() >= 0) { gear.add(item.getId()); } } }
		TreeSet<String> tools = new TreeSet<>();
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory != null)
		{
			for (Item item : inventory.getItems())
			{
				if (item == null || item.getId() < 0) { continue; }
				String name = itemManager.getItemComposition(item.getId()).getName().toLowerCase(java.util.Locale.ROOT);
				if (name.contains("harpoon") || name.contains("pickaxe") || name.endsWith(" axe") || name.contains("fishing rod")
					|| name.contains("tackle box") || name.contains("fishing tackle") || name.contains("spirit flakes")) { tools.add(name); }
			}
		}
		result.append("; worn IDs: ").append(gear).append("; carried tools: ").append(tools);
		if (skill == Skill.FISHING)
		{
			AttemptTrackerConfig.SharkLures mode = effectiveSharkLures();
			result.append("; lure mode: ").append(mode == null ? "UNKNOWN" : mode.name()).append("; raw lure setting: ")
				.append(client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY))
				.append("; lure supply: ").append(lureQuantity() > 0 ? "inventory" : hasTackleBox() ? "tackle box (contents unverified)" : "none");
		}
		if (isCustom) { result.append("; custom definition: ").append(custom.signature()); }
		String notes = CustomActivity.trim(config.setupNotes(), 240);
		if (!notes.isEmpty()) { result.append("; notes: ").append(notes); }
		return result.toString();
	}

	private boolean enabled(Skill skill)
	{
		switch (skill)
		{
			case COOKING: return config.cooking();
			case THIEVING: return config.pickpocketing();
			case FISHING: return config.fishing();
			case MINING: return config.mining();
			case WOODCUTTING: return config.woodcutting();
			default: return false;
		}
	}

	private void queueSuccess(String activity, Skill skill, boolean isCustom)
	{
		Player player = client.getLocalPlayer();
		TimedContext captured = player == null ? null : context(player);
		if (captured == null && !cancelled && previousContext != null
			&& previousContext.skill == skill && previousContext.custom == isCustom && player != null
			&& (skill == Skill.FISHING ? resolveFishingTarget(player) : player.getInteracting()) == previousContext.actor
			&& previousPosition != null && previousPosition.equals(player.getWorldLocation()))
		{
			// Some final catches end the animation before their chat event arrives.
			captured = previousContext;
		}
		if (captured != null && (captured.skill != skill || captured.custom != isCustom)) { captured = null; }
		String method = captured == null ? (isCustom ? "custom" : "unverified timing") : captured.method;
		String capturedSetup = setup(skill, method, isCustom);
		if (captured != null && !isCustom) { capturedSetup += "; first roll delay: " + captured.firstDelay; }
		pendingSuccesses.add(new PendingSuccess(activity, skill, capturedSetup, isCustom, captured, client.getTickCount()));
	}

	private void breakTiming()
	{
		breakTiming(true);
	}
	private boolean sharksOnly() { return config.sharksOnly() && !config.trackAllFish(); }
	private void stopAdaptive() { adaptiveActivity.stop(); adaptiveFishing = false; }
	private void clearTwoTickFrame()
	{
		twoTickCombatHit = twoTickCombatRetarget = twoTickAcceptedFishing = twoTickConflict = twoTickOtherAnimation = false;
		twoTickHarpoonStart = twoTickFreshHarpoon = twoTickRefused = false;
	}
	private void stopTwoTick()
	{
		twoTickTracker.discontinuity(); twoTickSpot = null; twoTickSpotPosition = twoTickPlayerPosition = null;
		twoTickIdleBefore = false; twoTickBlockedBefore = false; twoTickSetup = null;
	}
	private void selectTwoTickSpot(NPC spot)
	{
		if (!TwoTickFishingSignals.supportedSpot(spot)) { stopTwoTick(); return; }
		if (spot != twoTickSpot || twoTickSpotPosition == null || !twoTickSpotPosition.equals(spot.getWorldLocation()))
		{
			stopTwoTick(); twoTickSpot = spot; twoTickSpotPosition = spot.getWorldLocation();
		}
	}
	private boolean validTwoTickSpot(Player player)
	{
		WorldPoint position = player.getWorldLocation();
		return TwoTickFishingSignals.supportedSpot(twoTickSpot) && twoTickSpotPosition != null
			&& twoTickSpotPosition.equals(twoTickSpot.getWorldLocation()) && position != null
			&& position.getPlane() == twoTickSpotPosition.getPlane() && position.distanceTo(twoTickSpotPosition) <= 1
			&& player.getWorldView() != null && player.getWorldView() == twoTickSpot.getWorldView();
	}
	private int twoTickSetupFingerprint(int speed, int rawLures)
	{
		// Supply exhaustion/level-ups can result from a successful roll. They do
		// not change flinch cadence and must not selectively exclude that catch.
		int hash = java.util.Objects.hash(speed, rawLures, client.getVarpValue(VarPlayerID.COM_MODE),
			client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY));
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		Item weapon = worn == null ? null : worn.getItem(net.runelite.api.EquipmentInventorySlot.WEAPON.getSlotIdx());
		hash = 31 * hash + (weapon == null ? -1 : weapon.getId());
		return hash;
	}
	private boolean isCombatTarget(Actor target)
	{
		if (target == client.getLocalPlayer()) { return false; }
		if (target instanceof Player) { return true; }
		if (!(target instanceof NPC) || FishingActivityTracker.isFishingSpot((NPC) target)) { return false; }
		net.runelite.api.NPCComposition definition = ((NPC) target).getTransformedComposition();
		if (definition != null && definition.getActions() != null)
		{
			for (String action : definition.getActions()) { if ("Attack".equalsIgnoreCase(action)) { return true; } }
		}
		return false;
	}
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (running && config.adaptiveTiming() && client.getGameState() == GameState.LOGGED_IN
			&& event.getActor() == client.getLocalPlayer())
		{
			adaptiveActivity.manipulation(client.getTickCount()); incomingHitsThisTick++;
			if (TwoTickFishingSignals.combatHitsplat(event.getHitsplat())) { twoTickCombatHit = true; }
			if (adaptiveFishing && adaptiveActivity.canBridge(client.getTickCount())) { fishingSessions.advance(true, true); }
		}
	}
	private void breakTiming(boolean stopFishingClock)
	{
		if (fishingSessions.isVariableActive() && !pendingSuccesses.isEmpty())
		{
			if (pendingSuccesses.size() == 1 && previousContext != null && pendingSuccesses.get(0).matches(previousContext))
			{
				if (!fishingSessions.observeVariable(pendingSuccesses.get(0).tick, 1)) { adaptiveInvalidated = true; }
			}
			else { fishingSessions.observeVariable(pendingSuccesses.get(0).tick, 2); adaptiveInvalidated = true; }
		}
		// A known catch on the due tick remains a completed attempt even if a
		// later packet/menu action stops fishing before GameTick dispatch.
		if (engine.isTimingActive() && pendingSuccesses.size() == 1 && previousContext != null
			&& pendingSuccesses.get(0).matches(previousContext))
		{
			TimedResult result = engine.observeTimed(pendingSuccesses.get(0).tick, previousContext.activity,
				previousContext.setup, previousContext.cycle, true, 1);
			if (result == TimedResult.INVALIDATED) { adaptiveInvalidated = true; }
			if (result == TimedResult.COUNTED) { clearPending(); }
		}
		engine.interruptTiming(); flushPending(); fishingSessions.stopVariable();
		if (stopFishingClock)
		{
			fishingActivity.stop();
			// The adaptive frame resolves brief manipulation transitions. Hard stops
			// clear adaptiveFishing first; do not lose time between a re-click and GameTick.
			fishingSessions.advance(client != null && client.getGameState() == GameState.LOGGED_IN,
				config.adaptiveTiming() && adaptiveFishing);
		}
		previousContext = null; clearFishingTarget(); cancelled = true; gatherLabel = ""; pendingStart = null; fishingAnimation = -1;
	}
	private void flushPending()
	{
		for (PendingSuccess success : pendingSuccesses)
		{
			engine.recordSuccessOnly(success.activity, success.setup);
		}
		clearPending();
	}
	private void clearPending() { pendingSuccesses.clear(); }

	private int effectiveSharkCycle()
	{
		AttemptTrackerConfig.SharkLures mode = effectiveSharkLures();
		return mode == null ? 0 : mode.getCycle();
	}
	private AttemptTrackerConfig.SharkLures effectiveSharkLures()
	{
		// A saved preference does not prove that any lures are available.
		if (client.getItemContainer(InventoryID.INV) == null) { return null; }
		if (lureQuantity() <= 0) { return hasTackleBox() ? null : AttemptTrackerConfig.SharkLures.NONE; }
		if (!config.autoDetectLures()) { return config.sharkLures(); }
		return AttemptTrackerConfig.SharkLures.fromGameValue(client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY));
	}
	private LureDisplay liveLureDisplay()
	{
		if (client == null || client.getGameState() != GameState.LOGGED_IN) { return LureDisplay.waiting(); }
		int raw = client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY); lastDisplayedRawLureSetting = raw;
		AttemptTrackerConfig.SharkLures selected = config.autoDetectLures() ? AttemptTrackerConfig.SharkLures.fromGameValue(raw) : config.sharkLures();
		String amount = selected == null ? "Unknown" : selected == AttemptTrackerConfig.SharkLures.NONE ? "None" : selected.getQuantity() + " per catch";
		String note = config.autoDetectLures() ? "Detected automatically" : "Manual override";
		String tooltip = "Live lure availability, independent of the session selected in history. Selected choice: " + amount + ".";
		if (client.getItemContainer(InventoryID.INV) == null) { amount = "--"; note = "Inventory loading"; }
		else if (lureQuantity() <= 0)
		{
			boolean hiddenSupply = hasTackleBox();
			amount = hiddenSupply ? "Unknown" : "None";
			note = hiddenSupply ? "Tackle box supply unverified" : "No lures available";
			tooltip += hiddenSupply ? " Hidden supplies cannot establish a lure attempt schedule." : " No shark lures are carried. Estimates use the no-lure schedule until lures are available.";
		}
		else if (selected == null) { note = "Choice not recognized"; tooltip += " Catch attempts cannot be estimated for this choice."; }
		else if (fishingActivity.isActive() && previousContext != null && previousContext.shark
			&& ((lastRawLureSetting != null && lastRawLureSetting != raw) || (!engine.isTimingActive() && !fishingSessions.isVariableActive())))
		{
			note = "Restart harpooning for attempts";
			tooltip += " Fishing time continues; a new harpooning start anchors attempt estimates.";
		}
		return new LureDisplay(amount, note, tooltip);
	}

	private boolean hasTackleBox()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null) { return false; }
		for (Item item : inventory.getItems())
		{
			if (item != null && item.getId() == ItemID.TACKLE_BOX) { return true; }
		}
		return false;
	}

	private int lureQuantity()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		return inventory == null ? 0 : inventory.count(ItemID.SHARK_LURE);
	}

	private boolean inventoryFull()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INV);
		if (inventory == null || inventory.getItems() == null || inventory.getItems().length < 28) { return false; }
		// Occupied slots alone do not prove fishing is blocked: stackable catches
		// and an open barrel can still accept fish. The game's stop message wins.
		for (Item item : inventory.getItems())
		{
			if (item != null && (item.getId() == ItemID.FISH_BARREL_OPEN || item.getId() == ItemID.FISH_SACK_BARREL_OPEN)) { return false; }
		}
		Player player = client.getLocalPlayer();
		NPC target = player != null && player.getInteracting() instanceof NPC ? (NPC) player.getInteracting() : fishingTarget;
		FishingSpot spot = target == null ? null : FishingSpot.findSpot(target.getId());
		if ((spot == FishingSpot.MINNOW && inventory.count(ItemID.MINNOW) > 0)
			|| (spot == FishingSpot.KARAMBWANJI && inventory.count(ItemID.TBWT_RAW_KARAMBWANJI) > 0)) { return false; }
		for (int slot = 0; slot < 28; slot++)
		{
			Item item = inventory.getItems()[slot];
			if (item == null || item.getId() < 0 || item.getQuantity() <= 0) { return false; }
		}
		return true;
	}

	static String pickpocketVictim(String normalized)
	{
		String name = normalized.replaceFirst("^you (?:fail to )?pick (?:the )?", "").replaceFirst(" pocket\\.$", "").replaceFirst("'s$", "").replaceFirst("'$", "");
		return name.isEmpty() ? "" : Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private void publish()
	{
		final long run = generation;
		if (!isCurrent(run)) { return; }
		currentSnapshot = engine.getCurrentSession(); historySnapshot = engine.getSessions();
		fishingSessions.sync(historySnapshot); fishingSnapshot = fishingSessions.current().copy();
		lureSnapshot = liveLureDisplay();
		summaryStatus = client == null || client.getGameState() != GameState.LOGGED_IN ? "Logged out"
			: fishingActivity.isActive() || adaptiveFishing ? "Fishing" : "Paused - select a fishing spot";
		if (paused) { status = "Paused."; }
		String panelStatus = statusWithNotice();
		final String displayStatus = panelStatus;
		List<AttemptSession> snapshot = historySnapshot;
		final boolean wasPaused = paused;
		final List<FishingSession> fishSnapshot = fishingSessions.snapshots(); final String summary = simpleStatus(); final LureDisplay lures = lureSnapshot;
		SwingUtilities.invokeLater(() -> { synchronized (lifecycleLock) { if (panel != null && isCurrent(run)) { panel.refresh(fishSnapshot, summary, displayStatus, lures); } } });
		long fingerprint = 1;
		for (AttemptSession session : snapshot) { fingerprint = 31 * fingerprint + java.util.Objects.hash(session.getId(), session.getSuccesses(), session.getFailures(), session.getExcludedWindows()); }
		fishingHistorySnapshot = fishSnapshot;
		for (FishingSession session : fishSnapshot) { fingerprint = 31 * fingerprint + java.util.Objects.hash(session.id, session.catches, session.measuredCatches, session.minimumFailures, session.maximumFailures, session.adaptiveCatches, session.adaptiveFailureUpper, session.adaptiveTiming, session.twoTickCatches, session.twoTickFailures, session.twoTickTiming, session.fishingTicks, session.loggedMillis / 5000, session.fishingMillis / 5000, summaryStatus); }
		synchronized (lifecycleLock)
		{
		if (isCurrent(run) && io != null && !io.isShutdown() && fingerprint != savedFingerprint)
		{
			cancelPendingSave();
			savedFingerprint = fingerprint;
			final SessionStore runStore = store;
			final FishingSessionStore runFishingStore = fishingStore;
			pendingSave = io.schedule(() -> { if (isCurrent(run)) { save(runStore, snapshot, run); saveFishing(runFishingStore, fishSnapshot, run); } }, 200, TimeUnit.MILLISECONDS);
		}
		}
	}

	private String statusWithNotice() { return status + (notice.isEmpty() ? "" : " " + notice); }
	private String simpleStatus()
	{
		return summaryStatus;
	}
	private void saveFishing(FishingSessionStore target, List<FishingSession> sessions, long run)
	{
		synchronized (persistenceLock)
		{
			if (generation != run || target == null) { return; }
			try { target.save(sessions); } catch (IOException ex) { reportIoError("Fishing sessions could not be saved", ex, run); savedFingerprint = Long.MIN_VALUE; }
		}
	}
	private boolean isCurrent(long run) { return running && generation == run; }
	private void cancelPendingSave() { if (pendingSave != null) { pendingSave.cancel(false); pendingSave = null; } }
	private void invokeClient(long run, Runnable action)
	{
		clientThread.invoke(() -> { synchronized (lifecycleLock) { if (isCurrent(run)) { action.run(); } } });
	}
	private void submitIo(long run, ScheduledExecutorService worker, Runnable action)
	{
		synchronized (lifecycleLock)
		{
			if (!isCurrent(run) || worker == null || worker != io || worker.isShutdown()) { return; }
			try { worker.execute(() -> { if (isCurrent(run)) { action.run(); } }); }
			catch (RejectedExecutionException ex) { LOG.debug("Attempt Tracker worker has stopped", ex); }
		}
	}
	private void save(SessionStore runStore, List<AttemptSession> sessions, long run)
	{
		synchronized (persistenceLock)
		{
			// Loading a new run cannot race an older atomic replacement of the same file.
			if (generation != run || runStore == null) { return; }
			try { runStore.save(sessions); }
			catch (IOException ex) { reportIoError("History could not be saved", ex, run); savedFingerprint = Long.MIN_VALUE; }
		}
	}
	private void closeTrace(TickTrace runTrace, long run) { if (runTrace != null) { try { runTrace.close(); } catch (IOException ex) { reportIoError("Trace could not be closed", ex, run); } } }
	private void reportIoError(String description, IOException ex, long run) { if (generation == run) { notice = description + "."; } LOG.warn(description, ex); }

	private void exportCsv(long run)
	{
		if (!isCurrent(run)) { return; }
		final FishingSessionStore runStore = fishingStore;
		final ScheduledExecutorService worker = io;
		final AttemptTrackerPanel exportPanel = panel;
		List<Filepath> selection = new Filepath.Chooser()
			.setIsSave()
			.setDialogTitle("Export Attempt Tracker sessions")
			.addExtensionFilter("CSV files", "csv")
			.setDefaultExtension("csv")
			.setFileName("attempt-tracker-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".csv")
			.showDialog(exportPanel);
		if (selection == null || selection.isEmpty() || !isCurrent(run) || worker == null || worker.isShutdown()) { return; }
		final Filepath target = selection.get(0);
		if (target.exists() && JOptionPane.showConfirmDialog(exportPanel, "Replace this CSV file?", "Export CSV", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) { return; }
		if (!isCurrent(run) || worker.isShutdown()) { return; }
		final List<FishingSession> snapshot = new ArrayList<>(fishingHistorySnapshot);
		submitIo(run, worker, () ->
		{
			try { runStore.exportCsv(snapshot, target); SwingUtilities.invokeLater(() -> { if (isCurrent(run)) { JOptionPane.showMessageDialog(exportPanel, "Saved " + snapshot.size() + " sessions to:\n" + target, "Attempt Tracker", JOptionPane.INFORMATION_MESSAGE); } }); }
			catch (IOException ex) { reportIoError("CSV export failed", ex, run); SwingUtilities.invokeLater(() -> { if (isCurrent(run)) { JOptionPane.showMessageDialog(exportPanel, ex.getMessage(), "CSV export failed", JOptionPane.ERROR_MESSAGE); } }); }
		});
	}

	static BufferedImage createIcon()
	{
		return ImageUtil.loadImageResource(AttemptTrackerPlugin.class, "icon.png");
	}

	private static final class TimedContext
	{
		final Skill skill; final String activity; final String setup; final int cycle; final int firstDelay;
		final Actor actor; final String target; final boolean custom; final boolean shark; final String method;
		TimedContext(Skill skill, String activity, String setup, int cycle, int firstDelay, Actor actor, String target,
			boolean custom, boolean shark, String method)
		{ this.skill = skill; this.activity = activity; this.setup = setup; this.cycle = cycle; this.firstDelay = firstDelay; this.actor = actor; this.target = target; this.custom = custom; this.shark = shark; this.method = method; }
		boolean sameTarget(TimedContext other) { return skill == other.skill && actor == other.actor && target.equals(other.target) && custom == other.custom; }
	}

	private static final class PendingSuccess
	{
		final String activity; final Skill skill; final String setup; final boolean custom; final TimedContext captured; final int tick;
		PendingSuccess(String activity, Skill skill, String setup, boolean custom, TimedContext captured, int tick)
		{ this.activity = activity; this.skill = skill; this.setup = setup; this.custom = custom; this.captured = captured; this.tick = tick; }
		boolean matches(TimedContext context)
		{
			return captured != null && skill == context.skill && custom == context.custom
				&& captured.sameTarget(context) && setup.equals(context.setup);
		}
	}

	private static final class PendingStart
	{
		final int tick; final Skill skill; final boolean custom; final boolean harpooning;
		PendingStart(int tick, Skill skill, boolean custom, boolean harpooning)
		{ this.tick = tick; this.skill = skill; this.custom = custom; this.harpooning = harpooning; }
		boolean matches(TimedContext context, int currentTick)
		{
			return tick == currentTick && skill == context.skill && custom == context.custom
				&& (custom || skill != Skill.FISHING || (harpooning
					? context.method.startsWith("harpoon") : context.method.startsWith("fishing rod")));
		}
		String label() { return custom ? "CUSTOM" : harpooning ? "HARPOON" : skill.name(); }
	}
}

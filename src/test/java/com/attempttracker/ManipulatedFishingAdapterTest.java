package com.attempttracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.attempttracker.core.FishingSession;
import com.attempttracker.core.FishingSessions;
import com.attempttracker.core.ManipulatedFishingTracker;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.InteractingChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;

/** Callback replays exercise real adapter qualification, without setting model evidence flags. */
public class ManipulatedFishingAdapterTest
{
	@Test
	public void twoTickHarpoonWorksWithLingeringFishingNpcAtEveryGameTick() throws Exception
	{
		Harness h = new Harness(false); h.seed();
		for (int roll = 1; roll <= 50; roll++)
		{
			h.flinch(roll % 2 == 0); h.tick();
			h.acceptFishing(roll % 5 != 0); h.tick();
			assertEquals(h.spot, h.player.getInteracting());
		}
		assertEquals(41, h.session().catches);
		assertEquals(40, h.session().modeledCatches); assertEquals(10, h.session().modeledFailureLower);
		assertEquals(10, h.session().modeledFailureUpper); assertEquals(0.8, h.session().rate(false), 0);
	}

	@Test
	public void confirmedTarAndKnifeProductionSupportRodAndHarpoonThreeTickRolls() throws Exception
	{
		for (boolean rod : new boolean[]{false, true})
		{
			for (boolean knife : new boolean[]{false, true})
			{
				Harness h = new Harness(rod); h.seed();
				for (int roll = 1; roll <= 10; roll++)
				{
					h.production(knife); h.tick();
					if (rod) { h.acceptFishing(false); } h.tick();
					if (rod) { if (roll % 2 == 0) { h.catchFish(); } }
					else { h.acceptFishing(roll % 2 == 0); }
					h.tick();
				}
				assertEquals("All observed catches retained", 6, h.session().catches);
				assertEquals(5, h.session().modeledCatches); assertEquals(5, h.session().modeledFailureLower);
				assertEquals(5, h.session().modeledFailureUpper); assertEquals(0.5, h.session().rate(false), 0);
			}
		}
	}

	@Test
	public void confirmedConsumptionWorksWithEitherPacketOrderAndNamedEatVariants() throws Exception
	{
		for (boolean inventoryFirst : new boolean[]{false, true})
		{
			for (int eatAnimation : new int[]{AnimationID.HUMAN_EAT, AnimationID.HUMAN_EAT_WITHSOUND})
			{
				Harness h = new Harness(false); h.seed();
				h.eat(ItemID.TBWT_COOKED_KARAMBWAN, true, true, inventoryFirst, eatAnimation); h.tick();
				h.acceptFishing(false); h.tick();
				assertEquals(1, h.session().modeledFailureLower); assertEquals(1, h.session().modeledFailureUpper);
				assertEquals(0, h.session().modeledCatches);
			}
		}
	}

	@Test
	public void eatInputOrQuantityChangeAloneNeverProvidesFoodTimer() throws Exception
	{
		for (boolean missingInput : new boolean[]{false, true})
		{
			Harness h = new Harness(false); h.seed();
			h.eat(ItemID.TBWT_COOKED_KARAMBWAN, !missingInput, missingInput, false, AnimationID.HUMAN_EAT); h.tick();
			h.acceptFishing(false); h.tick();
			assertEquals(0, h.model().getAttempts()); assertEquals(1, h.session().catches);
		}
	}

	@Test
	public void droppedFoodCannotConfirmAnEarlierRejectedEat() throws Exception
	{
		Harness h = new Harness(false); h.seed();
		h.itemOp("Eat", ItemID.TBWT_COOKED_KARAMBWAN);
		h.itemOp("Drop", ItemID.TBWT_COOKED_KARAMBWAN);
		// A potion can use the same animation. The food disappearance is a drop.
		h.animation(AnimationID.HUMAN_EAT); h.decrementFood(ItemID.TBWT_COOKED_KARAMBWAN); h.tick();
		h.acceptFishing(false); h.tick(); assertEquals(0, h.model().getAttempts());
	}

	@Test
	public void laterDuplicateProductionAnimationCannotReuseOneInputToken() throws Exception
	{
		Harness h = new Harness(true); h.seed(); h.production(false); h.tick();
		h.acceptFishing(false); h.tick();
		// Reassignment after the original input was consumed is not another recipe.
		h.animation(AnimationID.HUMAN_SALAMANDER_TAR_GRIND); h.tick();
		h.acceptFishing(false); h.tick(); h.tick();
		assertEquals(0, h.model().getAttempts()); assertFalse(h.model().isActive());
	}

	@Test
	public void anUnsupportedMethodOnTheSameNpcCannotInheritHarpoonFamily() throws Exception
	{
		Harness h = new Harness(false); h.seed(); h.flinch(false); h.tick();
		h.interact(h.spot); h.animation(AnimationID.HUMAN_LARGENET); h.tick();
		assertEquals(0, h.model().getAttempts()); assertFalse(h.model().isAnchored());
		assertEquals(1, h.session().catches);
	}

	@Test
	public void irregularTwoAndThreeTickHarpoonCyclesShareTheQualifiedDenominator() throws Exception
	{
		Harness h = new Harness(false); h.seed();
		h.flinch(false); h.tick(); h.acceptFishing(false); h.tick();
		h.production(false); h.tick(); h.tick(); h.acceptFishing(true); h.tick();
		h.flinch(true); h.tick(); h.acceptFishing(false); h.tick();
		h.production(true); h.tick(); h.tick(); h.acceptFishing(true); h.tick();
		assertEquals(3, h.session().catches); assertEquals(2, h.session().modeledCatches);
		assertEquals(2, h.session().modeledFailureLower); assertEquals(2, h.session().modeledFailureUpper);
		assertEquals(0.5, h.session().rate(false), 0);
	}

	@Test
	public void alternatingProductionAndConfirmedKarambwanProducesTwoPointFiveTickRodSample() throws Exception
	{
		Harness h = new Harness(true); h.seed();
		h.production(false); h.tick(); h.acceptFishing(false); h.tick(); h.catchFish(); h.tick();
		h.eat(ItemID.TBWT_COOKED_KARAMBWAN, true, true, false, AnimationID.HUMAN_EAT);
		h.acceptFishing(false); h.tick(); h.tick();
		h.production(true); h.tick(); h.acceptFishing(false); h.tick(); h.catchFish(); h.tick();
		assertEquals(2, h.session().modeledCatches); assertEquals(1, h.session().modeledFailureLower);
		assertEquals(1, h.session().modeledFailureUpper); assertEquals(2.0 / 3, h.session().rate(false), 0);
	}

	@Test
	public void unresolvedComboEatingDoesNotApplyOnlyTheLastFoodDelay() throws Exception
	{
		Harness h = new Harness(false); h.seed(); h.food.put(ItemID.SHARK, 3);
		h.itemOp("Eat", ItemID.SHARK); h.itemOp("Eat", ItemID.TBWT_COOKED_KARAMBWAN);
		h.interact(null); h.animation(AnimationID.HUMAN_EAT);
		h.decrementFood(ItemID.SHARK); h.decrementFood(ItemID.TBWT_COOKED_KARAMBWAN); h.tick();
		h.acceptFishing(false); h.tick(); assertEquals(0, h.model().getAttempts());
	}

	@Test
	public void productionAndFoodInOneFrameDoNotAssumeActionOrdering() throws Exception
	{
		for (boolean foodFirst : new boolean[]{false, true})
		{
			Harness h = new Harness(false); h.seed(); h.flinch(false); h.tick(); h.acceptFishing(false); h.tick();
			if (foodFirst) { h.eat(ItemID.TBWT_COOKED_KARAMBWAN, true, true, false, AnimationID.HUMAN_EAT); }
			h.production(false);
			if (!foodFirst) { h.eat(ItemID.TBWT_COOKED_KARAMBWAN, true, true, false, AnimationID.HUMAN_EAT); }
			h.tick(); h.acceptFishing(false); h.tick(); h.tick();
			assertEquals("Prior completed sample retained", 1, h.model().getAttempts());
			assertEquals(1, h.session().modeledFailureLower); assertEquals(1, h.session().modeledFailureUpper);
		}
	}

	@Test
	public void unrelatedRecipeOrMismatchedProductionAnimationIsExcluded() throws Exception
	{
		for (boolean wrongAnimation : new boolean[]{false, true})
		{
			Harness h = new Harness(false); h.seed();
			h.recipe(wrongAnimation ? ItemID.GUAM_LEAF : ItemID.KNIFE, wrongAnimation ? ItemID.SWAMP_TAR : ItemID.LOGS);
			h.interact(null); h.animation(AnimationID.HUMAN_FLETCHING); h.tick();
			h.tick(); h.acceptFishing(false); h.tick(); assertEquals(0, h.model().getAttempts());
		}
	}

	@Test
	public void delayedRecipeAcceptanceDoesNotInventFailureOrSubtractPriorSample() throws Exception
	{
		Harness h = new Harness(false); h.seed(); h.flinch(false); h.tick(); h.acceptFishing(false); h.tick();
		h.recipe(ItemID.GUAM_LEAF, ItemID.SWAMP_TAR); h.tick();
		h.interact(null); h.animation(AnimationID.HUMAN_SALAMANDER_TAR_GRIND); h.tick();
		h.tick(); h.acceptFishing(false); h.tick();
		assertEquals(1, h.model().getAttempts()); assertEquals(1, h.session().modeledFailureLower);
		assertEquals(1, h.session().modeledFailureUpper); assertEquals(1, h.session().catches);
	}

	@Test
	public void moreThanOneMissingFoodOrNonItemEatMenuCannotProveConsumption() throws Exception
	{
		for (boolean nonItemMenu : new boolean[]{false, true})
		{
			Harness h = new Harness(false); h.seed();
			if (nonItemMenu)
			{
				MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(MenuAction.CC_OP);
				when(entry.getOption()).thenReturn("Eat"); when(entry.getItemId()).thenReturn(ItemID.TBWT_COOKED_KARAMBWAN);
				h.plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
			}
			else { h.itemOp("Eat", ItemID.TBWT_COOKED_KARAMBWAN); }
			h.interact(null); h.animation(AnimationID.HUMAN_EAT);
			if (!nonItemMenu) { h.food.put(ItemID.TBWT_COOKED_KARAMBWAN, 3); }
			else { h.food.put(ItemID.TBWT_COOKED_KARAMBWAN, 4); }
			h.plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, h.inventory)); h.tick();
			h.acceptFishing(false); h.tick(); assertEquals(0, h.model().getAttempts());
		}
	}

	@Test
	public void slowerKnownWeaponsUseTheirOwnHalfSpeedFlinchTimers() throws Exception
	{
		for (int speed : new int[]{4, 6})
		{
			Harness h = new Harness(false); h.weaponSpeed(speed); h.seed(); h.flinch(false); h.tick();
			int dueTick = 1 + speed / 2;
			while (h.tick < dueTick)
			{
				assertEquals(0, h.model().getAttempts()); h.tick();
			}
			h.acceptFishing(speed == 4); h.tick();
			assertEquals(1, h.model().getAttempts());
			assertEquals(speed == 4 ? 1 : 0, h.session().modeledCatches);
			assertEquals(speed == 6 ? 1 : 0, h.session().modeledFailureLower);
			assertEquals(h.session().modeledFailureLower, h.session().modeledFailureUpper);
		}
	}

	@Test
	public void actorClearAfterAnIncomingCueCannotRetroactivelyQualifyAfkFlinch() throws Exception
	{
		Harness h = new Harness(false);
		h.acceptFishing(false); h.tick(); h.tick(); h.tick(); h.tick(); h.catchFish(); h.tick();
		// This ordinary catch was not a freshly re-clicked timer-zero harpoon.
		h.flinch(false); h.interact(null); h.tick(); h.acceptFishing(false); h.tick();
		assertEquals(1, h.session().catches); assertEquals(0, h.model().getAttempts());
	}

	private static final class Harness
	{
		final AttemptTrackerPlugin plugin = new AttemptTrackerPlugin();
		final Client client = mock(Client.class);
		final Player player = mock(Player.class);
		final NPC spot = mock(NPC.class);
		final AttemptTrackerConfig config = mock(AttemptTrackerConfig.class, CALLS_REAL_METHODS);
		final ItemManager itemManager = mock(ItemManager.class);
		final ItemContainer inventory = mock(ItemContainer.class);
		final EnumMap<Skill, Integer> experience = new EnumMap<>(Skill.class);
		final Map<Integer, Integer> food = new HashMap<>();
		final boolean rod;
		int tick;

		Harness(boolean rod) throws Exception
		{
			this.rod = rod;
			when(config.adaptiveTiming()).thenReturn(true); when(config.trackAllFish()).thenReturn(true);
			when(config.autoDetectLures()).thenReturn(true); when(config.fixedTiming()).thenReturn(false);
			when(client.getLocalPlayer()).thenReturn(player); when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
			when(client.getTickCount()).thenAnswer(call -> tick); when(client.getGameCycle()).thenAnswer(call -> tick * 30);
			when(client.getSkillExperience(any(Skill.class))).thenAnswer(call -> experience.getOrDefault(call.getArgument(0), 0));
			when(client.getRealSkillLevel(any(Skill.class))).thenReturn(99); when(client.getBoostedSkillLevel(any(Skill.class))).thenReturn(99);
			when(client.getVarbitValue(VarbitID.SHARK_LURE_USE_QUANTITY)).thenReturn(1);
			when(player.getName()).thenReturn("Adapter Tester");
			WorldView world = mock(WorldView.class); when(player.getWorldView()).thenReturn(world); when(spot.getWorldView()).thenReturn(world);
			when(player.getWorldLocation()).thenReturn(new WorldPoint(3200, 3200, 0));
			when(spot.getWorldLocation()).thenReturn(new WorldPoint(3200, 3201, 0));
			when(spot.getId()).thenReturn((rod ? FishingSpot.BARB_FISH : FishingSpot.SHARK).getIds()[0]);
			when(spot.getName()).thenReturn("Fishing spot");
			when(player.getInteracting()).thenReturn(spot); when(player.getAnimation()).thenReturn(fishingAnimation());
			food.put(ItemID.TBWT_COOKED_KARAMBWAN, 5); food.put(ItemID.BRUT_ROE, 5);
			when(client.getItemContainer(InventoryID.INV)).thenReturn(inventory);
			when(inventory.count(anyInt())).thenAnswer(call -> call.<Integer>getArgument(0) == ItemID.SHARK_LURE ? 300 : food.getOrDefault(call.getArgument(0), 0));
			when(inventory.getItems()).thenAnswer(call ->
			{
				ArrayList<Item> items = new ArrayList<>(); items.add(new Item(ItemID.SHARK_LURE, 300));
				for (Map.Entry<Integer, Integer> value : food.entrySet())
				{
					for (int quantity = 0; quantity < value.getValue(); quantity++) { items.add(new Item(value.getKey(), 1)); }
				}
				return items.toArray(new Item[0]);
			});
			ItemContainer worn = mock(ItemContainer.class); when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
			when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(1234, 1));
			when(worn.getItems()).thenReturn(new Item[]{new Item(1234, 1)});
			when(itemManager.getItemStats(1234)).thenReturn(new ItemStats(true, 0, 0, ItemEquipmentStats.builder().aspeed(3).build()));
			when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenReturn(0); when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1);
			when(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)).thenReturn(7);
			EnumComposition categories = mock(EnumComposition.class); when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
			when(categories.getIntValue(7)).thenReturn(100);
			EnumComposition styles = mock(EnumComposition.class); when(client.getEnum(100)).thenReturn(styles);
			when(styles.getIntVals()).thenReturn(new int[]{1001, 1001, 0, 1001});
			StructComposition style = mock(StructComposition.class); when(client.getStructComposition(1001)).thenReturn(style);
			when(style.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Ranging");
			ItemComposition composition = mock(ItemComposition.class); when(composition.getName()).thenReturn("Test item");
			when(itemManager.getItemComposition(anyInt())).thenReturn(composition);
			ClientThread clientThread = mock(ClientThread.class);
			doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(clientThread).invoke(any(Runnable.class));
			set("client", client); set("config", config); set("itemManager", itemManager); set("clientThread", clientThread);
			set("custom", new CustomActivity(config)); set("running", true); set("lastAccount", player.getName());
		}
		void seed() { acceptFishing(true); tick(); }
		void weaponSpeed(int speed)
		{
			// Accurate short-/longbow categories retain the public base speed.
			when(itemManager.getItemStats(1234)).thenReturn(new ItemStats(true, 0, 0, ItemEquipmentStats.builder().aspeed(speed).build()));
			when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(0);
			when(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)).thenReturn(3);
			when(client.getEnum(EnumID.WEAPON_STYLES).getIntValue(3)).thenReturn(100);
		}
		int fishingAnimation() { return rod ? AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT : AnimationID.HUMAN_HARPOON_CRYSTAL; }
		void tick() { plugin.onGameTick(new GameTick()); tick++; }
		void interact(Actor target)
		{
			when(player.getInteracting()).thenReturn(target); plugin.onInteractingChanged(new InteractingChanged(player, target));
		}
		void animation(int id)
		{
			when(player.getAnimation()).thenReturn(id); AnimationChanged event = new AnimationChanged(); event.setActor(player); plugin.onAnimationChanged(event);
		}
		void acceptFishing(boolean caught)
		{
			MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(MenuAction.NPC_SECOND_OPTION);
			when(entry.getOption()).thenReturn(rod ? "Fish" : "Harpoon"); when(entry.getTarget()).thenReturn("Fishing spot"); when(entry.getNpc()).thenReturn(spot);
			plugin.onMenuOptionClicked(new MenuOptionClicked(entry)); interact(spot); animation(fishingAnimation());
			chat(rod ? "You cast out your line." : "You start harpooning fish."); if (caught) { catchFish(); }
		}
		void catchFish()
		{
			chat(rod ? "You catch a leaping trout." : "You catch a shark!"); experience.merge(Skill.FISHING, rod ? 50 : 22, Integer::sum);
		}
		void chat(String text)
		{
			ChatMessage event = new ChatMessage(); event.setType(ChatMessageType.SPAM); event.setMessage(text); plugin.onChatMessage(event);
		}
		void flinch(boolean reverse)
		{
			animation(-1); Player attacker = mock(Player.class);
			Hitsplat hit = mock(Hitsplat.class); when(hit.getHitsplatType()).thenReturn(HitsplatID.BLOCK_ME); when(hit.getAmount()).thenReturn(0);
			HitsplatApplied event = new HitsplatApplied(); event.setActor(player); event.setHitsplat(hit);
			if (reverse) { interact(attacker); plugin.onHitsplatApplied(event); }
			else { plugin.onHitsplatApplied(event); interact(attacker); }
		}
		void production(boolean knife)
		{
			recipe(knife ? ItemID.KNIFE : ItemID.GUAM_LEAF, knife ? ItemID.TEAK_LOGS : ItemID.SWAMP_TAR); interact(null);
			animation(knife ? AnimationID.HUMAN_FLETCHING : AnimationID.HUMAN_SALAMANDER_TAR_GRIND);
		}
		void recipe(int source, int target)
		{
			Widget selected = mock(Widget.class); when(selected.getItemId()).thenReturn(source);
			when(client.isWidgetSelected()).thenReturn(true); when(client.getSelectedWidget()).thenReturn(selected);
			MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(MenuAction.WIDGET_TARGET_ON_WIDGET);
			when(entry.getOption()).thenReturn("Use"); when(entry.getItemId()).thenReturn(target);
			plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
		}
		void itemOp(String option, int item)
		{
			MenuEntry entry = mock(MenuEntry.class); when(entry.getType()).thenReturn(MenuAction.CC_OP);
			when(entry.getOption()).thenReturn(option); when(entry.isItemOp()).thenReturn(true); when(entry.getItemId()).thenReturn(item);
			plugin.onMenuOptionClicked(new MenuOptionClicked(entry));
		}
		void decrementFood(int item)
		{
			food.put(item, food.getOrDefault(item, 0) - 1); plugin.onItemContainerChanged(new ItemContainerChanged(InventoryID.INV, inventory));
		}
		void eat(int item, boolean input, boolean inventoryDelta, boolean inventoryFirst, int eatAnimation)
		{
			if (input) { itemOp("Eat", item); } interact(null);
			if (inventoryDelta && inventoryFirst) { decrementFood(item); }
			animation(eatAnimation); if (inventoryDelta && !inventoryFirst) { decrementFood(item); }
		}
		FishingSession session() throws Exception { return field(FishingSessions.class).current(); }
		ManipulatedFishingTracker model() throws Exception { return field(ManipulatedFishingTracker.class); }
		<T> T field(Class<T> type) throws Exception
		{
			for (Field field : AttemptTrackerPlugin.class.getDeclaredFields())
			{
				if (field.getType() == type) { field.setAccessible(true); return type.cast(field.get(plugin)); }
			}
			throw new AssertionError("Missing plugin state " + type);
		}
		void set(String name, Object value) throws Exception
		{
			Field field = AttemptTrackerPlugin.class.getDeclaredField(name); field.setAccessible(true); field.set(plugin, value);
		}
	}
}

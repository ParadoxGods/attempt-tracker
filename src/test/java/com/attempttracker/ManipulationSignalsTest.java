package com.attempttracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.attempttracker.core.ManipulatedFishingTracker.Family;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.StructComposition;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;

public class ManipulationSignalsTest
{
	@Test
	public void tarAndStockRecipesWorkInEitherItemOrder()
	{
		for (int herb : new int[]{ItemID.GUAM_LEAF, ItemID.MARENTILL, ItemID.TARROMIN, ItemID.HARRALANDER})
		{
			assertTrue(ManipulationSignals.productionPair(ItemID.SWAMP_TAR, herb));
			assertTrue(ManipulationSignals.productionPair(herb, ItemID.SWAMP_TAR));
			assertTrue(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_SALAMANDER_TAR_GRIND, herb, ItemID.SWAMP_TAR));
		}
		for (int log : new int[]{ItemID.TEAK_LOGS, ItemID.MAHOGANY_LOGS})
		{
			assertTrue(ManipulationSignals.productionPair(ItemID.KNIFE, log));
			assertTrue(ManipulationSignals.productionPair(log, ItemID.KNIFE));
			assertTrue(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_FLETCHING, log, ItemID.KNIFE));
			assertTrue(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_FLETCHING_SINGLE, ItemID.KNIFE, log));
		}
	}

	@Test
	public void unrelatedRecipesOrMismatchedAnimationCannotConfirmAReset()
	{
		assertFalse(ManipulationSignals.productionPair(ItemID.KNIFE, ItemID.LOGS));
		assertFalse(ManipulationSignals.productionPair(ItemID.SWAMP_TAR, ItemID.RANARR_WEED));
		assertFalse(ManipulationSignals.productionPair(ItemID.GUAM_LEAF, ItemID.TEAK_LOGS));
		assertFalse(ManipulationSignals.productionPair(-1, ItemID.SWAMP_TAR));
		assertFalse(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_FLETCHING, ItemID.GUAM_LEAF, ItemID.SWAMP_TAR));
		assertFalse(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_SALAMANDER_TAR_GRIND, ItemID.KNIFE, ItemID.TEAK_LOGS));
		assertFalse(ManipulationSignals.productionResetAnimation(AnimationID.XBOWS_FLETCHING_TEAK_STEEL, ItemID.KNIFE, ItemID.TEAK_LOGS));
		assertFalse(ManipulationSignals.productionResetAnimation(AnimationID.HUMAN_EAT));
		assertFalse(ManipulationSignals.productionResetAnimation(-1));
		assertFalse(ManipulationSignals.productionResetAnimation(99999));
	}

	@Test
	public void useDispatchCapturesSelectedItemAndTargetWithoutParsingText()
	{
		UseFixture fixture = new UseFixture();
		assertEquals(ItemID.KNIFE, ManipulationSignals.selectedUseItem(fixture.client, fixture.event));
		assertEquals(ItemID.TEAK_LOGS, ManipulationSignals.selectedUseTargetItem(fixture.event));
		assertTrue(ManipulationSignals.productionPair(ManipulationSignals.selectedUseItem(fixture.client, fixture.event),
			ManipulationSignals.selectedUseTargetItem(fixture.event)));
		when(fixture.entry.getType()).thenReturn(MenuAction.ITEM_USE_ON_ITEM);
		when(fixture.entry.getItemId()).thenReturn(-1);
		assertEquals(ItemID.TEAK_LOGS, ManipulationSignals.selectedUseTargetItem(fixture.event));
	}

	@Test
	public void consumedUnselectedSpellAndOtherMenusAreNotItemUseEvidence()
	{
		UseFixture fixture = new UseFixture();
		fixture.event.consume();
		assertEquals(-1, ManipulationSignals.selectedUseItem(fixture.client, fixture.event));
		assertEquals(-1, ManipulationSignals.selectedUseTargetItem(fixture.event));
		fixture = new UseFixture(); when(fixture.client.isWidgetSelected()).thenReturn(false);
		assertEquals(-1, ManipulationSignals.selectedUseItem(fixture.client, fixture.event));
		when(fixture.client.isWidgetSelected()).thenReturn(true); when(fixture.selected.getItemId()).thenReturn(-1);
		assertEquals(-1, ManipulationSignals.selectedUseItem(fixture.client, fixture.event));
		when(fixture.client.getSelectedWidget()).thenReturn(null);
		assertEquals(-1, ManipulationSignals.selectedUseItem(fixture.client, fixture.event));
		when(fixture.entry.getType()).thenReturn(MenuAction.WIDGET_TARGET_ON_NPC);
		assertEquals(-1, ManipulationSignals.selectedUseTargetItem(fixture.event));
		assertEquals(-1, ManipulationSignals.selectedUseItem(null, fixture.event));
		assertEquals(-1, ManipulationSignals.selectedUseTargetItem(null));
	}

	@Test
	public void confirmedSupportedFoodsHaveTheirActualDelays()
	{
		assertEquals(3, ManipulationSignals.foodDelay(ItemID.BRUT_ROE));
		assertEquals(3, ManipulationSignals.foodDelay(ItemID.BRUT_CAVIAR));
		assertEquals(2, ManipulationSignals.foodDelay(ItemID.TBWT_COOKED_KARAMBWAN));
		assertEquals(2, ManipulationSignals.foodDelay(ItemID.BLIGHTED_KARAMBWAN));
		for (int food : new int[]{ItemID.SHARK, ItemID.SWORDFISH, ItemID.SALMON, ItemID.TROUT, ItemID.LOBSTER,
			ItemID.TUNA, ItemID.ANGLERFISH, ItemID.MANTARAY, ItemID.SEATURTLE, ItemID.DARK_CRAB, ItemID.BREAD})
		{
			assertEquals(3, ManipulationSignals.foodDelay(food));
		}
	}

	@Test
	public void RawFishBurntFishAndUnknownItemsDoNotBecomeFoodEvidence()
	{
		for (int item : new int[]{ItemID.RAW_SHARK, ItemID.BURNT_SHARK, ItemID.TBWT_RAW_KARAMBWAN,
			ItemID.SWAMP_TAR, ItemID.KNIFE, -1, 0, 99999})
		{
			assertEquals(0, ManipulationSignals.foodDelay(item));
		}
	}

	@Test
	public void ActualRapidDartAttackResetsAtFullWeaponSpeed()
	{
		AttackFixture fixture = new AttackFixture();
		when(fixture.player.getAnimation()).thenReturn(AnimationID.II_HUMAN_DART_THROW);
		assertEquals(2, fixture.attackSpeed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(0);
		assertEquals(3, fixture.attackSpeed());
		when(fixture.itemManager.getItemStats(1234)).thenReturn(null);
		assertEquals(0, fixture.attackSpeed());
	}

	@Test
	public void DefenceConsumptionFishingAndUnknownAnimationsAreNeverAttacks()
	{
		AttackFixture fixture = new AttackFixture();
		for (int animation : new int[]{AnimationID.HUMAN_UNARMEDBLOCK, AnimationID.HUMAN_BOW,
			AnimationID.HUMAN_EAT, AnimationID.HUMAN_SALAMANDER_TAR_GRIND, AnimationID.HUMAN_HARPOON, -1, 99999})
		{
			when(fixture.player.getAnimation()).thenReturn(animation);
			if (animation == AnimationID.HUMAN_BOW) { assertEquals(2, fixture.attackSpeed()); }
			else { assertEquals(0, fixture.attackSpeed()); }
		}
		when(fixture.player.getAnimation()).thenReturn(AnimationID.II_HUMAN_DART_THROW);
		when(fixture.player.getInteracting()).thenReturn(null); assertEquals(0, fixture.attackSpeed());
		when(fixture.player.getInteracting()).thenReturn(fixture.player); assertEquals(0, fixture.attackSpeed());
	}

	@Test
	public void NpcAttackRequiresCombatDefinitionAndNeverFishingSpot()
	{
		AttackFixture fixture = new AttackFixture();
		NPC npc = mock(NPC.class); NPCComposition definition = mock(NPCComposition.class);
		when(npc.getId()).thenReturn(99999); when(npc.getTransformedComposition()).thenReturn(definition);
		when(fixture.player.getInteracting()).thenReturn(npc);
		when(fixture.player.getAnimation()).thenReturn(AnimationID.HUMAN_SWORD_SLASH);
		when(definition.getActions()).thenReturn(new String[]{null, "Attack"}); assertEquals(2, fixture.attackSpeed());
		when(definition.getActions()).thenReturn(new String[]{"Talk-to", null}); assertEquals(0, fixture.attackSpeed());
		when(definition.getActions()).thenReturn(new String[]{"Attack"});
		when(npc.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]); assertEquals(0, fixture.attackSpeed());
		when(npc.getId()).thenReturn(99999); when(npc.getTransformedComposition()).thenReturn(null); assertEquals(0, fixture.attackSpeed());
	}

	@Test
	public void HarpoonFamilyRequiresSpecificMethodEvidence()
	{
		NPC npc = mock(NPC.class);
		for (int id : new int[]{FishingSpot.SHARK.getIds()[0], FishingSpot.LOBSTER.getIds()[0], NpcID._0_40_34_MEMBERFISH})
		{
			when(npc.getId()).thenReturn(id);
			assertEquals(Family.HARPOON, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_HARPOON_CRYSTAL));
			assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_LOBSTER));
			assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISH_ONSPOT));
		}
	}

	@Test
	public void RodFamiliesNeedRodAnimationAndSupportedFishSpot()
	{
		NPC npc = mock(NPC.class);
		for (int id : new int[]{FishingSpot.SALMON.getIds()[0], FishingSpot.BARB_FISH.getIds()[0]})
		{
			when(npc.getId()).thenReturn(id);
			assertEquals(Family.ROD, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISH_ONSPOT));
			assertEquals(Family.ROD, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISHING_ONSPOT_BRUT));
			assertEquals(Family.ROD, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISH_ONSPOT_PEARL_BRUT));
			assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_HARPOON));
			assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, -1));
		}
	}

	@Test
	public void IndependentTimerFamiliesRemainExcludedEvenWithGenericRodAnimation()
	{
		NPC npc = mock(NPC.class);
		for (FishingSpot spot : FishingSpot.values())
		{
			if (spot == FishingSpot.SALMON || spot == FishingSpot.BARB_FISH) { continue; }
			when(npc.getId()).thenReturn(spot.getIds()[0]);
			assertEquals(spot.name(), Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISH_ONSPOT));
		}
		when(npc.getId()).thenReturn(99999);
		assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(npc, AnimationID.HUMAN_FISH_ONSPOT));
		assertEquals(Family.UNSUPPORTED, ManipulationSignals.fishingFamily(null, AnimationID.HUMAN_FISH_ONSPOT));
	}

	private static final class UseFixture
	{
		final Client client = mock(Client.class);
		final Widget selected = mock(Widget.class);
		final Widget target = mock(Widget.class);
		final MenuEntry entry = mock(MenuEntry.class);
		final MenuOptionClicked event = new MenuOptionClicked(entry);
		UseFixture()
		{
			when(client.isWidgetSelected()).thenReturn(true); when(client.getSelectedWidget()).thenReturn(selected);
			when(selected.getItemId()).thenReturn(ItemID.KNIFE); when(target.getItemId()).thenReturn(ItemID.TEAK_LOGS);
			when(entry.getType()).thenReturn(MenuAction.WIDGET_TARGET_ON_WIDGET);
			when(entry.getWidget()).thenReturn(target); when(entry.getItemId()).thenReturn(ItemID.TEAK_LOGS);
		}
	}

	private static final class AttackFixture
	{
		final Player player = mock(Player.class);
		final Client client = mock(Client.class);
		final ItemManager itemManager = mock(ItemManager.class);
		AttackFixture()
		{
			when(player.getInteracting()).thenReturn(mock(Player.class));
			ItemContainer worn = mock(ItemContainer.class); when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
			when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(1234, 1));
			when(itemManager.getItemStats(1234)).thenReturn(new ItemStats(true, 0, 0, ItemEquipmentStats.builder().aspeed(3).build()));
			when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1);
			when(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)).thenReturn(7);
			EnumComposition categories = mock(EnumComposition.class); when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
			when(categories.getIntValue(7)).thenReturn(100);
			EnumComposition styles = mock(EnumComposition.class); when(client.getEnum(100)).thenReturn(styles);
			when(styles.getIntVals()).thenReturn(new int[]{1001, 1001, 0, 1001});
			StructComposition style = mock(StructComposition.class); when(client.getStructComposition(1001)).thenReturn(style);
			when(style.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Ranging");
		}
		int attackSpeed() { return ManipulationSignals.outgoingAttackSpeed(player, client, itemManager); }
	}
}

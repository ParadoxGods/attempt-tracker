package com.attempttracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.ParamID;
import net.runelite.api.Player;
import net.runelite.api.StructComposition;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import org.junit.Test;

public class TwoTickFishingSignalsTest
{
	@Test
	public void combatBlockAndDamageTypesIncludeZeroButExcludeSpecialEffects()
	{
		int[] combatTypes = {HitsplatID.BLOCK_ME, HitsplatID.BLOCK_OTHER, HitsplatID.DAMAGE_ME, HitsplatID.DAMAGE_OTHER,
			HitsplatID.DAMAGE_ME_CYAN, HitsplatID.DAMAGE_OTHER_CYAN, HitsplatID.DAMAGE_ME_ORANGE, HitsplatID.DAMAGE_OTHER_ORANGE,
			HitsplatID.DAMAGE_ME_YELLOW, HitsplatID.DAMAGE_OTHER_YELLOW, HitsplatID.DAMAGE_ME_WHITE, HitsplatID.DAMAGE_OTHER_WHITE,
			HitsplatID.DAMAGE_ME_POISE, HitsplatID.DAMAGE_OTHER_POISE, HitsplatID.DAMAGE_MAX_ME, HitsplatID.DAMAGE_MAX_ME_CYAN,
			HitsplatID.DAMAGE_MAX_ME_ORANGE, HitsplatID.DAMAGE_MAX_ME_YELLOW, HitsplatID.DAMAGE_MAX_ME_WHITE, HitsplatID.DAMAGE_MAX_ME_POISE};
		Hitsplat hit = mock(Hitsplat.class);
		for (int type : combatTypes)
		{
			when(hit.getHitsplatType()).thenReturn(type);
			when(hit.getAmount()).thenReturn(0); assertTrue("Zero block/damage " + type, TwoTickFishingSignals.combatHitsplat(hit));
			when(hit.getAmount()).thenReturn(8); assertTrue("Positive damage " + type, TwoTickFishingSignals.combatHitsplat(hit));
		}
		int[] excluded = {HitsplatID.POISON, HitsplatID.VENOM, HitsplatID.DISEASE, HitsplatID.DISEASE_BLOCKED, HitsplatID.HEAL,
			HitsplatID.CYAN_UP, HitsplatID.CYAN_DOWN, HitsplatID.CORRUPTION, HitsplatID.PRAYER_DRAIN, HitsplatID.BLEED,
			HitsplatID.SANITY_DRAIN, HitsplatID.SANITY_RESTORE, HitsplatID.DOOM, HitsplatID.BURN, -1, 10000};
		for (int type : excluded)
		{
			when(hit.getHitsplatType()).thenReturn(type); assertFalse("Noncombat type " + type, TwoTickFishingSignals.combatHitsplat(hit));
		}
		when(hit.getHitsplatType()).thenReturn(HitsplatID.DAMAGE_OTHER); when(hit.getAmount()).thenReturn(-1);
		assertFalse(TwoTickFishingSignals.combatHitsplat(hit)); assertFalse(TwoTickFishingSignals.combatHitsplat(null));
	}

	@Test
	public void autoRetaliateRequiresEnabledValue()
	{
		Client client = mock(Client.class);
		when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenReturn(0); assertTrue(TwoTickFishingSignals.autoRetaliateEnabled(client));
		when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenReturn(1); assertFalse(TwoTickFishingSignals.autoRetaliateEnabled(client));
		when(client.getVarpValue(VarPlayerID.OPTION_NODEF)).thenReturn(-1); assertFalse(TwoTickFishingSignals.autoRetaliateEnabled(client));
		assertFalse(TwoTickFishingSignals.autoRetaliateEnabled(null));
	}

	@Test
	public void sameTileWalkOnlyRecognizesMatchingSceneAndWorldView()
	{
		WalkFixture fixture = new WalkFixture();
		assertTrue(fixture.sameTile());
		when(fixture.entry.getParam0()).thenReturn(51); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam0()).thenReturn(50); when(fixture.entry.getParam1()).thenReturn(61); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam1()).thenReturn(60); when(fixture.entry.getWorldViewId()).thenReturn(99); assertFalse(fixture.sameTile());
		when(fixture.entry.getWorldViewId()).thenReturn(-1); when(fixture.player.getWorldLocation()).thenReturn(new WorldPoint(3250, 3260, 1)); assertFalse(fixture.sameTile());
	}

	@Test
	public void sameTileWalkRejectsConsumedActionsAndOtherMenuTypes()
	{
		WalkFixture fixture = new WalkFixture(); fixture.event.consume(); assertFalse(fixture.sameTile());
		fixture = new WalkFixture(); when(fixture.entry.getType()).thenReturn(MenuAction.NPC_SECOND_OPTION); assertFalse(fixture.sameTile());
		when(fixture.entry.getType()).thenReturn(MenuAction.CANCEL); assertFalse(fixture.sameTile());
		assertFalse(TwoTickFishingSignals.sameTileWalk(null, fixture.event));
		assertFalse(TwoTickFishingSignals.sameTileWalk(fixture.player, null));
	}

	@Test
	public void sameTileWalkRequiresAvailableBoundedSceneCoordinates()
	{
		WalkFixture fixture = new WalkFixture();
		when(fixture.entry.getParam0()).thenReturn(-1); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam0()).thenReturn(104); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam0()).thenReturn(50); when(fixture.entry.getParam1()).thenReturn(-1); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam1()).thenReturn(104); assertFalse(fixture.sameTile());
		when(fixture.entry.getParam1()).thenReturn(60); when(fixture.player.getWorldLocation()).thenReturn(null); assertFalse(fixture.sameTile());
		when(fixture.player.getWorldLocation()).thenReturn(new WorldPoint(3250, 3260, 0));
		when(fixture.player.getWorldView()).thenReturn(null); assertFalse(fixture.sameTile());
	}

	@Test
	public void bowAccurateAndRapidResolveDifferentSpeedsFromSameTrainingName()
	{
		EquipmentFixture fixture = new EquipmentFixture(4, 3, 0, "Ranging");
		assertEquals(4, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1); assertEquals(3, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(3);
		when(fixture.style.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn("Longrange"); assertEquals(4, fixture.speed());
	}

	@Test
	public void dartsSupportAccurateThreeAndRapidTwoWithoutInventingOtherStyleSpeeds()
	{
		EquipmentFixture fixture = new EquipmentFixture(3, 7, 0, "Ranging");
		assertEquals(3, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1); assertEquals(2, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(2); assertEquals(0, fixture.speed());
	}

	@Test
	public void rangedStyleOnSalamanderOrUnknownCategoryIsNotAssumedRapid()
	{
		assertEquals(0, new EquipmentFixture(4, 6, 1, "Ranging").speed());
		assertEquals(0, new EquipmentFixture(4, 999, 1, "Ranging").speed());
		assertEquals(0, new EquipmentFixture(4, 3, 1, "Rapid").speed());
		assertEquals(3, new EquipmentFixture(4, 19, 1, "Ranging").speed());
		assertEquals(5, new EquipmentFixture(6, 5, 1, "Ranging").speed());
	}

	@Test
	public void validMeleeStyleUsesBaseSpeedWhileCastingAndMissingDataFailClosed()
	{
		assertEquals(3, new EquipmentFixture(3, 1, 0, "Accurate").speed());
		assertEquals(4, new EquipmentFixture(4, 1, 1, "Aggressive").speed());
		assertEquals(0, new EquipmentFixture(3, 23, 0, "Casting").speed());
		assertEquals(0, new EquipmentFixture(3, 23, 0, "Other").speed());
		EquipmentFixture fixture = new EquipmentFixture(3, 7, 0, "Ranging");
		when(fixture.itemManager.getItemStats(fixture.weaponId)).thenReturn(null); assertEquals(0, fixture.speed());
		when(fixture.itemManager.getItemStats(fixture.weaponId)).thenReturn(new ItemStats(true, 0, 0, null)); assertEquals(0, fixture.speed());
		assertEquals(0, new EquipmentFixture(0, 7, 0, "Ranging").speed());
	}

	@Test
	public void missingAndOutOfRangeStylesNeverDefaultToRapid()
	{
		EquipmentFixture fixture = new EquipmentFixture(4, 3, 1, "Ranging");
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(-1); assertEquals(0, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(9); assertEquals(0, fixture.speed());
		when(fixture.client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(1);
		when(fixture.style.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(null); assertEquals(0, fixture.speed());
		when(fixture.client.getStructComposition(1001)).thenReturn(null); assertEquals(0, fixture.speed());
		when(fixture.client.getEnum(100)).thenReturn(null); assertEquals(0, fixture.speed());
		when(fixture.client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(null); assertEquals(0, fixture.speed());
	}

	@Test
	public void emptyHandsAreFourTicksAndUnavailableEquipmentIsUnknown()
	{
		EquipmentFixture fixture = new EquipmentFixture(4, 1, 0, "Accurate");
		when(fixture.worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(null); assertEquals(4, fixture.speed());
		when(fixture.worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(-1, 0)); assertEquals(4, fixture.speed());
		when(fixture.client.getItemContainer(InventoryID.WORN)).thenReturn(null); assertEquals(0, fixture.speed());
		assertEquals(0, TwoTickFishingSignals.effectiveWeaponSpeed(null, fixture.itemManager));
		assertEquals(0, TwoTickFishingSignals.effectiveWeaponSpeed(fixture.client, null));
	}

	@Test
	public void supportedSpotIsIndependentOfIdleFlinchAnimationButHarpoonChecksMethod()
	{
		FishingFixture fixture = new FishingFixture();
		for (int id : FishingSpot.SHARK.getIds()) { when(fixture.spot.getId()).thenReturn(id); assertTrue(fixture.harpoon()); }
		for (int id : FishingSpot.LOBSTER.getIds()) { when(fixture.spot.getId()).thenReturn(id); assertTrue(fixture.harpoon()); }
		when(fixture.spot.getId()).thenReturn(NpcID._0_40_34_MEMBERFISH); assertTrue(fixture.harpoon());
		when(fixture.player.getAnimation()).thenReturn(-1); assertFalse(fixture.harpoon()); assertTrue(TwoTickFishingSignals.supportedSpot(fixture.spot));
		when(fixture.player.getAnimation()).thenReturn(AnimationID.HUMAN_LOBSTER); assertFalse(fixture.harpoon());
		assertFalse(TwoTickFishingSignals.supportedSpot(null));
	}

	@Test
	public void unsupportedTimerFamiliesAndUnknownHarpoonSpotsStayExcluded()
	{
		FishingFixture fixture = new FishingFixture();
		for (FishingSpot method : FishingSpot.values())
		{
			if (method == FishingSpot.SHARK || method == FishingSpot.LOBSTER) { continue; }
			for (int id : method.getIds())
			{
				when(fixture.spot.getId()).thenReturn(id); assertFalse(method.name(), fixture.harpoon());
			}
		}
		when(fixture.spot.getId()).thenReturn(99999); assertFalse(fixture.harpoon());
	}

	@Test
	public void positionPlaneAndWorldViewMustAgree()
	{
		FishingFixture fixture = new FishingFixture();
		assertTrue(fixture.harpoon());
		when(fixture.player.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 1)); assertFalse(fixture.harpoon());
		when(fixture.player.getWorldLocation()).thenReturn(new WorldPoint(98, 100, 0)); assertFalse(fixture.harpoon());
		when(fixture.player.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
		when(fixture.spot.getWorldView()).thenReturn(mock(WorldView.class)); assertFalse(fixture.harpoon());
		when(fixture.spot.getWorldView()).thenReturn(fixture.worldView); when(fixture.player.getWorldLocation()).thenReturn(null); assertFalse(fixture.harpoon());
		when(fixture.player.getWorldView()).thenReturn(null); when(fixture.spot.getWorldView()).thenReturn(null); assertFalse(fixture.harpoon());
		assertFalse(TwoTickFishingSignals.supportedHarpoon(null, fixture.spot));
		assertFalse(TwoTickFishingSignals.supportedHarpoon(fixture.player, null));
	}

	private static final class EquipmentFixture
	{
		final Client client = mock(Client.class);
		final ItemManager itemManager = mock(ItemManager.class);
		final ItemContainer worn = mock(ItemContainer.class);
		final StructComposition style = mock(StructComposition.class);
		final int weaponId = 1234;
		EquipmentFixture(int speed, int category, int styleIndex, String trainingStyle)
		{
			when(client.getItemContainer(InventoryID.WORN)).thenReturn(worn);
			when(worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx())).thenReturn(new Item(weaponId, 1));
			when(itemManager.getItemStats(weaponId)).thenReturn(new ItemStats(true, 0, 0, ItemEquipmentStats.builder().aspeed(speed).build()));
			when(client.getVarpValue(VarPlayerID.COM_MODE)).thenReturn(styleIndex);
			when(client.getVarbitValue(VarbitID.COMBAT_WEAPON_CATEGORY)).thenReturn(category);
			EnumComposition categories = mock(EnumComposition.class); when(client.getEnum(EnumID.WEAPON_STYLES)).thenReturn(categories);
			when(categories.getIntValue(category)).thenReturn(100);
			EnumComposition styles = mock(EnumComposition.class); when(client.getEnum(100)).thenReturn(styles);
			when(styles.getIntVals()).thenReturn(new int[]{1001, 1001, 0, 1001});
			when(client.getStructComposition(1001)).thenReturn(style);
			when(style.getStringValue(ParamID.ATTACK_STYLE_NAME)).thenReturn(trainingStyle);
		}
		int speed() { return TwoTickFishingSignals.effectiveWeaponSpeed(client, itemManager); }
	}

	private static final class FishingFixture
	{
		final Player player = mock(Player.class);
		final NPC spot = mock(NPC.class);
		final WorldView worldView = mock(WorldView.class);
		FishingFixture()
		{
			when(player.getAnimation()).thenReturn(AnimationID.HUMAN_HARPOON_CRYSTAL);
			when(spot.getId()).thenReturn(FishingSpot.SHARK.getIds()[0]);
			when(player.getWorldView()).thenReturn(worldView); when(spot.getWorldView()).thenReturn(worldView);
			when(player.getWorldLocation()).thenReturn(new WorldPoint(100, 100, 0));
			when(spot.getWorldLocation()).thenReturn(new WorldPoint(101, 100, 0));
		}
		boolean harpoon() { return TwoTickFishingSignals.supportedHarpoon(player, spot); }
	}

	private static final class WalkFixture
	{
		final Player player = mock(Player.class);
		final WorldView worldView = mock(WorldView.class);
		final MenuEntry entry = mock(MenuEntry.class);
		final MenuOptionClicked event = new MenuOptionClicked(entry);
		WalkFixture()
		{
			when(player.getWorldView()).thenReturn(worldView);
			when(player.getWorldLocation()).thenReturn(new WorldPoint(3250, 3260, 0));
			when(worldView.getId()).thenReturn(-1); when(worldView.getPlane()).thenReturn(0);
			when(worldView.getBaseX()).thenReturn(3200); when(worldView.getBaseY()).thenReturn(3200);
			when(worldView.getSizeX()).thenReturn(104); when(worldView.getSizeY()).thenReturn(104);
			when(entry.getType()).thenReturn(MenuAction.WALK); when(entry.getWorldViewId()).thenReturn(-1);
			when(entry.getParam0()).thenReturn(50); when(entry.getParam1()).thenReturn(60);
		}
		boolean sameTile() { return TwoTickFishingSignals.sameTileWalk(player, event); }
	}
}

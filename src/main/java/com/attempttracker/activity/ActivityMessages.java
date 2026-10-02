package com.attempttracker.activity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Conservative, client-independent parser for action-result game messages.
 * The caller must filter to RuneLite SPAM/GAMEMESSAGE events; player chat is not
 * evidence that an action occurred. Each parsed message represents one result,
 * never the number of items produced by that result.
 *
 * <p>Message families and fixtures are checked against these primary sources:</p>
 * <ul>
 * <li>runelite/runelite: FishingPlugin and FishingPluginTest</li>
 * <li>runelite/runelite: CookingPlugin and CookingPluginTest</li>
 * <li>runelite/runelite: WoodcuttingPlugin and WoodcuttingPluginTest</li>
 * <li>Skretzo/runelite-plugins, chat-success-rates branch: MiningRock,
 * Pickpocketing and ChatSuccessRatesAction</li>
 * <li>pajlads/runelite-pickpocket-helper: utility/MessagePattern</li>
 * </ul>
 *
 * <p>Starts, bonus-item notifications, inventory-full notices, cooking
 * conversions and unsupported item names deliberately return empty.</p>
 */
public final class ActivityMessages
{
	private static final Pattern FORMATTING = Pattern.compile("<[^>]*>|@[a-z0-9_]+@", Pattern.CASE_INSENSITIVE);
	private static final Pattern WHITESPACE = Pattern.compile("\\s+");
	// Live shark catches use "!"; RuneLite's fishing fixtures also include ".".
	private static final Pattern FISHING = Pattern.compile("you catch (?:a|an|some) ([a-z][a-z -]*)[.!]");
	// RuneLite's official fixture is "You catch 15 Karambwanji.". A single
	// message is one catch result, even when that result awards many bait fish.
	private static final Pattern KARAMBWANJI = Pattern.compile("you catch [1-9][0-9]* karambwanji[.!]");
	private static final Pattern COOKING_SUCCESS = Pattern.compile(
		"you (?:(?:successfully (?:cook|bake|fry))|(?:manage to cook)|cook|roast) "
			+ "(?:a|an|some|the) ([a-z][a-z -]*)\\.");
	private static final Pattern COOKING_FAILURE = Pattern.compile(
		"you accidentally burn (?:a|an|some|the) ([a-z][a-z -]*)\\.");
	private static final Pattern PICKPOCKET_SUCCESS = Pattern.compile(
		"you pick (?:the )?[a-z][a-z '-]* pocket\\.");
	private static final Pattern PICKPOCKET_FAILURE = Pattern.compile(
		"you fail to pick (?:the )?[a-z][a-z '-]* pocket\\.");
	private static final Pattern MINING = Pattern.compile("you manage to mine some ([a-z][a-z ]*)\\.");
	private static final Pattern WOODCUTTING = Pattern.compile("you get (?:some|an) ([a-z][a-z ]*)\\.");

	private static final Map<String, String> FISH = fishNames();
	private static final Map<String, String> FOOD = foodNames();
	private static final Map<String, String> ORE = oreNames();
	private static final Map<String, String> WOOD = woodNames();

	private ActivityMessages()
	{
	}

	public static Optional<ActivityOutcome> parse(String message)
	{
		if (message == null || message.isEmpty())
		{
			return Optional.empty();
		}
		String text = normalize(message);
		Optional<ActivityOutcome> result = named(FISHING, text, FISH, "Fishing", "FISHING", true, true);
		if (result.isPresent())
		{
			return result;
		}
		if (KARAMBWANJI.matcher(text).matches())
		{
			return outcome("Fishing: Karambwanji", "FISHING", true, true);
		}
		result = named(COOKING_SUCCESS, text, FOOD, "Cooking", "COOKING", true, false);
		if (result.isPresent())
		{
			return result;
		}
		// Explicit special messages used by RuneLite's core cooking plugin.
		if (text.equals("you cook the karambwan. it looks delicious."))
		{
			return outcome("Cooking: Karambwan", "COOKING", true, false);
		}
		if (text.equals("you burn the mushroom in the fire."))
		{
			return outcome("Cooking: Mushroom", "COOKING", false, false);
		}
		result = named(COOKING_FAILURE, text, FOOD, "Cooking", "COOKING", false, false);
		if (result.isPresent())
		{
			return result;
		}
		if (PICKPOCKET_SUCCESS.matcher(text).matches())
		{
			return outcome("Pickpocketing", "THIEVING", true, false);
		}
		if (PICKPOCKET_FAILURE.matcher(text).matches())
		{
			return outcome("Pickpocketing", "THIEVING", false, false);
		}
		result = named(MINING, text, ORE, "Mining", "MINING", true, true);
		if (result.isPresent())
		{
			return result;
		}
		return named(WOODCUTTING, text, WOOD, "Woodcutting", "WOODCUTTING", true, true);
	}

	private static Optional<ActivityOutcome> named(Pattern pattern, String message,
		Map<String, String> names, String activity, String skill, boolean success, boolean timed)
	{
		Matcher matcher = pattern.matcher(message);
		if (!matcher.matches())
		{
			return Optional.empty();
		}
		String name = names.get(matcher.group(1));
		return name == null ? Optional.empty() : outcome(activity + ": " + name, skill, success, timed);
	}

	private static Optional<ActivityOutcome> outcome(String activity, String skill, boolean success, boolean timed)
	{
		return Optional.of(new ActivityOutcome(activity, skill, success, timed));
	}

	private static String normalize(String message)
	{
		return WHITESPACE.matcher(FORMATTING.matcher(message).replaceAll("")
			.replace('\u00a0', ' ').replace('\u2019', '\''))
			.replaceAll(" ").trim().toLowerCase(Locale.ROOT);
	}

	private static Map<String, String> fishNames()
	{
		Map<String, String> names = new LinkedHashMap<>();
		add(names, "Shrimp", "shrimp", "shrimps");
		add(names, "Anchovies", "anchovies");
		add(names, "Sardine", "sardine", "sardines");
		add(names, "Herring", "herring");
		add(names, "Trout", "trout");
		add(names, "Pike", "pike");
		add(names, "Salmon", "salmon");
		add(names, "Tuna", "tuna");
		add(names, "Lobster", "lobster");
		add(names, "Bass", "bass");
		add(names, "Swordfish", "swordfish");
		add(names, "Shark", "shark");
		add(names, "Monkfish", "monkfish");
		add(names, "Anglerfish", "anglerfish");
		add(names, "Dark crab", "dark crab", "dark crabs");
		add(names, "Karambwan", "karambwan");
		add(names, "Karambwanji", "karambwanji");
		add(names, "Lava eel", "lava eel");
		add(names, "Cave eel", "cave eel");
		add(names, "Slimy eel", "slimy eel");
		add(names, "Sacred eel", "sacred eel");
		add(names, "Infernal eel", "infernal eel");
		add(names, "Leaping trout", "leaping trout");
		add(names, "Leaping salmon", "leaping salmon");
		add(names, "Leaping sturgeon", "leaping sturgeon");
		add(names, "Minnow", "minnow", "minnows");
		add(names, "Harpoonfish", "harpoonfish");
		add(names, "Guppy", "guppy", "guppies");
		add(names, "Cavefish", "cavefish");
		add(names, "Tetra", "tetra");
		add(names, "Catfish", "catfish");
		add(names, "Squid", "squid");
		add(names, "Jumbo squid", "jumbo squid");
		return Collections.unmodifiableMap(names);
	}

	private static Map<String, String> foodNames()
	{
		Map<String, String> names = new LinkedHashMap<>(FISH);
		// These gatherable fish do not use the ordinary cook/burn mechanic.
		for (String unsupported : new String[]{"karambwanji", "sacred eel", "infernal eel",
			"leaping trout", "leaping salmon", "leaping sturgeon", "minnow", "minnows",
			"guppy", "guppies", "cavefish", "tetra", "catfish"})
		{
			names.remove(unsupported);
		}
		add(names, "Mushroom", "mushroom", "mushrooms");
		add(names, "Meat", "meat", "piece of meat");
		add(names, "Chicken", "chicken");
		add(names, "Bread", "bread");
		// Burn messages can identify only "the pie", even when a successful
		// bake names the flavor. Keep both outcomes in one explicitly aggregate
		// profile; counting named successes without their generic failures
		// would systematically inflate the measured success rate.
		add(names, "Pie", "pie", "garden pie", "tasty garden pie", "apple pie",
			"redberry pie", "meat pie", "fish pie", "admiral pie", "wild pie", "summer pie");
		return Collections.unmodifiableMap(names);
	}

	private static Map<String, String> oreNames()
	{
		Map<String, String> names = new LinkedHashMap<>();
		for (String ore : new String[]{"copper", "tin", "iron", "silver", "gold", "mithril",
			"adamantite", "runite", "blurite"})
		{
			String label = Character.toUpperCase(ore.charAt(0)) + ore.substring(1) + " ore";
			add(names, label, ore, ore + " ore");
		}
		add(names, "Coal", "coal");
		add(names, "Clay", "clay");
		return Collections.unmodifiableMap(names);
	}

	private static Map<String, String> woodNames()
	{
		Map<String, String> names = new LinkedHashMap<>();
		add(names, "Logs", "logs", "log");
		for (String wood : new String[]{"oak", "willow", "teak", "maple", "mahogany", "yew",
			"magic", "redwood", "arctic", "pine", "juniper"})
		{
			String label = Character.toUpperCase(wood.charAt(0)) + wood.substring(1) + " logs";
			add(names, label, wood + " logs", wood + " log");
		}
		add(names, "Arctic logs", "arctic logs", "arctic log");
		add(names, "Mushrooms", "mushrooms");
		return Collections.unmodifiableMap(names);
	}

	private static void add(Map<String, String> names, String label, String... aliases)
	{
		for (String alias : aliases)
		{
			names.put(alias, label);
		}
	}
}

/*
 * Copyright (c) 2024, TheStonedTurtle <https://github.com/TheStonedTurtle>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package thestonedturtle.unbalancedtradeprevention;

import com.google.common.collect.Sets;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class UnbalancedTradeTest
{
	@Mock
	UnbalancedTradePreventionConfig config;

	@Spy
	@InjectMocks
	UnbalancedTradePreventionPlugin plugin;

	@ParameterizedTest
	@MethodSource("providerForTradeWindowDelta")
	void testGetTradeWindowDelta(String selfValue, String oppValue, int expectedDelta)
	{
		doReturn(createSelfValueTextPattern(selfValue))
			.when(plugin).getTextByWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID);
		doReturn(createOpponentValueTextPattern(oppValue))
			.when(plugin).getTextByWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID);

		int delta = plugin.getTradeWindowDelta();
		assertEquals(expectedDelta, delta);
	}

	private static Stream<Arguments> providerForTradeWindowDelta()
	{
		return Stream.of(
			// Value should be positive if you're giving away money and negative if you're receiving money
			Arguments.of("100 coins", "0 coins", 100),
			Arguments.of("0 coins", "100 coins", -100),
			Arguments.of("100 coins", "100 coins", 0),
			// If any value is null then the trade should be as unfavorable as possible
			Arguments.of("100 coins", null, Integer.MAX_VALUE),
			Arguments.of(null, "100 coins", Integer.MAX_VALUE),
			Arguments.of(null, null, Integer.MAX_VALUE),
			// If the user's value is `Lots` the trade should be as unfavorable as possible
			Arguments.of("Lots!", "Lots!", Integer.MAX_VALUE),
			Arguments.of("Lots!", "100 coins", Integer.MAX_VALUE),
			// If either value doesn't match the pattern then trade should be as unfavorable as possible
			Arguments.of("DOES NOT MATCH", "DOES NOT MATCH", Integer.MAX_VALUE),
			Arguments.of("DOES NOT MATCH", "0 coins", Integer.MAX_VALUE),
			Arguments.of("0 coins", "DOES NOT MATCH", Integer.MAX_VALUE),
			// If the opponents value is lots their value should be set to max value
			Arguments.of("0 coins", "Lots!", Integer.MAX_VALUE * -1),
			Arguments.of("100 coins", "Lots!", (100 - Integer.MAX_VALUE)),
			// Ensure values with commas work as expected
			Arguments.of("100,000 coins", "0 coins", 100_000),
			Arguments.of("100,000,000 coins", "0 coins", 100_000_000),
			Arguments.of("1,000,000,000 coins", "0 coins", 1_000_000_000),
			Arguments.of("0 coins", "100,000 coins", -100_000),
			Arguments.of("0 coins", "100,000,000 coins", -100_000_000),
			Arguments.of("0 coins", "1,000,000,000 coins", -1_000_000_000),
			Arguments.of("100,000 coins", "200,000 coins", -100_000),
			// parseException when trying to convert string to number
			Arguments.of(", coins", "0 coins", Integer.MAX_VALUE),
			Arguments.of("0 coins", ", coins", Integer.MAX_VALUE),
			Arguments.of(", coins", ", coins", Integer.MAX_VALUE)
		);
	}

	@ParameterizedTest
	@MethodSource("providerForItemFilters")
	void testUnbalancedTradeByItemFilters(ItemFilterType itemFilterType, Set<String> filterNames, Set<String> wildcardNames, Set<String> opponentItems, boolean expectedBoolean)
	{
		doReturn(itemFilterType).when(config).filterType();

		// Since we mutate the opponentItems we need recreate the set
		doReturn(lowercaseAllSetValues(opponentItems)).when(plugin).getOpponentItemNames();
		doReturn(lowercaseAllSetValues(filterNames)).when(plugin).getFilterItemNames();
		doReturn(lowercaseAllSetValues(wildcardNames)).when(plugin).getFilterWildcardNames();

		boolean isUnbalanced = plugin.unbalancedTradeByItemFilters();
		assertEquals(expectedBoolean, isUnbalanced);
	}

	public static Stream<Arguments> providerForItemFilters()
	{
		final Set<String> EMPTY = new HashSet<>();
		// false = Balanced trade
		// true = Unbalanced trade
		// Only the opponentItems set needs to be mutable, so use Sets.newHashSet instead
		return Stream.of(
			// If the item filter is off then it should always be balanced
			Arguments.of(ItemFilterType.OFF, EMPTY, EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.OFF, Set.of("ITEM NAME"), EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.OFF, EMPTY, EMPTY, Set.of("ITEM NAME"), false),
			Arguments.of(ItemFilterType.OFF, Set.of("ITEM NAME"), EMPTY, Sets.newHashSet("ITEM NAME"), false),
			Arguments.of(ItemFilterType.OFF, Set.of("ITEM NAME"), EMPTY, Sets.newHashSet("ITEM NAME"), false),
			// WHITELIST - unbalanced if there's any item in the opponents trade that IS NOT specified in one of our lists
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("ITEM NAME"), EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, EMPTY, Sets.newHashSet("ITEM NAME"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("ITEM NAME"), EMPTY, Sets.newHashSet("ITEM NAME"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("OTHER ITEM"), EMPTY, Sets.newHashSet("ITEM NAME"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("ITEM NAME", "OTHER ITEM"), EMPTY, Sets.newHashSet("ITEM NAME"), false),
			// BLACKLIST - unbalanced if there's any item in the opponents trade that IS specified in one of our lists
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("ITEM NAME"), EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, EMPTY, Sets.newHashSet("ITEM NAME"), false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("ITEM NAME"), EMPTY, Sets.newHashSet("ITEM NAME"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("OTHER ITEM"), EMPTY, Sets.newHashSet("ITEM NAME"), false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("ITEM NAME", "OTHER ITEM"), EMPTY, Sets.newHashSet("ITEM NAME"), true),
			// Coins and Platinum Tokens should always be allowed regardless of the settings
			Arguments.of(ItemFilterType.OFF, EMPTY, EMPTY, Sets.newHashSet("Coins", "Platinum token"), false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, EMPTY, Sets.newHashSet("Coins", "Platinum token"), false),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, EMPTY, Sets.newHashSet("Coins", "Platinum token"), false),
			// Even if the blacklist contains them they should still be allowed
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Coins"), EMPTY, Sets.newHashSet("Coins", "Platinum token"), false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Platinum token"), EMPTY, Sets.newHashSet("Coins", "Platinum token"), false),

			// Wildcards
			// trailing wildcard
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item name"), false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item name", "Item with another name"), false), // Matches multiple values
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item "), false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item"), true), // missing space so shouldn't match
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item Name", "Item"), true), // one matches one doesn't
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item name"), true),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item "), true),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item"), false), // missing space so shouldn't match
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("Item *"), Sets.newHashSet("Item Name", "Item"), true), // one matches one doesn't
			// leading wildcard
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Named Item"), false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Named Item", "Other Named Item"), false), // Matches multiple values
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("* Item"), Sets.newHashSet(" Item"), false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Item"), true), // missing space so shouldn't match
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Named Item", "Item"), true), // one matches one doesn't
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Named Item"), true),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("* Item"), Sets.newHashSet(" Item"), true),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Item"), false), // missing space so shouldn't match
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of("* Item"), Sets.newHashSet("Named Item", "Item"), true), // one matches one doesn't

			// Combination of Wildcards and non wildcards - WHITELIST
			// non-wildcard matches
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Swordfish"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Swordfish", "Lobster"), true),
			// wildcard matches
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item Name", "Lobster"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Lobster"), true),
			// both matches
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name", "Swordfish"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name", "Swordfish", "Lobster"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Swordfish"), false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Swordfish", "Lobster"), true),
			// neither matches
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Lobster"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Lobster"), true),
			Arguments.of(ItemFilterType.WHITELIST, Set.of("Swordfish"), Set.of("Item *"), EMPTY, false),

			// Combination of Wildcards and non wildcards - BLACKLIST
			// non-wildcard matches
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Swordfish"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Swordfish", "Lobster"), true),
			// wildcard matches
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item Name", "Lobster"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Lobster"), true),
			// both matches
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name", "Swordfish"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Item name", "Swordfish", "Lobster"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Swordfish"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Named Item", "Swordfish", "Lobster"), true),
			// neither matches
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), Sets.newHashSet("Lobster"), false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("* Item"), Sets.newHashSet("Lobster"), false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of("Swordfish"), Set.of("Item *"), EMPTY, false),

			// If there was an error getting the opponents items return true if we're meant to filter by them
			Arguments.of(ItemFilterType.OFF, EMPTY, EMPTY, null, false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, EMPTY, null, true),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, EMPTY, null, true),

			// Ignore strings that are just spaces
			Arguments.of(ItemFilterType.WHITELIST, Set.of(" ", "      "), EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.WHITELIST, Set.of(" ", "      "), EMPTY, Sets.newHashSet("Some Item"), true),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of(" ", "      "), EMPTY, false),
			Arguments.of(ItemFilterType.WHITELIST, EMPTY, Set.of(" ", "      "), Sets.newHashSet("Some Item"), true),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of(" ", "      "), EMPTY, EMPTY, false),
			Arguments.of(ItemFilterType.BLACKLIST, Set.of(" ", "      "), EMPTY, Sets.newHashSet("Some Item"), false),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of(" ", "      "), EMPTY, false),
			Arguments.of(ItemFilterType.BLACKLIST, EMPTY, Set.of(" ", "      "), Sets.newHashSet("Some Item"), false)
		);
	}

	private static String createSelfValueTextPattern(String s)
	{
		return s == null ? null : String.format("You are about to give:(Value: %s)", s);
	}

	private static String createOpponentValueTextPattern(String s)
	{
		return s == null ? null : String.format("In return you will receive:(Value: %s)", s);
	}

	private static Set<String> lowercaseAllSetValues(Set<String> set)
	{
		if (set == null)
		{
			return null;
		}

		return set.stream().map(String::toLowerCase).collect(Collectors.toSet());
	}
}

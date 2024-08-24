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

import java.util.stream.Stream;
import net.runelite.api.Client;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import static org.mockito.Mockito.doReturn;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID;
import static thestonedturtle.unbalancedtradeprevention.UnbalancedTradePreventionPlugin.TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID;

@ExtendWith(MockitoExtension.class)
public class UnbalancedTradeTest
{

	@Mock
	Client client;

	@Mock
	UnbalancedTradePreventionConfig config;

	@Spy
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
			Arguments.of("100 coins", "100 coins", 0)
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
}

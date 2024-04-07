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

import com.google.inject.Provides;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Unbalanced Trade Prevention"
)
public class UnbalancedTradePreventionPlugin extends Plugin
{
	private static final int TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID = 334;
	private static final int TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID = 23;
	private static final int TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID = 24;

	private static final Pattern SELF_VALUE_PATTERN = Pattern.compile("You are about to give:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final Pattern OPPONENT_VALUE_PATTERN = Pattern.compile("In return you will receive:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final NumberFormat VALUE_FORMAT = NumberFormat.getNumberInstance(java.util.Locale.UK);

	private static final String UNBALANCED_TRADE_CHAT_MESSAGE = "<col=ff0000>Unbalanced trade detected! The accept trade option has been set to right-click only.</col>";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private UnbalancedTradePreventionConfig config;

	@Provides
	UnbalancedTradePreventionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnbalancedTradePreventionConfig.class);
	}

	private boolean unbalancedTradeDetected = false;

	@Override
	protected void startUp()
	{
		if (!client.getGameState().equals(GameState.LOGGED_IN))
		{
			return;
		}

		if (client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID) != null)
		{
			checkTradeWindow();
		}
	}

	@Override
	protected void shutDown()
	{
		unbalancedTradeDetected = false;
	}

	private int parseWidgetForValue(Widget w, Pattern p)
	{
		Matcher m = p.matcher(Text.removeTags(w.getText()));
		if (!m.matches())
		{
			return -1;
		}

		String matchedText = m.group(1);
		if (matchedText.equals("Lots!"))
		{
			return Integer.MAX_VALUE;
		}

		matchedText = matchedText.replace(" coins", "");

		try
		{
			return VALUE_FORMAT.parse(matchedText).intValue();
		}
		catch (ParseException e)
		{
			return -1;
		}
	}

	/**
	 * Calculates the difference between your trade value and your opponents trade value
	 * @return the difference between your trades. Returns `Integer.MAX_VALUE` if it can not find the values or if your value is `Lots!`
	 */
	private int getTradeWindowDelta()
	{
		Widget selfValueWidget = client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID);
		Widget opponentValueWidget = client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID);
		if (selfValueWidget == null || opponentValueWidget == null)
		{
			return Integer.MAX_VALUE;
		}

		int selfValue = parseWidgetForValue(selfValueWidget, SELF_VALUE_PATTERN);
		int opponentValue = parseWidgetForValue(opponentValueWidget, OPPONENT_VALUE_PATTERN);

		// If there was an error getting our own value, or it equals "Lots!" (or max cash), assume the trade is in their favor
		if (selfValue == -1 || selfValue == Integer.MAX_VALUE)
		{
			return Integer.MAX_VALUE;
		}

		return selfValue - opponentValue;
	}

	private void checkTradeWindow()
	{
		int delta = getTradeWindowDelta();
		unbalancedTradeDetected = delta >= config.valueThreshold();
		if (unbalancedTradeDetected)
		{
			sendChatMessage();
		}
	}

	private void sendChatMessage()
	{
		if (!client.isClientThread())
		{
			clientThread.invoke(this::sendChatMessage);
			return;
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", UNBALANCED_TRADE_CHAT_MESSAGE, null);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() != TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID)
		{
			return;
		}

		checkTradeWindow();
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID)
		{
			unbalancedTradeDetected = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(UnbalancedTradePreventionConfig.GROUP_NAME))
		{
			return;
		}

		checkTradeWindow();
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		// The menu is not rebuilt when it is open so no need to swap
		if (!unbalancedTradeDetected || client.isMenuOpen())
		{
			return;
		}

		MenuEntry[] menuEntries = client.getMenuEntries();
		for (int i = 0; i < menuEntries.length; i++)
		{
			MenuEntry entry = menuEntries[i];
			String option = Text.removeTags(entry.getOption()).toLowerCase();
			if (option.equals("accept"))
			{
				// the `cancel` option should always exist so there should always be at least 2 entries in this array
				assert menuEntries.length > 1;

				// swap to the bottom of the list to prevent it from being the left-click option
				simpleSwap(menuEntries, i, 0);
				break;
			}
		}
	}

	private void simpleSwap(MenuEntry[] entries, int index1, int index2)
	{
		MenuEntry entry1 = entries[index1],
			entry2 = entries[index2];

		entries[index1] = entry2;
		entries[index2] = entry1;

		client.setMenuEntries(entries);
	}
}

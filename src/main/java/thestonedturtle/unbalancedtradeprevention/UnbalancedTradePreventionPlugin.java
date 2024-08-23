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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
	private static final int TRADE_WINDOW_OPPONENT_ITEMS_CHILD_ID = 29;

	private static final Pattern SELF_VALUE_PATTERN = Pattern.compile("You are about to give:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final Pattern OPPONENT_VALUE_PATTERN = Pattern.compile("In return you will receive:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final NumberFormat VALUE_FORMAT = NumberFormat.getNumberInstance(java.util.Locale.UK);

	private static final String UNBALANCED_TRADE_CHAT_MESSAGE = "<col=ff0000>Unbalanced trade detected! The accept trade option has been set to right-click only.</col>";
	private static final String WHITELISTED_TRADE_CHAT_MESSAGE = "<col=ff0000>A non-whitelisted item was found in the opponents trade window</col>";
	private static final String BLACKLISTED_TRADE_CHAT_MESSAGE = "<col=ff0000>A blacklisted item was found in the opponents trade window</col>";

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
	private final Set<String> filterItemNames = new HashSet<>();
	private final Collection<String> filterWildcardNames = new ArrayList<>();

	@Override
	protected void startUp()
	{
		if (!client.getGameState().equals(GameState.LOGGED_IN))
		{
			return;
		}

		checkTradeWindow();
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
	 *
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

	private List<String> getOpponentItemNames()
	{
		final Widget opponentItemContainer = client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_ITEMS_CHILD_ID);
		if (opponentItemContainer == null)
		{
			return null;
		}

		final List<String> list = new ArrayList<>();
		for (final Widget itemWidget : opponentItemContainer.getDynamicChildren())
		{
			// If there are multiple of the same item then there will be a white `x`
			// This seems to be the only time there will be a color tag inside these widgets
			final String name = itemWidget.getText().split("<col")[0].trim().toLowerCase();
			list.add(name);

			// TODO: Allow setting/removing items from whitelist/blacklist from trade interface?
		}

		return list;
	}

	private void checkTradeWindow()
	{
		if (client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID) == null)
		{
			return;
		}

		if (!ItemFilterType.OFF.equals(config.filterType()))
		{
			final List<String> itemNames = getOpponentItemNames();
			// If there was some issue getting their items return false;
			if (itemNames == null)
			{
				unbalancedTradeDetected = true;
				sendChatMessage(false);
				return;
			}

			final boolean isBlacklist = ItemFilterType.BLACKLIST.equals(config.filterType());

			// Returns true if BLACKLIST and any match or WHITELIST and any do not match
			final boolean filterUnbalancedMatch = isBlacklist
				? itemNames.stream().anyMatch(filterItemNames::contains)
				: !filterItemNames.containsAll(itemNames);

			if (filterUnbalancedMatch)
			{
				unbalancedTradeDetected = true;
				sendChatMessage(true);
				return;
			}

			// Check wildcards last
			// Returns true if BLACKLIST and any match or WHITELIST and any do not match
			final boolean wildcardUnbalancedMatch = filterWildcardNames.stream()
				.anyMatch(wildcard ->
				{
					final String[] split = wildcard.split("\\*");

					// If it starts with the wildcard we're looking for strings that end with this value
					if (wildcard.startsWith("*") && split.length > 1)
					{
						// Do not trim the actual search term as we may want to match stuff with a leading space
						final String searchTerm = split[1];
						if (searchTerm.trim().isEmpty())
						{
							return false;
						}

						// ItemFilterType.BLACKLIST = If any match
						// ItemFilterType.WHITELIST = If any do not match
						return isBlacklist
							? itemNames.stream().anyMatch(s -> s.endsWith(searchTerm))
							: !itemNames.stream().allMatch(s -> s.endsWith(searchTerm));
					}

					// Do not trim the actual search term as we may want to match stuff with a trailing space
					final String searchTerm = split[0];
					if (searchTerm.trim().isEmpty())
					{
						return false;
					}

					// ItemFilterType.BLACKLIST = If any match
					// ItemFilterType.WHITELIST = If any do not match
					return isBlacklist
						? itemNames.stream().anyMatch(s -> s.startsWith(searchTerm))
						: !itemNames.stream().allMatch(s -> s.startsWith(searchTerm));
				});

			if (wildcardUnbalancedMatch)
			{
				unbalancedTradeDetected = true;
				sendChatMessage(true);
				return;
			}
		}

		int delta = getTradeWindowDelta();
		unbalancedTradeDetected = delta >= config.valueThreshold();
		if (unbalancedTradeDetected)
		{
			sendChatMessage(false);
		}
	}

	private void sendChatMessage(boolean byFilter)
	{
		sendChatMessage(UNBALANCED_TRADE_CHAT_MESSAGE);
		if (byFilter)
		{
			switch (config.filterType())
			{
				case WHITELIST:
					sendChatMessage(WHITELISTED_TRADE_CHAT_MESSAGE);
					return;
				case BLACKLIST:
					sendChatMessage(BLACKLISTED_TRADE_CHAT_MESSAGE);
			}
		}
	}

	private void sendChatMessage(String message)
	{
		if (!client.isClientThread())
		{
			clientThread.invoke(() -> sendChatMessage(message));
			return;
		}

		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
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

		updateFilters();
		checkTradeWindow();
	}

	private void updateFilters()
	{
		filterItemNames.clear();
		filterWildcardNames.clear();

		final Map<Boolean, List<String>> items = Arrays.stream(config.itemList().split(","))
			.map(s -> s.trim().toLowerCase())
			.collect(Collectors.partitioningBy(s -> s.contains("*")));

		filterItemNames.addAll(items.get(false));
		filterWildcardNames.addAll(items.get(true));
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

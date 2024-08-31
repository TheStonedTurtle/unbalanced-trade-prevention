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

import com.google.common.annotations.VisibleForTesting;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetUtil;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import net.runelite.client.util.WildcardMatcher;

@Slf4j
@PluginDescriptor(
	name = "Unbalanced Trade Prevention"
)
public class UnbalancedTradePreventionPlugin extends Plugin
{
	private static final int TRADE_WINDOW_OPPONENT_ITEMS_CHILD_ID = 29;
	@VisibleForTesting
	public static final int TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID = 334;
	@VisibleForTesting
	static final int TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID = 23;
	@VisibleForTesting
	static final int TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID = 24;
	private static final int TRADE_WINDOW_OPPONENT_NAME_CHILD_ID = 30;

	private static final Pattern SELF_VALUE_PATTERN = Pattern.compile("You are about to give:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final Pattern OPPONENT_VALUE_PATTERN = Pattern.compile("In return you will receive:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final NumberFormat VALUE_FORMAT = NumberFormat.getNumberInstance(java.util.Locale.UK);

	private static final String UNBALANCED_TRADE_CHAT_MESSAGE = "<col=ff0000>Unbalanced trade detected! The accept trade option has been set to right-click only.</col>";
	private static final String WHITELISTED_TRADE_CHAT_MESSAGE = "<col=ff0000>A non-whitelisted item was found in the opponents trade window</col>";
	private static final String BLACKLISTED_TRADE_CHAT_MESSAGE = "<col=ff0000>A blacklisted item was found in the opponents trade window</col>";
	private static final String FRIEND_WHITELISTED_MESSAGE = "<col=009900>You're trading a whitelisted friend, unbalanced trade detection is disabled</col>";

	private static final String ADD_FRIEND_WHITELIST = "Whitelist";
	private static final String REMOVE_FRIEND_WHITELIST = "Revoke Whitelist";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@VisibleForTesting
	@Inject
	UnbalancedTradePreventionConfig config;

	@Provides
	UnbalancedTradePreventionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(UnbalancedTradePreventionConfig.class);
	}

	private boolean unbalancedTradeDetected = false;
	@Getter
	private final Set<String> filterItemNames = new HashSet<>();
	@Getter
	private final Collection<String> filterWildcardNames = new ArrayList<>();
	@Getter
	private final Set<String> friends = new HashSet<>();

	@Override
	protected void startUp()
	{
		updateFilters();

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
		filterItemNames.clear();
		filterWildcardNames.clear();
		friends.clear();
	}

	private int parseStringForValue(String text, Pattern p)
	{
		Matcher m = p.matcher(Text.removeTags(text));
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

	String getTextByWidget(int groupId, int childId)
	{
		Widget w = client.getWidget(groupId, childId);
		if (w == null)
		{
			return null;
		}

		return w.getText();
	}

	/**
	 * Calculates the difference between your trade value and your opponents trade value
	 *
	 * @return the difference between your trades. Returns `Integer.MAX_VALUE` if it can not find the values or if your value is `Lots!`
	 */
	@VisibleForTesting
	int getTradeWindowDelta()
	{
		String selfValueText = getTextByWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID);
		String opponentValueText = getTextByWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID);
		if (selfValueText == null || opponentValueText == null)
		{
			return Integer.MAX_VALUE;
		}

		int selfValue = parseStringForValue(selfValueText, SELF_VALUE_PATTERN);
		int opponentValue = parseStringForValue(opponentValueText, OPPONENT_VALUE_PATTERN);

		// If there was an error getting our own value, or it equals "Lots!" (or max cash), assume the trade is in their favor
		// If there was an error getting the opponents value also assume it's in their favor
		if (selfValue == -1 || selfValue == Integer.MAX_VALUE || opponentValue == -1)
		{
			return Integer.MAX_VALUE;
		}

		return selfValue - opponentValue;
	}

	@VisibleForTesting
	Set<String> getOpponentItemNames()
	{
		final Widget opponentItemContainer = client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_ITEMS_CHILD_ID);
		if (opponentItemContainer == null)
		{
			return null;
		}

		final Set<String> set = new HashSet<>();
		for (final Widget itemWidget : opponentItemContainer.getDynamicChildren())
		{
			// If there are multiple of the same item then there will be a white `x`
			// This seems to be the only time there will be a color tag inside these widgets
			final String name = itemWidget.getText().split("<col")[0].trim().toLowerCase();
			set.add(name);

			// TODO: Allow setting/removing items from whitelist/blacklist from trade interface?
		}

		return set;
	}

	private String getTradePartnersName()
	{
		final String text = getTextByWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_OPPONENT_NAME_CHILD_ID);
		if (text == null)
		{
			return null;
		}

		return Text.removeTags(text).replace("Trading with:", "").toLowerCase().trim();
	}

	private void checkTradeWindow()
	{
		if (client.getWidget(TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID, TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID) == null)
		{
			unbalancedTradeDetected = false; // Ensure it's false if the widget can't be found
			return;
		}

		// Friends are always allowed to trade us
		final String opponentName = getTradePartnersName();
		if (getFriends().contains(opponentName))
		{
			sendChatMessage(FRIEND_WHITELISTED_MESSAGE);
			unbalancedTradeDetected = false; // Reset to false in case they whitelisted during a trade
			return;
		}

		if (unbalancedTradeByItemFilters())
		{
			unbalancedTradeDetected = true;
			sendChatMessage(true);
			return;
		}

		int delta = getTradeWindowDelta();
		unbalancedTradeDetected = delta >= config.valueThreshold();
		if (unbalancedTradeDetected)
		{
			sendChatMessage(false);
		}
	}

	@VisibleForTesting
	boolean unbalancedTradeByItemFilters()
	{
		if (!ItemFilterType.OFF.equals(config.filterType()))
		{
			final Set<String> itemNames = getOpponentItemNames();
			// If there was some issue getting their items the trade should be unbalanced
			if (itemNames == null)
			{
				return true;
			}

			// Coins and Platinum tokens are always acceptable, so just assume they aren't in the opponents list
			itemNames.remove("coins");
			itemNames.remove("platinum token");
			itemNames.remove("absolutely nothing!");

			if (itemNames.isEmpty())
			{
				return false;
			}

			final Set<String> currentFilterItemNames = getFilterItemNames();
			final Collection<String> matchingNames = itemNames.stream()
				.filter(currentFilterItemNames::contains)
				.collect(Collectors.toList());

			final boolean isBlacklist = ItemFilterType.BLACKLIST.equals(config.filterType());
			if (isBlacklist && !matchingNames.isEmpty())
			{
				return true;
			}

			// If we're whitelisting we may have matched some directly but not all
			// The rest could be matched via wildcards
			matchingNames.forEach(itemNames::remove);

			// Check each wildcard filter
			for (final String wildcard : getFilterWildcardNames())
			{
				final Collection<String> wildcardMatchingNames = itemNames.stream()
					.filter(s -> WildcardMatcher.matches(wildcard, s))
					.collect(Collectors.toList());

				// If it matches and we're blacklisting return early
				if (isBlacklist && !wildcardMatchingNames.isEmpty())
				{
					return true;
				}

				wildcardMatchingNames.forEach(itemNames::remove);
				if (itemNames.isEmpty())
				{
					break;
				}
			}


			// If we weren't able to match all the names and we're whitelisting
			return !isBlacklist && !itemNames.isEmpty();
		}

		return false;
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

		friends.clear();
		friends.addAll(Arrays.stream(config.friendsList().split(","))
			.map(s -> s.trim().toLowerCase())
			.collect(Collectors.toList()));
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		// The menu is not rebuilt when it is open so no need to swap
		if (!unbalancedTradeDetected || client.isMenuOpen())
		{
			return;
		}

		MenuEntry[] menuEntries = client.getMenu().getMenuEntries();
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


	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		final int groupId = WidgetUtil.componentToInterface(event.getActionParam1());

		if (!(groupId == InterfaceID.FRIEND_LIST && event.getOption().equals("Message")))
		{
			return;
		}

		final String friend = Text.toJagexName(Text.removeTags(event.getTarget()));

		client.getMenu().createMenuEntry(-2)
			.setOption(friends.contains(friend.toLowerCase()) ? REMOVE_FRIEND_WHITELIST : ADD_FRIEND_WHITELIST)
			.setType(MenuAction.RUNELITE)
			.setTarget(event.getTarget())
			.onClick(e ->
			{
				final String sanitizedFriend = Text.toJagexName(Text.removeTags(event.getTarget()));

				// Remove friend from list
				if (friends.contains(sanitizedFriend.toLowerCase()))
				{
					List<String> friends = Arrays.stream(config.friendsList().split(","))
						.filter(s -> !s.isEmpty() && !s.equalsIgnoreCase(sanitizedFriend))
						.collect(Collectors.toList());
					config.setFriendsList(String.join(",", friends));
					return;
				}

				// Add friend to list
				config.setFriendsList(config.friendsList().isEmpty() ? sanitizedFriend : String.format("%s,%s", config.friendsList(), sanitizedFriend));
			});
	}

	private void simpleSwap(MenuEntry[] entries, int index1, int index2)
	{
		MenuEntry entry1 = entries[index1],
			entry2 = entries[index2];

		entries[index1] = entry2;
		entries[index2] = entry1;

		client.getMenu().setMenuEntries(entries);
	}
}

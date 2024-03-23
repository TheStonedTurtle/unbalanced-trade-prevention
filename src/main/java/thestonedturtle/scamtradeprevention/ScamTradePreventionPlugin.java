package thestonedturtle.scamtradeprevention;

import com.google.inject.Provides;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PostMenuSort;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;

@Slf4j
@PluginDescriptor(
	name = "Scam Trade Prevention"
)
public class ScamTradePreventionPlugin extends Plugin
{
	private static final int TRADE_WINDOW_SECOND_SCREEN_INTERFACE_ID = 334;
	private static final int TRADE_WINDOW_SELF_VALUE_TEXT_CHILD_ID = 23;
	private static final int TRADE_WINDOW_OPPONENT_VALUE_TEXT_CHILD_ID = 24;

	private static final Pattern SELF_VALUE_PATTERN = Pattern.compile("You are about to give:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final Pattern OPPONENT_VALUE_PATTERN = Pattern.compile("In return you will receive:\\(Value: ([\\d,]* coins|Lots!)\\)");
	private static final NumberFormat VALUE_FORMAT = NumberFormat.getNumberInstance(java.util.Locale.UK);

	@Inject
	private Client client;

	@Inject
	private ScamTradePreventionConfig config;

	@Provides
	ScamTradePreventionConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(ScamTradePreventionConfig.class);
	}

	private boolean scamTradeDetected = false;

	@Override
	protected void startUp()
	{
		if (!client.getGameState().equals(GameState.LOGGED_IN)) {
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
		scamTradeDetected = false;
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
		scamTradeDetected = delta >= config.valueThreshold();
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
			scamTradeDetected = false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals(ScamTradePreventionConfig.GROUP_NAME))
		{
			return;
		}

		checkTradeWindow();
	}

	@Subscribe
	public void onPostMenuSort(PostMenuSort postMenuSort)
	{
		// The menu is not rebuilt when it is open so no need to swap
		if (!scamTradeDetected || client.isMenuOpen())
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

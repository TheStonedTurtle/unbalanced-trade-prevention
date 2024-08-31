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

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(UnbalancedTradePreventionConfig.GROUP_NAME)
public interface UnbalancedTradePreventionConfig extends Config
{
	String GROUP_NAME = "UnbalancedTradePrevention";

	@ConfigItem(
		keyName = "valueThreshold",
		name = "Trade Value Threshold",
		description = "A trade will be considered unbalanced if the value of the trade favors the other play by at least this amount",
		position = 0
	)
	default int valueThreshold()
	{
		return 100_000;
	}

	@ConfigItem(
		keyName = "filterType",
		name = "Item Filter Method",
		description = "<html>Controls how the list of items should be treated<br/>" +
			"<br/>Off: No item filtering" +
			"<br/>Whitelist: If the other player's trade contains any item NOT in the list it will be considered unbalanced (excluding coins/plat tokens)" +
			"<br/>Blacklist: If any of the listed items are in the other player's trade then it will be considered it unbalanced" +
			"</html>",
		position = 1
	)
	default ItemFilterType filterType()
	{
		return ItemFilterType.WHITELIST;
	}

	@ConfigItem(
		keyName = "itemList",
		name = "Item List",
		description = "<html>The list of items that are used for the `Item Filter Method` config option<br/>" +
			"<br/>Format: (item),(item)" +
			"<br/>Supports wildcards: (item*)/(*item) only, (it*em) does not work" +
			"</html>",
		position = 2
	)
	default String itemList()
	{
		return "";
	}

	@ConfigItem(
		keyName = "friendsList",
		name = "Whitelisted Friends",
		description = "<html>The list of usernames that will be ignored for this plugin, allowing you to always trade them even if the trade would otherwise be unbalanced<br/>" +
			"<br/>A right-click option has been added to each friend in your friends list to easily add or remove them from this list" +
			"<br/>Format: (username),(username)" +
			"</html>",
		position = 3
	)
	default String friendsList()
	{
		return "";
	}

	@ConfigItem(
		keyName = "friendsList",
		name = "",
		description = ""
	)
	void setFriendsList(String key);
}

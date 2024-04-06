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
		description = "A trade will be considered unbalanced if the value of the trade favors the other play by at least this amount"
	)
	default int valueThreshold()
	{
		return 100_000;
	}
}

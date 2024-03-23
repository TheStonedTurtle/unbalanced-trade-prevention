package thestonedturtle.scamtradeprevention;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(ScamTradePreventionConfig.GROUP_NAME)
public interface ScamTradePreventionConfig extends Config
{
	String GROUP_NAME = "ScamTradePrevention";
	@ConfigItem(
		keyName = "valueThreshold",
		name = "Scam Value Threshold",
		description = "A trade will be considered a scam if the value of the trade favors the other play by at least this amount"
	)
	default int valueThreshold()
	{
		return 100_000;
	}
}

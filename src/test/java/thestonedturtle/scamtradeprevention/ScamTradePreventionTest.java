package thestonedturtle.scamtradeprevention;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class ScamTradePreventionTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(ScamTradePreventionPlugin.class);
		RuneLite.main(args);
	}
}
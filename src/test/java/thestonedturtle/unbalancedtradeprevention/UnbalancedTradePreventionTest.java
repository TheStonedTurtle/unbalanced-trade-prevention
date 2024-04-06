package thestonedturtle.unbalancedtradeprevention;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class UnbalancedTradePreventionTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(UnbalancedTradePreventionPlugin.class);
		RuneLite.main(args);
	}
}
# Unbalanced Trade Prevention [![Plugin Installs](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/unbalanced-trade-prevention)](https://runelite.net/plugin-hub/TheStonedTurtle) [![Plugin Rank](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/unbalanced-trade-prevention)](https://runelite.net/plugin-hub)
Disables the left-click `Accept` option on the second trade window if the value of the trade is vastly in the other players favor. The exact amount can be controlled via the plugin config options.

# Limitations
* Item prices are based off the GE price as displayed in-game via the Price Checker UI.
  * Prices that are vastly inflated/overpriced may make the trade look favorable for you when it's not, don't trade for items you aren't sure about the price of. 
* If the second trade window says `Lots!` it will assume the value is max cash. This is because we can't calculate the true price of the trade once the value gets this high.

# Unbalanced Trade Prevention [![Plugin Installs](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/installs/plugin/unbalanced-trade-prevention)](https://runelite.net/plugin-hub/TheStonedTurtle) [![Plugin Rank](http://img.shields.io/endpoint?url=https://api.runelite.net/pluginhub/shields/rank/plugin/unbalanced-trade-prevention)](https://runelite.net/plugin-hub)

Disables the left-click `Accept` option on the second trade window if the value of the trade is in the other players
favor. The exact amount for what determines being in the other players favor can be controlled via the plugin config
options.

# Limitations

* Item prices are based off the GE price as displayed in-game via the Price Checker UI.
    * Prices that are vastly inflated/overpriced may make the trade look favorable for you when it's not, don't trade
      for items you aren't sure about the price of.
* If the second trade window says `Lots!` it will assume the value is max cash. This is because we can't calculate the
  true price of the trade once the value gets this high.

## Config Options

| Name                                            | Default Value |
|:------------------------------------------------|:--------------|
| [Trade Value Threshold](#trade-value-threshold) | 100,000       |
| [Item Filter Method](#item-filter-method)       | Whitelist     | 
| [Item List](#item-list)                         | N/A           |

### Trade Value Threshold

Controls how much money you can give away before the trade is unbalanced. If this is set to 100k then you can give up
to 99,999 gp away without it being considered unbalanced.

### Item Filter Method

Controls if any item filtering should be enabled. Item Filtering allows you to automatically flag a trade as unbalanced
based on the items in the trade.

There are three possible Item Filtering methods:

* **OFF:** Completely disables item filtering
* **WHITELSIT:** Any item that **IS NOT** specified in the list will cause the trade to be unbalanced
* **BLACKLIST:** Any item that **IS** specified in the list will cause the trade to be unbalanced

### Item List

This is the list of items that are used for the `Item Filter Method` config option. This is formatted similar to most
RuneLite item lists, such as `Ground Items` and `NPC Indicators`.

This item list is **NOT** case-sensitive

You must separate each item name by a comma, example:

```
Rune scimitar,Rune longsword
```

It also supports wildcards, both leading and trailing.

If you wanted to indicate that all (1) dose potions in the list you can write:

```
* (1)
```

And if you wanted to include all Runite items you could time

```
Runite*
```

Whitespace here matters, for example if you put:

```
Mith *
```

This would match `Mith Grapple` but would not match `Mithril bar` as there's a space between `Mith` and `*`. If you
remove the space it would match against both.


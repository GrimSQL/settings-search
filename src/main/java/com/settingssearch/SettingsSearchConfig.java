package com.settingssearch;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(SettingsSearchConfig.GROUP)
public interface SettingsSearchConfig extends Config
{
	String GROUP = "settings-search";
	String FUZZY_KEY = "fuzzySearch";
	String HISTORY_KEY = "searchHistory";
	String FUZZY_DESCRIPTION = "Finds close matches for typos, for example “notificaiton” also finds “notification”. "
		+ "Turn this off to match only the letters you type.";

	@ConfigItem(keyName = FUZZY_KEY, name = "Fuzzy search", description = FUZZY_DESCRIPTION)
	default boolean fuzzySearch()
	{
		return true;
	}
}

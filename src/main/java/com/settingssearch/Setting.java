package com.settingssearch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

final class Setting
{
	private static final Pattern CAMEL_CASE = Pattern.compile("(?<=[a-z0-9])(?=[A-Z])");

	final String name;
	final String plugin;
	final String section;
	final String description;
	final Runnable open;
	final String normalizedName;
	final List<List<String>> searchFields;

	Setting(String name, String plugin, String section, String description,
		String key, String[] tags, Runnable open)
	{
		this.name = name;
		this.plugin = plugin;
		this.section = section;
		this.description = description;
		this.open = open;
		normalizedName = SettingSearch.normalize(name);
		searchFields = new ArrayList<>();
		for (String field : List.of(name, section, plugin, key + " " + CAMEL_CASE.matcher(key).replaceAll(" "),
			description, String.join(" ", tags)))
		{
			searchFields.add(SettingSearch.words(field));
		}
	}

	String location()
	{
		return section.isBlank() ? plugin : plugin + " / " + section;
	}
}

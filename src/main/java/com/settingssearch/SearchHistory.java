package com.settingssearch;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class SearchHistory
{
	private static final int LIMIT = 10;

	private final Gson gson;
	private final Supplier<String> load;
	private final Consumer<String> save;

	SearchHistory(Gson gson, Supplier<String> load, Consumer<String> save)
	{
		this.gson = gson;
		this.load = load;
		this.save = save;
	}

	List<Entry> entries()
	{
		return readEntries(load.get());
	}

	private List<Entry> readEntries(String value)
	{
		List<Entry> entries = new ArrayList<>();
		try
		{
			Entry[] stored = gson.fromJson(value, Entry[].class);
			if (stored != null)
			{
				for (Entry entry : stored)
				{
					if (entry != null && entry.query != null && !entry.query.isBlank())
					{
						entries.add(entry);
						if (entries.size() == LIMIT)
						{
							break;
						}
					}
				}
			}
		}
		catch (JsonParseException ignored)
		{
			// An invalid profile value is replaced when a new search is saved.
		}
		return entries;
	}

	void remember(String query, Setting setting)
	{
		String normalized = SettingSearch.normalize(query);
		if (normalized.isEmpty())
		{
			return;
		}

		String stored = load.get();
		List<Entry> recent = readEntries(stored);
		Entry previous = recent.stream()
			.filter(entry -> normalized.equals(SettingSearch.normalize(entry.query)))
			.findFirst().orElse(null);
		Entry next = setting == null
			? new Entry(query.strip(), previous)
			: new Entry(query.strip(), setting.name, setting.plugin, setting.key);
		recent.remove(previous);
		recent.add(0, next);
		String updated = gson.toJson(recent.subList(0, Math.min(recent.size(), LIMIT)));
		if (!updated.equals(stored))
		{
			save.accept(updated);
		}
	}

	static final class Entry
	{
		final String query;
		final String settingName;
		final String plugin;
		final String settingKey;

		private Entry(String query, Entry previous)
		{
			this(query, previous == null ? null : previous.settingName, previous == null ? null : previous.plugin,
				previous == null ? null : previous.settingKey);
		}

		private Entry(String query, String settingName, String plugin, String settingKey)
		{
			this.query = query;
			this.settingName = settingName;
			this.plugin = plugin;
			this.settingKey = settingKey;
		}

		boolean matches(Setting setting)
		{
			return setting.plugin.equals(plugin) && setting.key.equals(settingKey);
		}
	}
}

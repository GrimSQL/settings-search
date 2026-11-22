package com.settingssearch;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

final class SettingSearch
{

	private static final Pattern ACCENTS = Pattern.compile("\\p{M}+");
	private static final Pattern SEPARATORS = Pattern.compile("[^\\p{L}\\p{N}]+");
	private static final int[] FIELD_WEIGHTS = {32, 18, 16, 12, 7, 5};

	private SettingSearch()
	{
	}

	static String normalize(String value)
	{

		String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
		String plain = ACCENTS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT);
		return SEPARATORS.matcher(plain).replaceAll(" ").strip();
	}

	static List<String> words(String value)
	{
		String normalized = normalize(value);
		return normalized.isEmpty() ? List.of() : List.of(normalized.split(" "));
	}

	static List<Setting> find(List<Setting> index, String query, boolean fuzzy)
	{
		String phrase = normalize(query);
		if (phrase.isEmpty())
		{
			return List.of();
		}
		List<String> terms = List.of(phrase.split(" "));
		List<Map<String, Integer>> scoresByWord = new ArrayList<>();
		for (String ignored : terms)
		{
			scoresByWord.add(new HashMap<>());
		}
		List<Match> matches = new ArrayList<>();
		for (Setting setting : index)
		{
			int score = 0;
			for (int i = 0; i < terms.size(); i++)
			{
				int termScore = scoreTerm(setting, terms.get(i), fuzzy, scoresByWord.get(i));
				if (termScore == 0)
				{
					score = 0;
					break;
				}
				score += termScore;
			}
			if (score > 0)
			{
				if (setting.normalizedName.equals(phrase))
				{
					score += 80;
				}
				else if ((" " + setting.normalizedName + " ").contains(" " + phrase + " "))
				{
					score += 40;
				}
				matches.add(new Match(setting, score));
			}
		}
		matches.sort(Comparator.comparingInt((Match match) -> match.score).reversed()
			.thenComparing(match -> match.setting.name, String.CASE_INSENSITIVE_ORDER)
			.thenComparing(match -> match.setting.location(), String.CASE_INSENSITIVE_ORDER));
		List<Setting> result = new ArrayList<>(matches.size());
		for (Match match : matches)
		{
			result.add(match.setting);
		}
		return result;
	}

	private static int scoreTerm(Setting setting, String term, boolean fuzzy, Map<String, Integer> cache)
	{
		int best = 0;
		for (int i = 0; i < FIELD_WEIGHTS.length; i++)
		{
			for (String word : setting.searchFields.get(i))
			{
				int score = cache.computeIfAbsent(word, candidate -> scoreWord(term, candidate, fuzzy));
				if (score > 0)
				{
					best = Math.max(best, score + FIELD_WEIGHTS[i]);
				}
			}
		}
		return best;
	}

	private static int scoreWord(String term, String word, boolean fuzzy)
	{
		if (word.equals(term))
		{
			return 100;
		}
		if (word.startsWith(term))
		{
			return 75;
		}
		if (term.length() >= 3 && word.contains(term))
		{
			return 54;
		}
		int limit = term.length() >= 8 ? 2 : 1;
		if (!fuzzy || term.length() < 4 || Math.abs(word.length() - term.length()) > limit)
		{
			return 0;
		}
		int distance = editDistance(term, word);
		return distance <= limit ? 30 - 8 * (distance - 1) : 0;
	}

	// Optimal string alignment counts one adjacent transposition as one edit.
	private static int editDistance(String term, String word)
	{
		int[] twoBack = new int[word.length() + 1];
		int[] previous = new int[word.length() + 1];
		int[] current = new int[word.length() + 1];
		for (int j = 0; j <= word.length(); j++)
		{
			previous[j] = j;
		}
		for (int i = 1; i <= term.length(); i++)
		{
			current[0] = i;
			for (int j = 1; j <= word.length(); j++)
			{
				int substitution = term.charAt(i - 1) == word.charAt(j - 1) ? 0 : 1;
				current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1),
					previous[j - 1] + substitution);
				if (i > 1 && j > 1 && term.charAt(i - 1) == word.charAt(j - 2)
					&& term.charAt(i - 2) == word.charAt(j - 1))
				{
					current[j] = Math.min(current[j], twoBack[j - 2] + 1);
				}
			}
			int[] reuse = twoBack;
			twoBack = previous;
			previous = current;
			current = reuse;
		}
		return previous[word.length()];
	}

	private static final class Match
	{
		private final Setting setting;
		private final int score;

		private Match(Setting setting, int score)
		{
			this.setting = setting;
			this.score = score;
		}
	}
}

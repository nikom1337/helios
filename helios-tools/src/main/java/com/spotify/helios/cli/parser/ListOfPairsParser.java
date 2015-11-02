package com.spotify.helios.cli.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.lang.String.format;

public abstract class ListOfPairsParser implements Parser {

  private static final String DELIMITER = "=";

  protected Map<String, String> parseListOfPairs(final List<String> list, final String fieldName) {
    final Map<String, String> pairs = new HashMap<>();
    for (final String s : list) {
      final String[] parts = parsePair(fieldName, s);
      pairs.put(parts[0], parts[1]);
    }
    return pairs;
  }

  private String[] parsePair(final String fieldName, final String s) {
    final String[] parts = s.split(DELIMITER, 2);
    if (parts.length != 2) {
      throw new IllegalArgumentException(format("Bad format for %s: '%s', expecting %s-delimited pairs", fieldName, s, DELIMITER));
    }
    return parts;
  }
}

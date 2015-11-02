package com.spotify.helios.cli.parser;

import com.google.common.collect.Maps;

import java.util.List;
import java.util.Map;

import static com.spotify.helios.common.descriptors.Job.Builder;

public class EnvParser extends ListOfPairsParser {
  private final List<String> envList;

  public EnvParser(List<String> envList) {
    this.envList = envList;
  }

  @Override
  public void execute(Builder builder) {
    // TODO (mbrown): does this mean that env config is only added when there is a CLI flag too?
    if (!envList.isEmpty()) {
      final Map<String, String> env = Maps.newHashMap();
      // Add environmental variables from helios job configuration file
      env.putAll(builder.getEnv());
      // Add environmental variables passed in via CLI
      // Overwrite any redundant keys to make CLI args take precedence
      env.putAll(parseListOfPairs(envList, "environment variable"));

      builder.setEnv(env);
    }
  }
}

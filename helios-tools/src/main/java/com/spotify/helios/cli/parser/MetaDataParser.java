package com.spotify.helios.cli.parser;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.spotify.helios.common.descriptors.Job.Builder;

import java.util.List;
import java.util.Map;

public class MetaDataParser extends ListOfPairsParser {

  /**
   * If any of the keys of this map are set as environment variables (i.e. an environment variable
   * of GIT_COMMIT_ID=abcdef is set when this command is run), then an entry will be added to the
   * job's metadata map with with key=this-maps-value, value=environment variable value.
   *
   * @see #defaultMetadata()
   */
  public static final Map<String, String> DEFAULT_METADATA_ENVVARS = ImmutableMap.of(
          // GIT_COMMIT is set by the Git plugin in Jenkins, so for jobs created in
          // Jenkins this will automatically set GIT_COMMIT = the sha1 of the
          // working tree
          "GIT_COMMIT", "GIT_COMMIT"
  );

  private final List<String> metadataSpec;
  private final Map<String, String> envVars;

  public MetaDataParser(List<String> metadataSpec, Map<String, String> envVars) {
    this.metadataSpec = metadataSpec;
    this.envVars = envVars;
  }

  @Override
  public void execute(Builder builder) {
    builder.setMetadata(getMetaData(metadataSpec));
  }

  private Map<String, String> getMetaData(List<String> metadataList) {
    Map<String, String> metadata = Maps.newHashMap();
    metadata.putAll(defaultMetadata());
    if (!metadataList.isEmpty()) {
      // TODO (mbrown): values from job conf file (which maybe involves dereferencing env vars?)
      metadata.putAll(parseListOfPairs(metadataList, "metadata"));
    }
    return metadata;
  }

  /**
   * Metadata to associate with jobs by default. Currently sets some metadata based upon environment
   * variables set when the CLI command is run.
   */
  private Map<String, String> defaultMetadata() {
    final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();

    for (final Map.Entry<String, String> entry : DEFAULT_METADATA_ENVVARS.entrySet()) {
      final String envKey = entry.getKey();
      final String metadataKey = entry.getValue();

      final String envValue = envVars.get(envKey);
      if (envValue != null) {
        builder.put(metadataKey, envValue);
      }
    }

    return builder.build();
  }
}

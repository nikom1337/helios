package com.spotify.helios.cli.parser;

import com.google.common.collect.Maps;
import com.spotify.helios.common.descriptors.Job;

import java.util.List;
import java.util.Map;

import static com.spotify.helios.common.descriptors.Job.EMPTY_MOUNT;

public class VolumesParser implements Parser {

    private List<String> volumesSpec;

    public VolumesParser(List<String> volumesSpec) {
        this.volumesSpec = volumesSpec;
    }

    @Override
    public void execute(Job.Builder builder) {
        final Map<String, String> volumes = Maps.newHashMap();
        volumes.putAll(builder.getVolumes());
        volumes.putAll(getVolumes(volumesSpec));
        builder.setVolumes(volumes);
    }

    private Map<String, String> getVolumes(List<String> volumeSpecs) {
        Map<String, String> volumes = Maps.newHashMap();
        for (final String spec : volumeSpecs) {
            final String[] parts = spec.split(":", 2);
            switch (parts.length) {
                // Data volume
                case 1:
                    volumes.put(parts[0], EMPTY_MOUNT);
                    break;
                // Bind mount
                case 2:
                    final String path = parts[1];
                    final String source = parts[0];
                    volumes.put(path, source);
                    break;
                default:
                    throw new IllegalArgumentException("Invalid volume: " + spec);
            }
        }
        return volumes;
    }
}

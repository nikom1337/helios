package com.spotify.helios.cli.parser;

import com.spotify.helios.common.descriptors.Job.Builder;
import com.spotify.helios.common.descriptors.Resources;

import java.util.List;

public class ResourcesParser implements Parser {

    private final List<String> resourcesSpec;

    public ResourcesParser(List<String> resourcesSpec) {
        this.resourcesSpec = resourcesSpec;
    }

    @Override
    public void execute(Builder builder) {
        builder.setResources(getResources(resourcesSpec));
    }

    private Resources getResources(List<String> resources) {
        Long memory = null;
        Long memorySwap = null;
        Long cpuShares = null;
        String cpuset = null;

        for (String resource : resources) {
            final String[] parts = resource.split(":", 2);
            switch (parts[0]) {
                case "memory":
                    memory = Long.valueOf(parts[1]);
                    break;
                case "memorySwap":
                    memorySwap = Long.valueOf(parts[1]);
                    break;
                case "cpuShares":
                    cpuShares = Long.valueOf(parts[1]);
                    break;
                case "cpuset":
                    cpuset = parts[1];
                    break;
                default:
                    throw new IllegalArgumentException("Invalid resource: " + parts[0]);
            }
        }
        return new Resources(memory, memorySwap, cpuShares, cpuset);
    }
}

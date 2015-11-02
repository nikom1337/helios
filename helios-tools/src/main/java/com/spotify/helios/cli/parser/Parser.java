package com.spotify.helios.cli.parser;

import com.spotify.helios.common.descriptors.Job.Builder;

public interface Parser {
    void execute(Builder builder);
}

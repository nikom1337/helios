package com.spotify.helios.cli.parser;

import com.spotify.helios.common.descriptors.ExecHealthCheck;
import com.spotify.helios.common.descriptors.HttpHealthCheck;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.TcpHealthCheck;

import java.util.List;

import static com.google.common.base.Strings.isNullOrEmpty;
import static java.util.Arrays.asList;

public class HealthCheckParser implements Parser {
    private final String httpHealthCheck;
    private final String tcpHealthCheck;
    private final List<String> execHealthCheck;

    public HealthCheckParser(String httpHealthCheck, String tcpHealthCheck, List<String> execHealthCheck) {
        this.httpHealthCheck = httpHealthCheck;
        this.tcpHealthCheck = tcpHealthCheck;
        this.execHealthCheck = execHealthCheck;
    }

    @Override
    public void execute(Job.Builder builder) {
        int numberOfHealthChecks = 0;
        for (final String c : asList(httpHealthCheck, tcpHealthCheck)) {
            if (!isNullOrEmpty(c)) {
                numberOfHealthChecks++;
            }
        }
        if (execHealthCheck != null && !execHealthCheck.isEmpty()) {
            numberOfHealthChecks++;
        }

        if (numberOfHealthChecks > 1) {
            throw new IllegalArgumentException("Only one health check may be specified.");
        }

        if (execHealthCheck != null && !execHealthCheck.isEmpty()) {
            builder.setHealthCheck(ExecHealthCheck.of(execHealthCheck));
        } else if (!isNullOrEmpty(httpHealthCheck)) {
            final String[] parts = httpHealthCheck.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid HTTP health check: " + httpHealthCheck);
            }

            builder.setHealthCheck(HttpHealthCheck.of(parts[0], parts[1]));
        } else if (!isNullOrEmpty(tcpHealthCheck)) {
            builder.setHealthCheck(TcpHealthCheck.of(tcpHealthCheck));
        }
    }
}

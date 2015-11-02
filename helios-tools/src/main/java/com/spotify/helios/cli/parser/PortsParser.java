package com.spotify.helios.cli.parser;

import com.google.common.collect.Maps;
import com.spotify.helios.common.descriptors.Job.Builder;
import com.spotify.helios.common.descriptors.PortMapping;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Optional.fromNullable;
import static com.spotify.helios.common.descriptors.PortMapping.TCP;
import static java.util.regex.Pattern.compile;

public class PortsParser implements Parser {

    private final List<String> portsString;

    public PortsParser(List<String> portsString) {
        this.portsString = portsString;
    }

    @Override
    public void execute(Builder builder) {
        final Map<String, PortMapping> explicitPorts = getExplicitPortsMapping(portsString);
        final Map<String, PortMapping> ports = Maps.newHashMap();
        ports.putAll(builder.getPorts());
        ports.putAll(explicitPorts);
        builder.setPorts(ports);
    }

    private Map<String, PortMapping> getExplicitPortsMapping(List<String> portSpecs) {
        final Map<String, PortMapping> explicitPorts = Maps.newHashMap();
        final Pattern portPattern = compile("(?<n>[_\\-\\w]+)=(?<i>\\d+)(:(?<e>\\d+))?(/(?<p>\\w+))?");
        for (final String spec : portSpecs) {
            final Matcher matcher = portPattern.matcher(spec);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Bad port mapping: " + spec);
            }

            final String portName = matcher.group("n");
            final int internal = Integer.parseInt(matcher.group("i"));
            final Integer external = nullOrInteger(matcher.group("e"));
            final String protocol = fromNullable(matcher.group("p")).or(TCP);

            if (explicitPorts.containsKey(portName)) {
                throw new IllegalArgumentException("Duplicate port mapping: " + portName);
            }

            explicitPorts.put(portName, PortMapping.of(internal, external, protocol));
        }
        return explicitPorts;
    }

    private Integer nullOrInteger(final String s) {
        return s == null ? null : Integer.valueOf(s);
    }
}

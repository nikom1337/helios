package com.spotify.helios.cli.parser;

import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.spotify.helios.common.descriptors.Job.Builder;
import com.spotify.helios.common.descriptors.PortMapping;
import com.spotify.helios.common.descriptors.ServiceEndpoint;
import com.spotify.helios.common.descriptors.ServicePorts;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.google.common.base.Optional.fromNullable;
import static com.spotify.helios.common.descriptors.ServiceEndpoint.HTTP;
import static java.util.regex.Pattern.compile;

public class ServiceRegistrationsParser implements Parser {
    private final List<String> registrationSpecs;

    public ServiceRegistrationsParser(List<String> registrationSpecs) {
        this.registrationSpecs = registrationSpecs;
    }

    @Override
    public void execute(Builder builder) {
        final Map<ServiceEndpoint, ServicePorts> registration = Maps.newHashMap();
        registration.putAll(builder.getRegistration());
        registration.putAll(getExpliticRegistration(builder.getPorts(), registrationSpecs));
        builder.setRegistration(registration);
    }

    private Map<ServiceEndpoint, ServicePorts> getExpliticRegistration(Map<String, PortMapping> ports, List<String> registrationSpecs) {
        final Map<ServiceEndpoint, ServicePorts> explicitRegistration = Maps.newHashMap();
        final Pattern registrationPattern = compile("(?<srv>[a-zA-Z][_\\-\\w]+)(?:/(?<prot>\\w+))?(?:=(?<port>[_\\-\\w]+))?");
        for (final String spec : registrationSpecs) {
            final Matcher matcher = registrationPattern.matcher(spec);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Bad registration: " + spec);
            }

            final String service = matcher.group("srv");
            final String proto = fromNullable(matcher.group("prot")).or(HTTP);
            final String optionalPort = matcher.group("port");
            final String port;

            if (ports.size() == 0) {
                throw new IllegalArgumentException("Need port mappings for service registration.");
            }

            if (optionalPort == null) {
                if (ports.size() != 1) {
                    throw new IllegalArgumentException(
                            "Need exactly one port mapping for implicit service registration");
                }
                port = Iterables.getLast(ports.keySet());
            } else {
                port = optionalPort;
            }

            explicitRegistration.put(ServiceEndpoint.of(service, proto), ServicePorts.of(port));
        }
        return explicitRegistration;
    }
}

/*
 * Copyright (c) 2014 Spotify AB.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package com.spotify.helios.cli.command;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;

import com.spotify.helios.cli.parser.EnvParser;
import com.spotify.helios.cli.parser.HealthCheckParser;
import com.spotify.helios.cli.parser.MetaDataParser;
import com.spotify.helios.cli.parser.PortsParser;
import com.spotify.helios.cli.parser.ResourcesParser;
import com.spotify.helios.cli.parser.ServiceRegistrationsParser;
import com.spotify.helios.cli.parser.VolumesParser;
import com.spotify.helios.client.HeliosClient;
import com.spotify.helios.common.JobValidator;
import com.spotify.helios.common.Json;
import com.spotify.helios.common.descriptors.Job;
import com.spotify.helios.common.descriptors.JobId;
import com.spotify.helios.common.protocol.CreateJobResponse;

import net.sourceforge.argparse4j.inf.Argument;
import net.sourceforge.argparse4j.inf.Namespace;
import net.sourceforge.argparse4j.inf.Subparser;

import org.joda.time.DateTime;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static com.google.common.base.Charsets.UTF_8;
import static com.google.common.base.Strings.isNullOrEmpty;
import static com.spotify.helios.common.descriptors.Job.EMPTY_TOKEN;
import static net.sourceforge.argparse4j.impl.Arguments.append;
import static net.sourceforge.argparse4j.impl.Arguments.fileType;
import static net.sourceforge.argparse4j.impl.Arguments.storeTrue;

public class JobCreateCommand extends ControlCommand {

  private static final JobValidator JOB_VALIDATOR = new JobValidator(false);

  // allow the retrieval of environment variables to be swapped out with a different Supplier for
  // testing purposes
  private static final Supplier<Map<String, String>> DEFAULT_ENV_VAR_SUPPLIER =
      new Supplier<Map<String, String>>() {
        @Override
        public Map<String, String> get() {
          return System.getenv();
        }
      };

  private final Argument fileArg;
  private final Argument templateArg;
  private final Argument quietArg;
  private final Argument idArg;
  private final Argument imageArg;
  private final Argument hostnameArg;
  private final Argument tokenArg;
  private final Argument envArg;
  private final Argument argsArg;
  private final Argument portArg;
  private final Argument registrationArg;
  private final Argument registrationDomainArg;
  private final Argument gracePeriodArg;
  private final Argument volumeArg;
  private final Argument expiresArg;
  private final Argument healthCheckExecArg;
  private final Argument healthCheckHttpArg;
  private final Argument healthCheckTcpArg;
  private final Argument securityOptArg;
  private final Argument networkModeArg;
  private final Argument metadataArg;
  private final Argument resourcesArg;
  private final Supplier<Map<String, String>> envVarSupplier;

  public JobCreateCommand(final Subparser parser) {
    this(parser, DEFAULT_ENV_VAR_SUPPLIER);
  }

  @VisibleForTesting
  /**
   * Allows the supplier of environment variables to be swapped out for testing, for example to
   * avoid unexpected environment variables being present during testing.
   */
  protected JobCreateCommand(final Subparser parser, Supplier<Map<String, String>> envVarSupplier) {
    super(parser);

    parser.help("create a job");

    fileArg = parser.addArgument("-f", "--file")
        .type(fileType().acceptSystemIn())
        .help("Job configuration file. Options specified on the command line will be merged with" +
              " the contents of this file. Cannot be used together with -t/--template.");

    templateArg = parser.addArgument("-t", "--template")
        .help("Template job id. The new job will be based on this job. Cannot be used together " +
              "with -f/--file.");

    quietArg = parser.addArgument("-q")
        .action(storeTrue())
        .help("only print job id");

    idArg = parser.addArgument("id")
        .nargs("?")
        .help("Job name:version[:hash]");

    imageArg = parser.addArgument("image")
        .nargs("?")
        .help("Container image");

    hostnameArg = parser.addArgument("--hostname")
         .nargs("?")
         .help("Container hostname");

    tokenArg = parser.addArgument("--token")
         .nargs("?")
         .setDefault(EMPTY_TOKEN)
         .help("Insecure access token meant to prevent accidental changes to your job " +
               "(e.g. undeploys).");

    envArg = parser.addArgument("--env")
        .action(append())
        .setDefault(new ArrayList<String>())
        .help("Environment variables");

    metadataArg = parser.addArgument("-m", "--metadata")
        .action(append())
        .setDefault(new ArrayList<String>())
        .help("Metadata (key-value pairs) to associate with job");

    portArg = parser.addArgument("-p", "--port")
        .action(append())
        .setDefault(new ArrayList<String>())
        .help("Port mapping. Specify an endpoint name and a single port (e.g. \"http=8080\") for " +
              "dynamic port mapping and a name=private:public tuple (e.g. \"http=8080:80\") for " +
              "static port mapping. E.g., foo=4711 will map the internal port 4711 of the " +
              "container to an arbitrary external port on the host. Specifying foo=4711:80 " +
              "will map internal port 4711 of the container to port 80 on the host. The " +
              "protocol will be TCP by default. For UDP, add /udp. E.g. quic=80/udp or " +
              "dns=53:53/udp. The endpoint name can be used when specifying service registration " +
              "using -r/--register.");

    registrationArg = parser.addArgument("-r", "--register")
        .action(append())
        .setDefault(new ArrayList<String>())
        .help("Service discovery registration. Specify a service name, the port name and a " +
              "protocol on the format service/protocol=port. E.g. -r website/tcp=http will " +
              "register the port named http with the protocol tcp. Protocol is optional and " +
              "default is tcp. If there is only one port mapping, this will be used by " +
              "default and it will be enough to specify only the service name, e.g. " +
              "-r wordpress.");

    registrationDomainArg = parser.addArgument("--registration-domain")
        .setDefault("")
        .help("If set, overrides the default domain in which discovery serviceregistration " +
              "occurs. What is allowed here will vary based upon the discovery service plugin " +
              "used.");

    gracePeriodArg = parser.addArgument("--grace-period")
        .type(Integer.class)
        .setDefault((Object) null)
        .help("if --grace-period is specified, Helios will unregister from service discovery and " +
              "wait the specified number of seconds before undeploying, default 0 seconds");

    volumeArg = parser.addArgument("--volume")
        .action(append())
        .setDefault(new ArrayList<String>())
        .help("Container volumes. Specify either a single path to create a data volume, " +
              "or a source path and a container path to mount a file or directory from the host. " +
              "The container path can be suffixed with \"rw\" or \"ro\" to create a read-write " +
              "or read-only volume, respectively. Format: [container-path]:[host-path]:[rw|ro].");

    argsArg = parser.addArgument("args")
        .nargs("*")
        .help("Command line arguments");

    expiresArg = parser.addArgument("-e", "--expires")
        .help("An ISO-8601 string representing the date/time when this job should expire. The " +
              "job will be undeployed from all hosts and removed at this time. E.g. " +
              "2014-06-01T12:00:00Z");

    healthCheckExecArg = parser.addArgument("--exec-check")
        .help("Run `docker exec` health check with the provided command. The service will not be " +
              "registered in service discovery until the command executes successfully in the " +
              "container, i.e. with exit code 0. E.g. --exec-check ping google.com");

    healthCheckHttpArg = parser.addArgument("--http-check")
        .help("Run HTTP health check against the provided port name and path. The service will " +
              "not be registered in service discovery until the container passes the HTTP health " +
              "check. Format: [port name]:[path].");

    healthCheckTcpArg = parser.addArgument("--tcp-check")
        .help("Run TCP health check against the provided port name. The service will not be " +
              "registered in service discovery until the container passes the TCP health check.");

    securityOptArg = parser.addArgument("--security-opt")
        .action(append())
        .setDefault(Lists.newArrayList())
        .help("Run the Docker container with a security option. " +
              "See https://docs.docker.com/reference/run/#security-configuration.");

    networkModeArg = parser.addArgument("--network-mode")
        .help("Sets the networking mode for the container. Supported values are: bridge, host, and "
              + "container:<name|id>. Docker defaults to bridge.");

      resourcesArg = parser.addArgument("--resource")
              .action(append())
              .setDefault(new ArrayList<String>())
              .help("TODO");

    this.envVarSupplier = envVarSupplier;
  }

  @Override
  int run(final Namespace options, final HeliosClient client, final PrintStream out,
          final boolean json, final BufferedReader stdin)
      throws ExecutionException, InterruptedException, IOException {

    final boolean quiet = options.getBoolean(quietArg.getDest());

    final Job.Builder builder;

    final String id = options.getString(idArg.getDest());
    final String imageIdentifier = options.getString(imageArg.getDest());

    // TODO (dano): look for e.g. Heliosfile in cwd by default?

    final String templateJobId = options.getString(templateArg.getDest());
    final File file = options.get(fileArg.getDest());

    if (file != null && templateJobId != null) {
      throw new IllegalArgumentException("Please use only one of -t/--template and -f/--file");
    }

    if (file != null) {
      final Job job = readConfigurationFromFile(file);
      builder = job.toBuilder();
    } else if (templateJobId != null) {
      final Map<JobId, Job> jobs = client.jobs(templateJobId).get();
      if (jobs.size() == 0) {
        if (!json) {
          out.printf("Unknown job: %s%n", templateJobId);
        } else {
          CreateJobResponse createJobResponse =
                  new CreateJobResponse(CreateJobResponse.Status.UNKNOWN_JOB, null, null);
          out.printf(createJobResponse.toJsonString());
        }
        return 1;
      } else if (jobs.size() > 1) {
        if (!json) {
          out.printf("Ambiguous job reference: %s%n", templateJobId);
        } else {
          CreateJobResponse createJobResponse =
                  new CreateJobResponse(CreateJobResponse.Status.AMBIGUOUS_JOB_REFERENCE, null, null);
          out.printf(createJobResponse.toJsonString());
        }
        return 1;
      }
      final Job template = Iterables.getOnlyElement(jobs.values());
      builder = template.toBuilder();
      if (id == null) {
        throw new IllegalArgumentException("Please specify new job name and version");
      }
    } else {
      if (id == null || imageIdentifier == null) {
        throw new IllegalArgumentException(
                "Please specify a file, or a template, or a job name, version and container image");
      }
      builder = Job.newBuilder();
    }

    // Merge job configuration options from command line arguments
    if (id != null) {
      final String[] parts = id.split(":");
      switch (parts.length) {
        case 3:
          builder.setHash(parts[2]);
          // fall through
        case 2:
          builder.setVersion(parts[1]);
          // fall through
        case 1:
          builder.setName(parts[0]);
          break;
        default:
          throw new IllegalArgumentException("Invalid Job id: " + id);
      }
    }

    if (imageIdentifier != null) {
      builder.setImage(imageIdentifier);
    }

    final String hostname = options.getString(hostnameArg.getDest());
    if (!isNullOrEmpty(hostname)) {
      builder.setHostname(hostname);
    }

    final List<String> command = options.getList(argsArg.getDest());
    if (command != null && !command.isEmpty()) {
      builder.setCommand(command);
    }


    new EnvParser(options.<String>getList(envArg.getDest())).execute(builder);
    new MetaDataParser(options.<String>getList(metadataArg.getDest()), envVarSupplier.get()).execute(builder);
    new PortsParser(options.<String>getList(portArg.getDest())).execute(builder);
    new ServiceRegistrationsParser(options.<String>getList(registrationArg.getDest())).execute(builder);
    new VolumesParser(options.<String>getList(volumeArg.getDest())).execute(builder);

    builder.setRegistrationDomain(options.getString(registrationDomainArg.getDest()));

    // Get grace period interval
    final Integer gracePeriod = options.getInt(gracePeriodArg.getDest());
    if (gracePeriod != null) {
      builder.setGracePeriod(gracePeriod);
    }

    // Parse expires timestamp
    final String expires = options.getString(expiresArg.getDest());
    if (expires != null) {
      // Use DateTime to parse the ISO-8601 string
      builder.setExpires(new DateTime(expires).toDate());
    }

    // Parse health check
    final String execString = options.getString(healthCheckExecArg.getDest());
    final String httpHealthCheck = options.getString(healthCheckHttpArg.getDest());
    final String tcpHealthCheck = options.getString(healthCheckTcpArg.getDest());
    final List<String> execHealthCheck = (execString == null) ? null : Arrays.asList(execString.split(" "));
    new HealthCheckParser(httpHealthCheck, tcpHealthCheck, execHealthCheck).execute(builder);

    final List<String> securityOpt = options.getList(securityOptArg.getDest());
    if (securityOpt != null && !securityOpt.isEmpty()) {
      builder.setSecurityOpt(securityOpt);
    }

    final String networkMode = options.getString(networkModeArg.getDest());
    if (!isNullOrEmpty(networkMode)) {
      builder.setNetworkMode(networkMode);
    }

    final String token = options.getString(tokenArg.getDest());
    if (!isNullOrEmpty(token)) {
      builder.setToken(token);
    }

    new ResourcesParser(options.<String>getList(resourcesArg.getDest())).execute(builder);

    // We build without a hash here because we want the hash to be calculated server-side.
    // This allows different CLI versions to be cross-compatible with different master versions
    // that have either more or fewer job parameters.
    final Job job = builder.buildWithoutHash();

    final Collection<String> errors = JOB_VALIDATOR.validate(job);
    if (!errors.isEmpty()) {
      if (!json) {
        for (String error : errors) {
          out.println(error);
        }
      } else {
        CreateJobResponse createJobResponse = new CreateJobResponse(
            CreateJobResponse.Status.INVALID_JOB_DEFINITION, ImmutableList.copyOf(errors),
            job.getId().toString());
        out.println(createJobResponse.toJsonString());
      }

      return 1;
    }

    if (!quiet && !json) {
      out.println("Creating job: " + job.toJsonString());
    }

    final CreateJobResponse status = client.createJob(job).get();
    if (status.getStatus() == CreateJobResponse.Status.OK) {
      if (!quiet && !json) {
        out.println("Done.");
      }
      if (json) {
        out.println(status.toJsonString());
      } else {
        out.println(status.getId());
      }
      return 0;
    } else {
      if (!quiet && !json) {
        out.println("Failed: " + status);
      } else if (json) {
        out.println(status.toJsonString());
      }
      return 1;
    }
  }

  private Job readConfigurationFromFile(File file) throws IOException {
    if (!file.exists() || !file.isFile() || !file.canRead()) {
      throw new IllegalArgumentException("Cannot read file " + file);
    }
    final byte[] bytes = Files.readAllBytes(file.toPath());
    final String config = new String(bytes, UTF_8);
    return Json.read(config, Job.class);
  }
}


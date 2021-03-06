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

package com.spotify.helios.servicescommon;

import com.google.common.util.concurrent.AbstractIdleService;

import com.spotify.helios.agent.BoundedRandomExponentialBackoff;
import com.spotify.helios.agent.RetryIntervalPolicy;
import com.spotify.helios.agent.RetryScheduler;
import com.spotify.helios.master.HostNotFoundException;
import com.spotify.helios.master.HostStillInUseException;
import com.spotify.helios.servicescommon.coordination.ZooKeeperClient;

import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.framework.state.ConnectionStateListener;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.KeeperException.ConnectionLossException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

import static com.google.common.util.concurrent.Service.State.STOPPING;
import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Common logic to have agents and masters register their "up" nodes in ZK, and to keep trying if
 * ZK is down.
 */
public class ZooKeeperRegistrarService extends AbstractIdleService {

  private static final Logger log = LoggerFactory.getLogger(ZooKeeperRegistrarService.class);

  private final ZooKeeperClient client;

  private final Reactor reactor;
  private final ZooKeeperRegistrar zooKeeperRegistrar;
  private final CountDownLatch zkRegistrationSignal;

  private final RetryIntervalPolicy retryIntervalPolicy;

  private ConnectionStateListener listener = new ConnectionStateListener() {
    @Override
    public void stateChanged(final CuratorFramework client, final ConnectionState newState) {
      if (newState == ConnectionState.RECONNECTED) {
        reactor.signal();
      }
    }
  };

  public ZooKeeperRegistrarService(final ZooKeeperClient client,
                                   final ZooKeeperRegistrar zooKeeperRegistrar) {
    this(client, zooKeeperRegistrar, null,
         BoundedRandomExponentialBackoff.newBuilder()
             .setMinInterval(1, SECONDS)
             .setMaxInterval(30, SECONDS)
             .build());
  }

  public ZooKeeperRegistrarService(final ZooKeeperClient client,
                                   final ZooKeeperRegistrar zooKeeperRegistrar,
                                   final CountDownLatch zkRegistrationSignal) {
    this(client, zooKeeperRegistrar, zkRegistrationSignal,
         BoundedRandomExponentialBackoff.newBuilder()
             .setMinInterval(1, SECONDS)
             .setMaxInterval(30, SECONDS)
             .build());
  }

  public ZooKeeperRegistrarService(final ZooKeeperClient client,
                                   final ZooKeeperRegistrar zooKeeperRegistrar,
                                   final CountDownLatch zkRegistrationSignal,
                                   final RetryIntervalPolicy retryIntervalPolicy) {
    this.client = client;
    this.zooKeeperRegistrar = zooKeeperRegistrar;
    this.zkRegistrationSignal = zkRegistrationSignal;
    this.retryIntervalPolicy = retryIntervalPolicy;
    this.reactor = new DefaultReactor("zk-client-async-init", new Update());
  }

  @Override
  protected void startUp() throws Exception {
    zooKeeperRegistrar.startUp();
    client.getConnectionStateListenable().addListener(listener);
    reactor.startAsync().awaitRunning();
    reactor.signal();
  }

  @Override
  protected void shutDown() throws Exception {
    reactor.stopAsync().awaitTerminated();
    zooKeeperRegistrar.shutDown();
  }

  private class Update implements Reactor.Callback {

    @Override
    public void run(final boolean timeout) throws InterruptedException {
      final RetryScheduler retryScheduler = retryIntervalPolicy.newScheduler();
      while (isAlive()) {
        final long sleep = retryScheduler.nextMillis();

        try {
          zooKeeperRegistrar.tryToRegister(client);
          if (zkRegistrationSignal != null) {
            zkRegistrationSignal.countDown();
          }
          return;
        } catch (KeeperException e) {
          if (e instanceof ConnectionLossException) {
            log.warn("ZooKeeper connection lost, retrying registration in {} ms", sleep);
          } else {
            log.error("ZooKeeper registration failed, retrying in {} ms", sleep, e);
          }
          Thread.sleep(sleep);
        } catch (HostNotFoundException | HostStillInUseException e) {
          log.error("ZooKeeper deregistration of old hostname failed, retrying in {} ms", sleep, e);
        }
      }
    }
  }

  private boolean isAlive() {
    return state().ordinal() < STOPPING.ordinal();
  }
}

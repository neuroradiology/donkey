/*
 * Copyright 2020 AppsFlyer
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.appsflyer.donkey.server;

import com.appsflyer.donkey.server.route.RouteDefinition;
import com.appsflyer.donkey.server.handler.DateHeaderHandler;
import com.appsflyer.donkey.server.handler.ServerHeaderHandler;
import com.appsflyer.donkey.server.exception.ServerInitializationException;
import com.appsflyer.donkey.server.exception.ServerShutdownException;
import io.vertx.core.*;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.LoggerHandler;
import io.vertx.ext.web.handler.ResponseContentTypeHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerImpl implements Server {
  
  private static final Logger logger = LoggerFactory.getLogger(ServerImpl.class.getName());
  private static final int TIMEOUT_SECONDS = 10;
  private final ServerConfig config;
  
  /**
   * Create a new instance with the given {@link ServerConfig}
   *
   * @param config See {@link ServerConfig} for configuration options.
   */
  public static Server create(ServerConfig config) {
    return new ServerImpl(config);
  }
  
  private ServerImpl(ServerConfig config) {
    config.vertx().exceptionHandler(ex -> logger.error(ex.getMessage(), ex.getCause()));
    this.config = config;
    addOptionalHandlers();
  }
  
  private void addOptionalHandlers() {
    Collection<Handler<RoutingContext>> handlers = new ArrayList<>();
    if (config.debug()) {
      handlers.add(LoggerHandler.create());
    }
    if (config.addDateHeader()) {
      handlers.add(DateHeaderHandler.create(config.vertx()));
    }
    if (config.addContentTypeHeader()) {
      handlers.add(ResponseContentTypeHandler.create());
    }
    if (config.addServerHeader()) {
      handlers.add(ServerHeaderHandler.create());
    }
  
    handlers.forEach(h -> config.routeList().addFirst(RouteDefinition.create().handler(h)));
  }
  
  @Override
  public Vertx vertx() {
    return config.vertx();
  }
  
  @Override
  public Future<String> start() {
    Promise<String> promise = Promise.promise();
    var deploymentOptions =
        new DeploymentOptions().setInstances(config.instances());
    config.vertx().deployVerticle(() -> new ServerVerticle(config), deploymentOptions, promise);
  
    return promise.future();
  }
  
  @Override
  public void startSync() throws ServerInitializationException {
    startSync(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
  
  @Override
  public void startSync(int timeout, TimeUnit unit) throws
                                                    ServerInitializationException {
    var latch = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    
    start().onComplete(v -> {
      if (v.failed()) {
        error.set(v.cause());
      }
      latch.countDown();
    });
    
    try {
      if (!latch.await(timeout, unit)) {
        throw new ServerInitializationException(
            String.format("Server start up timed out after %d %s",
                          timeout, unit.name().toLowerCase(Locale.ENGLISH)));
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ServerInitializationException(
          "Thread interrupted during initialization", ex);
    }
    
    if (error.get() != null) {
      throw new ServerInitializationException(error.get());
    }
  }
  
  @Override
  public Future<Void> shutdown() {
    Promise<Void> promise = Promise.promise();
    config.vertx().close(promise);
    return promise.future();
  }
  
  @Override
  public void shutdownSync() throws ServerShutdownException {
    shutdownSync(TIMEOUT_SECONDS, TimeUnit.SECONDS);
  }
  
  @Override
  public void shutdownSync(int timeout, TimeUnit unit) throws
                                                       ServerShutdownException {
    var latch = new CountDownLatch(1);
    AtomicReference<Throwable> error = new AtomicReference<>();
    
    shutdown().onComplete(v -> {
      if (v.failed()) {
        error.set(v.cause());
      }
      latch.countDown();
    });
    
    try {
      if (!latch.await(timeout, unit)) {
        throw new ServerShutdownException(
            String.format("Server shutdown timed out after %d %s",
                          timeout, unit.name().toLowerCase(Locale.ENGLISH)));
      }
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new ServerShutdownException("Thread interrupted during shutdown", ex);
    }
    
    if (error.get() != null) {
      throw new ServerShutdownException(error.get());
    }
  }
}
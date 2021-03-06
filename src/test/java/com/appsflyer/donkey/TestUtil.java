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

package com.appsflyer.donkey;

import clojure.lang.IPersistentMap;
import com.appsflyer.donkey.server.ServerConfig;
import com.appsflyer.donkey.server.ServerConfig.ServerConfigBuilder;
import com.appsflyer.donkey.server.route.AbstractRouteCreator;
import com.appsflyer.donkey.server.route.RouteCreator;
import com.appsflyer.donkey.server.route.RouteDefinition;
import com.appsflyer.donkey.server.route.RouteList;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;

import java.util.concurrent.TimeUnit;

import static com.appsflyer.donkey.client.ring.RingResponseField.BODY;
import static com.appsflyer.donkey.client.ring.RingResponseField.STATUS;
import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.vertx.core.http.HttpMethod.GET;
import static io.vertx.core.http.HttpMethod.POST;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestUtil {
  
  public static final int DEFAULT_PORT = 16969;
  
  private TestUtil() {}
  
  public static SocketAddress getDefaultAddress() {
    return SocketAddress.inetSocketAddress(DEFAULT_PORT, "localhost");
  }
  
  public static RequestOptions getRequestOptions(String uri) {
    return new RequestOptions()
        .setHost(getDefaultAddress().host())
        .setPort(DEFAULT_PORT)
        .setURI(uri);
  }
  
  public static Object parseResponseBody(HttpResponse<Buffer> response) {
    return ClojureObjectMapper.deserialize(response.bodyAsString());
  }
  
  public static Object parseResponseBody(IPersistentMap response) {
    return ClojureObjectMapper.deserialize((byte[]) response.valAt(BODY.keyword()));
  }
  
  public static void assert200(HttpResponse<Buffer> response) {
    assertEquals(OK.code(), response.statusCode());
  }
  
  public static void assert200(IPersistentMap response) {
    assertEquals(OK.code(), response.valAt(STATUS.keyword()));
  }
  
  public static void assert404(HttpResponse<Buffer> response) {
    assertEquals(NOT_FOUND.code(), response.statusCode(),
                 "It should respond with Not Found");
  }
  
  public static void assert405(HttpResponse<Buffer> response) {
    assertEquals(METHOD_NOT_ALLOWED.code(), response.statusCode(),
                 "It should respond with Method Not Allowed");
  }
  
  public static void assert406(HttpResponse<Buffer> response) {
    assertEquals(NOT_ACCEPTABLE.code(), response.statusCode(),
                 "It should respond with Not Acceptable");
  }
  
  public static void assert415(HttpResponse<Buffer> response) {
    assertEquals(UNSUPPORTED_MEDIA_TYPE.code(), response.statusCode(),
                 "It should respond with Unsupported Media Type");
  }
  
  public static void assert500(HttpResponse<Buffer> response) {
    assertEquals(INTERNAL_SERVER_ERROR.code(), response.statusCode());
  }
  
  public static Future<HttpResponse<Buffer>> doGet(Vertx vertx, String uri) {
    return makeRequest(WebClient.create(vertx), GET, uri);
  }
  
  public static Future<HttpResponse<Buffer>> doGet(WebClient client, String uri) {
    return makeRequest(client, GET, uri);
  }
  
  public static Future<HttpResponse<Buffer>> doPost(WebClient client, String uri) {
    return makeRequest(client, POST, uri);
  }
  
  private static Future<HttpResponse<Buffer>> makeRequest(
      WebClient client, HttpMethod method, String uri) {
  
    return client.request(method, getDefaultAddress(), uri)
                 .send();
  }
  
  public static ServerConfigBuilder getDefaultConfigBuilder(Vertx vertx) {
    return ServerConfig.builder()
                       .vertx(vertx)
                       .instances(1)
                       .serverOptions(new HttpServerOptions().setPort(DEFAULT_PORT))
                       .routeList(defaultRouteList())
                       .routeCreatorFactory(TestUtil::newRouteCreator);
  }
  
  private static RouteList defaultRouteList() {
    return RouteList.from(RouteDefinition.create().handler(ctx -> ctx.response().end()));
  }
  
  public static RouteCreator newRouteCreator(Router router, RouteList routeList) {
    return new AbstractRouteCreator(router, routeList) {
      @Override
      protected void buildRoute(Route route, RouteDefinition rd) {
        setPath(route, rd);
        addBodyHandler(route);
        addHandler(route, rd.handler(), rd.handlerMode());
      }
    };
  }
  
  public static void assertContextSuccess(VertxTestContext testContext) throws
                                                                        Throwable {
    assertContextSuccess(testContext, 5, TimeUnit.SECONDS);
  }
  
  public static void assertContextSuccess(
      VertxTestContext testContext, long amount, TimeUnit unit) throws
                                                                Throwable {
    assertTrue(testContext.awaitCompletion(amount, unit));
    if (testContext.failed()) {
      throw testContext.causeOfFailure();
    }
  }
}

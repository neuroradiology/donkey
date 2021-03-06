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

package com.appsflyer.donkey.server.ring.handler;

import clojure.lang.IPersistentMap;
import clojure.lang.Keyword;
import clojure.lang.RT;
import com.appsflyer.donkey.ValueExtractor;
import com.appsflyer.donkey.util.TypeConverter;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import org.jetbrains.annotations.Nullable;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.security.cert.Certificate;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

import static com.appsflyer.donkey.util.TypeConverter.toPersistentMap;
import static com.appsflyer.donkey.util.TypeConverter.toUrlDecodedPersistentMap;

/**
 * The class encapsulates the logic of translating between a Vertx {@link RoutingContext}
 * and a Ring request.
 * Each element corresponds to a Ring request field. It implements getting the field's
 * name as a Keyword, and extracting the corresponding value from the RoutingContext.
 */
public enum RingRequestField implements ValueExtractor<RoutingContext> {
  
  BODY("body") {
    @Nullable
    @Override
    public byte[] from(RoutingContext ctx) {
      Buffer body = ctx.getBody();
      if (body != null) {
        return body.getBytes();
      }
      return null;
    }
  },
  CLIENT_CERT("ssl-client-cert") {
    @Nullable
    @Override
    public Certificate[] from(RoutingContext ctx) {
      try {
        if (ctx.request().isSSL()) {
          return ctx.request().sslSession().getPeerCertificates();
        }
      } catch (SSLPeerUnverifiedException ignore) {
        //@todo - What should we do here?
      }
      return null;
    }
  },
  FORM_PARAMS("form-params") {
    @Nullable
    @Override
    public IPersistentMap from(RoutingContext ctx) {
      if (!ctx.request().isExpectMultipart()) {
        return null;
      }
      
      MultiMap formAttributes = ctx.request().formAttributes();
      if (formAttributes.isEmpty()) {
        return null;
      }
      
      return toUrlDecodedPersistentMap(formAttributes);
    }
  },
  HEADERS("headers") {
    private final Function<String, String> keyTransformer =
        (v) -> v.toLowerCase(Locale.ROOT);
    
    @Nullable
    @Override
    public IPersistentMap from(RoutingContext ctx) {
      MultiMap headers = ctx.request().headers();
      if (headers.isEmpty()) {
        return null;
      }
      return toPersistentMap(headers, keyTransformer, TypeConverter::stringJoiner);
    }
  },
  PATH_PARAMS("path-params") {
    @Nullable
    @Override
    public IPersistentMap from(RoutingContext ctx) {
      Map<String, String> pathParams = ctx.pathParams();
      if (pathParams.isEmpty()) {
        return null;
      }
      
      Object[] pathParamsArray = new Object[(pathParams.size() * 2)];
      int i = 0;
      for (Object obj : pathParams.entrySet()) {
        pathParamsArray[i] = ((Map.Entry<?, ?>) obj).getKey();
        pathParamsArray[i + 1] = ((Map.Entry<?, ?>) obj).getValue();
        i += 2;
      }
      return RT.mapUniqueKeys(pathParamsArray);
    }
  },
  PROTOCOL("protocol") {
    @Override
    public String from(RoutingContext ctx) {
      return HttpProtocolMapping.get(ctx.request().version());
    }
  },
  QUERY_STRING("query-string") {
    @Override
    public String from(RoutingContext ctx) {
      return ctx.request().query();
    }
  },
  REMOTE_ADDRESS("remote-addr") {
    @Nullable
    @Override
    public String from(RoutingContext ctx) {
      var forwardedFor = ctx.request().getHeader("x-forwarded-for");
      if (forwardedFor != null) {
        return forwardedFor;
      } else {
        SocketAddress remoteAddress = ctx.request().remoteAddress();
        if (remoteAddress != null) {
          return remoteAddress.toString();
        }
      }
      return null;
    }
  },
  /**
   * The request "verb", i.e GET, POST,
   */
  REQUEST_METHOD("request-method") {
    @Override
    public Keyword from(RoutingContext ctx) {
      return HttpMethodMapping.get(ctx.request().method());
    }
  },
  /**
   * The request scheme - HTTP or HTTPS
   */
  SCHEME("scheme") {
    private final Map<String, Keyword> schemeMapping =
        Map.of("http", Keyword.intern("http"),
               "https", Keyword.intern("https"));
    
    @Override
    public Keyword from(RoutingContext ctx) {
      return schemeMapping.get(ctx.request().scheme());
    }
  },
  /**
   * The hostname of the server, or the ip if a hostname cannot be determined.
   */
  SERVER_NAME("server-name") {
    @Override
    public String from(RoutingContext ctx) {
      return ctx.request().host();
    }
  },
  /**
   * The port on which the server is listening on
   */
  SERVER_PORT("server-port") {
    @Override
    public Integer from(RoutingContext ctx) {
      return ctx.request().localAddress().port();
    }
  },
  /**
   * The path part of the url.<br>
   * Example:<br>
   * {@code http://www.example.com/foo/bar => /foo/bar}
   */
  URI("uri") {
    @Override
    public String from(RoutingContext ctx) {
      return ctx.request().path();
    }
  };
  
  private final Keyword keyword;
  
  RingRequestField(String field) {
    keyword = Keyword.intern(field);
  }
  
  /**
   * @return The field name as a Keyword
   */
  public Keyword keyword() {
    return keyword;
  }
}

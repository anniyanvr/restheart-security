/*
 * RESTHeart Security
 * 
 * Copyright (C) SoftInstigate Srl
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restheart.security.plugins.services;

import com.google.common.escape.Escaper;
import com.google.common.net.UrlEscapers;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.restheart.security.handlers.PipedHttpHandler;
import org.restheart.security.plugins.PluginsRegistry;
import static org.restheart.security.plugins.TokenManager.AUTH_TOKEN_HEADER;
import static org.restheart.security.plugins.TokenManager.AUTH_TOKEN_LOCATION_HEADER;
import static org.restheart.security.plugins.TokenManager.AUTH_TOKEN_VALID_HEADER;
import org.restheart.security.plugins.authenticators.BaseAccount;
import org.restheart.security.plugins.tokens.RndTokenManager;
import org.restheart.security.plugins.Service;
import org.restheart.security.utils.HttpStatus;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import org.restheart.security.ConfigurationException;

/**
 * allows to get and invalidate the user auth token generated by RndTokenManager
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class RndTokenService extends Service {
    // used to compare the requested URI containing escaped chars
    private static final Escaper ESCAPER = UrlEscapers.urlPathSegmentEscaper();

    private final RndTokenManager tokenManager;

    /**
     *
     * @param next
     * @param name
     * @param uri
     * @param secured
     * @throws java.lang.Exception
     */
    public RndTokenService(PipedHttpHandler next,
            String name,
            String uri,
            Boolean secured)
            throws Exception {
        super(next, name, uri, secured, null);

        try {
            this.tokenManager = (RndTokenManager) PluginsRegistry
                    .getInstance()
                    .getTokenManager();
        } catch (ClassCastException cce) {
            throw new ConfigurationException("Error configuring RndTokenManager, "
                    + "it only works when using token manager "
                    + RndTokenManager.class.getName(), cce);
        }
    }

    /**
     *
     * @param exchange
     * @throws Exception
     */
    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.getRequestPath().startsWith(getUri())
                && exchange.getRequestPath().length() >= (getUri().length() + 2)
                && Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().put(
                    HttpString.tryFromString("Access-Control-Allow-Methods"), "GET, DELETE")
                    .put(HttpString.tryFromString("Access-Control-Allow-Headers"),
                            "Accept, Accept-Encoding, Authorization, Content-Length, "
                            + "Content-Type, Host, Origin, X-Requested-With, "
                            + "User-Agent, No-Auth-Challenge");

            exchange.setStatusCode(HttpStatus.SC_OK);
            exchange.endExchange();
            return;
        }

        if (exchange.getSecurityContext() == null
                || exchange.getSecurityContext().getAuthenticatedAccount() == null
                || exchange.getSecurityContext().getAuthenticatedAccount()
                        .getPrincipal() == null) {
            exchange.setStatusCode(HttpStatus.SC_UNAUTHORIZED);
            exchange.endExchange();
            return;
        }

        if (!((getUri() + "/" + exchange.getSecurityContext()
                .getAuthenticatedAccount().getPrincipal().getName())
                .equals(exchange.getRequestURI()))
                && !(ESCAPER.escape(getUri() + "/" + exchange.getSecurityContext()
                        .getAuthenticatedAccount().getPrincipal().getName()))
                        .equals(exchange.getRequestURI())) {
            exchange.setStatusCode(HttpStatus.SC_FORBIDDEN);
            exchange.endExchange();
            return;
        }

        if (Methods.GET.equals(exchange.getRequestMethod())) {
            JsonObject resp = new JsonObject();

            resp.add("auth_token", new JsonPrimitive(exchange.getResponseHeaders()
                    .get(AUTH_TOKEN_HEADER).getFirst()));

            resp.add("auth_token_valid_until",
                    new JsonPrimitive(exchange.getResponseHeaders()
                            .get(AUTH_TOKEN_VALID_HEADER).getFirst()));

            exchange.setStatusCode(HttpStatus.SC_OK);
            // TODO use static var for content type
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            exchange.getResponseSender().send(resp.toString());
            exchange.endExchange();
        } else if (Methods.DELETE.equals(exchange.getRequestMethod())) {
            BaseAccount account = new BaseAccount(exchange.getSecurityContext()
                    .getAuthenticatedAccount().getPrincipal().getName(),
                    null);

            tokenManager.invalidate(account);

            removeAuthTokens(exchange);
            exchange.setStatusCode(HttpStatus.SC_NO_CONTENT);
            exchange.endExchange();
        } else {
            exchange.setStatusCode(HttpStatus.SC_METHOD_NOT_ALLOWED);
            exchange.endExchange();
        }
    }

    private void removeAuthTokens(HttpServerExchange exchange) {
        exchange.getResponseHeaders().remove(AUTH_TOKEN_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_VALID_HEADER);
        exchange.getResponseHeaders().remove(AUTH_TOKEN_LOCATION_HEADER);
    }
}

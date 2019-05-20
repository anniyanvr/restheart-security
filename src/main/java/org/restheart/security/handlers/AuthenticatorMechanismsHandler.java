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
package org.restheart.security.handlers;

import java.util.List;

import io.undertow.security.api.SecurityContext;
import io.undertow.server.HttpServerExchange;
import org.restheart.security.plugins.AuthMechanism;

/**
 * This is the PipedHttpHandler version of
 * io.undertow.security.handlers.AuthenticationMechanismsHandler that adds one
 * or more authenticator mechanisms to the security context
 *
 * @author Andrea Di Cesare {@literal <andrea@softinstigate.com>}
 */
public class AuthenticatorMechanismsHandler extends PipedHttpHandler {

    private final List<AuthMechanism> authenticatorMechanisms;

    public AuthenticatorMechanismsHandler(final PipedHttpHandler next,
            final List<AuthMechanism> authenticatorMechanisms) {
        super(next);
        this.authenticatorMechanisms = authenticatorMechanisms;
    }

    public AuthenticatorMechanismsHandler(
            final List<AuthMechanism> authenticatorHandlers) {
        this.authenticatorMechanisms = authenticatorHandlers;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        final SecurityContext sc = exchange.getSecurityContext();

        if (sc != null) {
            authenticatorMechanisms.forEach((mechanism) -> {
                sc.addAuthenticationMechanism(
                        new AuthenticatorMechanismWrapper(mechanism));
            });
        }

        next(exchange);
    }
}
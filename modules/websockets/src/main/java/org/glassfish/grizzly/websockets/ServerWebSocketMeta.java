/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly.websockets;

import java.net.URI;
import java.util.Arrays;

/**
 * Server-side {@link WebSocket} meta information.
 *
 * @see WebSocketMeta
 * @see ClientWebSocketMeta
 *
 * @author Alexey Stashok
 */
public class ServerWebSocketMeta extends WebSocketMeta {

    private final String location;
    private final byte[] key;
    
    /**
     * Construct a server-side <tt>WebSocketMeta</tt> using {@link URI} and security key.
     *
     * @param uri {@link WebSocket} {@link URI}
     * @param origin the {@link WebSocket} "Sec-WebSocket-Origin" header value
     * @param location the {@link WebSocket} "Sec-WebSocket-Location" header value
     * @param protocol the {@link WebSocket} "Sec-WebSocket-Protocol" header value
     * @param key the {@link WebSocket} key value transferred as a part of response payload.
     * @param isSecure <tt>true</tt>, if the {@link WebSocket} will be used in the secured mode,
     *                 or <tt>false</tt> otherwise. It's possible to pass <tt>null</tt>, in this
     *                 case {@link WebSocket} will try to autodetect security basing on passed uri parameter.
     */
    public ServerWebSocketMeta(URI uri, String origin,
            String location, String protocol, byte[] key, Boolean isSecure) {
        super(uri, origin, protocol, isSecure);
        this.key = key;
        this.location = location;
    }

    /**
     * Get the {@link WebSocket} key value transferred as a part of response payload.
     * 
     * @return the {@link WebSocket} key value transferred as a part of response payload.
     */
    public byte[] getKey() {
        return key;
    }

    /**
     * Get the {@link WebSocket} "Sec-WebSocket-Location" header value
     * @return the {@link WebSocket} "Sec-WebSocket-Location" header value
     */
    public String getLocation() {
        return location;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(256);
        sb.append(super.toString())
                .append(" location=").append(location)
                .append(" key=").append(Arrays.toString(key));

        return sb.toString();
    }
}
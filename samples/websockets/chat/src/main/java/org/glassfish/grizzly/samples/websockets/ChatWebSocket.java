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

package org.glassfish.grizzly.samples.websockets;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.websockets.WebSocketBase;
import org.glassfish.grizzly.websockets.WebSocketHandler;
import org.glassfish.grizzly.websockets.WebSocketMeta;
import org.glassfish.grizzly.websockets.frame.Frame;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Customize {@link org.glassfish.grizzly.websockets.WebSocket} implementation, which contains chat application
 * specific properties and logic.
 * 
 * @author Alexey Stashok
 * @author Justin Lee
 */
public class ChatWebSocket extends WebSocketBase {
    private static final Logger logger = Grizzly.logger(ChatWebSocket.class);
    
    // chat user name
    private volatile String user;

    public ChatWebSocket(Connection connection, WebSocketMeta meta,
            WebSocketHandler handler) {
        super(connection, meta, handler);
    }

    /**
     * Get the user name
     * @return the user name
     */
    public String getUser() {
        return user;
    }

    /**
     * Set the user name
     * @param user the user name
     */
    public void setUser(String user) {
        this.user = user;
    }

    /**
     * Send the message in JSON encoding acceptable by browser's javascript.
     *
     * @param user the user name
     * @param text the text message
     */
    public void sendJson(String user, String text) {
        try {
            final String msg = toJsonp(user, text);
            send(Frame.createTextFrame(msg));
        } catch (IOException e) {
            logger.log(Level.SEVERE, "Removing chat client: " + e.getMessage(), e);
            try {
                close();
            } catch (IOException ee) {
            }
        }
    }

    private String toJsonp(String name, String message) {
        return "window.parent.app.update({ name: \"" + escape(name) +
                "\", message: \"" + escape(message) + "\" });\n";
    }

    private String escape(String orig) {
        StringBuilder buffer = new StringBuilder(orig.length());

        for (int i = 0; i < orig.length(); i++) {
            char c = orig.charAt(i);
            switch (c) {
                case '\b':
                    buffer.append("\\b");
                    break;
                case '\f':
                    buffer.append("\\f");
                    break;
                case '\n':
                    buffer.append("<br />");
                    break;
                case '\r':
                    // ignore
                    break;
                case '\t':
                    buffer.append("\\t");
                    break;
                case '\'':
                    buffer.append("\\'");
                    break;
                case '\"':
                    buffer.append("\\\"");
                    break;
                case '\\':
                    buffer.append("\\\\");
                    break;
                case '<':
                    buffer.append("&lt;");
                    break;
                case '>':
                    buffer.append("&gt;");
                    break;
                case '&':
                    buffer.append("&amp;");
                    break;
                default:
                    buffer.append(c);
            }
        }

        return buffer.toString();
    }
}
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http;

import java.nio.charset.Charset;

/**
 * Constants.
 *
 * @author Remy Maucherat
 */
public class Constants {
    protected static final Charset ASCII_CHARSET = Charset.forName("ASCII");


    /**
     * CR.
     */
    public static final byte CR = (byte) '\r';


    /**
     * LF.
     */
    public static final byte LF = (byte) '\n';


    /**
     * SP.
     */
    public static final byte SP = (byte) ' ';


    /**
     * HT.
     */
    public static final byte HT = (byte) '\t';


    /**
     * COMMA.
     */
    public static final byte COMMA = (byte) ',';


    /**
     * COLON.
     */
    public static final byte COLON = (byte) ':';


    /**
     * SEMI_COLON.
     */
    public static final byte SEMI_COLON = (byte) ';';


    /**
     * 'A'.
     */
    public static final byte A = (byte) 'A';


    /**
     * 'a'.
     */
    public static final byte a = (byte) 'a';


    /**
     * 'Z'.
     */
    public static final byte Z = (byte) 'Z';


    /**
     * '?'.
     */
    public static final byte QUESTION = (byte) '?';


    /**
     * Lower case offset.
     */
    public static final byte LC_OFFSET = A - a;


    /**
     * Default HTTP header buffer size.
     */
    public static final int DEFAULT_HTTP_HEADER_BUFFER_SIZE = 48 * 1024;


    /**
     * CRLF.
     */
    public static final String CRLF = "\r\n";


    /**
     * CRLF bytes.
     */
    public static final byte[] CRLF_BYTES = {(byte) '\r', (byte) '\n'};


    /**
     * Colon bytes.
     */
    public static final byte[] COLON_BYTES = {(byte) ':', (byte) ' '};


    /**
     * Close bytes.
     */
    public static final byte[] CLOSE_BYTES = {
        (byte) 'c',
        (byte) 'l',
        (byte) 'o',
        (byte) 's',
        (byte) 'e'
    };


    /**
     * Keep-alive bytes.
     */
    public static final byte[] KEEPALIVE_BYTES = {
        (byte) 'k',
        (byte) 'e',
        (byte) 'e',
        (byte) 'p',
        (byte) '-',
        (byte) 'a',
        (byte) 'l',
        (byte) 'i',
        (byte) 'v',
        (byte) 'e'
    };

    /**
     * HTTP/1.0.
     */
    public static final String HTTP_10 = "HTTP/1.0";


    /**
     * HTTP/1.1.
     */
    public static final String HTTP_11 = "HTTP/1.1";


    /**
     * GET.
     */
    public static final String GET = "GET";


    /**
     * HEAD.
     */
    public static final String HEAD = "HEAD";


    /**
     * POST.
     */
    public static final String POST = "POST";


    /**
     * Ack string when pipelining HTTP requests.
     */
    public static final byte[] ACK_BYTES = {
        (byte) 'H',
        (byte) 'T',
        (byte) 'T',
        (byte) 'P',
        (byte) '/',
        (byte) '1',
        (byte) '.',
        (byte) '1',
        (byte) ' ',
        (byte) '1',
        (byte) '0',
        (byte) '0',
        (byte) ' ',
        (byte) 'C',
        (byte) 'o',
        (byte) 'n',
        (byte) 't',
        (byte) 'i',
        (byte) 'n',
        (byte) 'u',
        (byte) 'e',
        (byte) '\r',
        (byte) '\n',
        (byte) '\r',
        (byte) '\n'
    };

    public static final int PROCESSOR_IDLE = 0;
    public static final int PROCESSOR_ACTIVE = 1;

    /**
     * Default header names.
     */
    public static final String AUTHORIZATION_HEADER = "authorization";

    /**
     * SSL Certificate Request Attributite.
     */
    public static final String SSL_CERTIFICATE_ATTR = "org.apache.coyote.request.X509Certificate";

    /**
     * Security flag.
     */
    public static final boolean SECURITY =
        (System.getSecurityManager() != null);


    // S1AS 4703023
    public static final int DEFAULT_MAX_DISPATCH_DEPTH = 20;


    // START SJSAS 6328909
    /**
     * The default response-type
     */
    public final static String DEFAULT_RESPONSE_TYPE =
            "text/html; charset=iso-8859-1";


    /**
     * The forced response-type
     */
    public final static String FORCED_RESPONSE_TYPE =
           "text/html; charset=iso-8859-1";
    // END SJSAS 6328909


    // START SJSAS 6337561
    public final static String PROXY_JROUTE = "proxy-jroute";
    // END SJSAS 6337561

    // START SJSAS 6346226
    public final static String JROUTE_COOKIE = "JROUTE";
    // END SJSAS 6346226

    public final static String CONTENT_LENGTH_HEADER = "content-length";

    public final static String CONTENT_TYPE_HEADER = "content-type";

    public final static byte[] CONTENT_LENGTH_HEADER_BYTES = CONTENT_LENGTH_HEADER.getBytes(ASCII_CHARSET);

    public final static String TRANSFER_ENCODING_HEADER = "transfer-encoding";

    public final static byte[] TRANSFER_ENCODING_HEADER_BYTES = TRANSFER_ENCODING_HEADER.getBytes(ASCII_CHARSET);

    public final static String CHUNKED_ENCODING = "chunked";

    public final static byte[] CHUNKED_ENCODING_BYTES = CHUNKED_ENCODING.getBytes(ASCII_CHARSET);
    
    public final static String UPGRADE_HEADER = "upgrade";

    public final static byte[] UPGRADE_HEADER_BYTES = UPGRADE_HEADER.getBytes(ASCII_CHARSET);

    public final static byte[] LAST_CHUNK_CRLF_BYTES = "0\r\n".getBytes(ASCII_CHARSET);

    public final static String CONTENT_ENCODING_HEADER = "content-encoding";

    // TODO Grizzly 2.0, by default, parsed the request URI using UTF-8.
    // We should probably do so with query parameters
    public static final String DEFAULT_CHARACTER_ENCODING="ISO-8859-1";

    public static final String EXPECT_100_CONTINUE_NAME = "expect";

    public static final byte[] EXPECT_100_CONTINUE_NAME_BYTES = EXPECT_100_CONTINUE_NAME.getBytes(ASCII_CHARSET);

    public static final String EXPECT_100_CONTINUE_VALUE = "100-Continue";

    public static final byte[] EXPECT_100_CONTINUE_VALUE_BYTES = EXPECT_100_CONTINUE_VALUE.getBytes(ASCII_CHARSET);

    public static final int KEEP_ALIVE_TIMEOUT_IN_SECONDS = 30;
    /**
     * Default max keep-alive count.
     */
    public final static int DEFAULT_MAX_KEEP_ALIVE = 256;
}
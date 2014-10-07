/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.compression.deflate.DeflateDecoder;
import org.glassfish.grizzly.compression.deflate.DeflateEncoder;
import org.glassfish.grizzly.compression.deflate.DeflateFilter;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.impl.SafeFutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferManager;
import org.glassfish.grizzly.memory.HeapMemoryManager;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.nio.transport.TCPNIOConnectorHandler;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.ChunkingFilter;
import org.glassfish.grizzly.utils.DelayFilter;
import org.glassfish.grizzly.utils.EchoFilter;
import org.glassfish.grizzly.utils.StringFilter;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Random;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Test set for {@link org.glassfish.grizzly.compression.deflate.DeflateFilter}.
 *
 * @author Gerd Riesselmann
 */
@RunWith(Parameterized.class)
public class DeflateTest {
    private static final int PORT = 7786;
    private final MemoryManager manager;

    public DeflateTest(MemoryManager manager) {
        this.manager = manager;
    }

    @Parameterized.Parameters
    public static Collection<Object[]> getLazySslInit() {
        return Arrays.asList(new Object[][]{
                {new HeapMemoryManager()},
                {new ByteBufferManager()},
        });
    }

    @Test
    public void testEncodingAndDecoding() {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        final String test = "Deflated hello. Works?";
        final Buffer buffer = Buffers.wrap(mm, test);
        final AttributeStorage storage = new AttributeStorage() {
            final AttributeHolder holder = AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();
            @Override
            public AttributeHolder getAttributes() {
                return holder;
            }
        };

        final DeflateEncoder enc = new DeflateEncoder();
        final DeflateDecoder dec = new DeflateDecoder();

        // Encode
        final TransformationResult<Buffer, Buffer> encoded = enc.transform(storage, buffer);
        final Buffer readyBuffer = encoded.getMessage();
        final Buffer finishBuffer = enc.finish(storage);

        final ByteBuffer encodedBuffer = Buffers.appendBuffers(mm, readyBuffer, finishBuffer).toByteBuffer();

        // Decode
        final Buffer bufferToBeDecoded = Buffers.wrap(mm, encodedBuffer);
        final TransformationResult<Buffer, Buffer> decoded = dec.transform(storage, bufferToBeDecoded);
        final Buffer decodedBuffer = decoded.getMessage();

        assertEquals(test, decodedBuffer.toStringContent());
    }

    @Test
    public void testDecoding() throws Throwable {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        final String test = "Deflated hello. Works?";


        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream go = new DeflaterOutputStream(baos);
        go.write(test.getBytes());
        go.finish();
        go.close();

        byte[] deflatedContent = baos.toByteArray();

        final Buffer encodedBuffer = Buffers.wrap(mm, deflatedContent);
        final AttributeStorage storage = new AttributeStorage() {
            final AttributeHolder holder = AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();
            @Override
            public AttributeHolder getAttributes() {
                return holder;
            }
        };

        final DeflateDecoder dec = new DeflateDecoder();

        // Decode
        final TransformationResult<Buffer, Buffer> decoded = dec.transform(storage, encodedBuffer);
        final Buffer decodedBuffer = decoded.getMessage();

        assertEquals(test, decodedBuffer.toStringContent());
    }

    @Test
    public void testEncoding() throws Throwable {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        final String test = "Deflated hello. Works?";
        final Buffer buffer = Buffers.wrap(mm, test);
        final AttributeStorage storage = new AttributeStorage() {
            final AttributeHolder holder = AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();
            @Override
            public AttributeHolder getAttributes() {
                return holder;
            }
        };

        final DeflateEncoder enc = new DeflateEncoder();

        // Encode
        final TransformationResult<Buffer, Buffer> encoded = enc.transform(storage, buffer);
        final Buffer readyBuffer = encoded.getMessage();
        final Buffer finishBuffer = enc.finish(storage);

        final ByteBuffer encodedBuffer = Buffers.appendBuffers(mm, readyBuffer, finishBuffer).toByteBuffer();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InflaterOutputStream go = new InflaterOutputStream(baos);
        go.write(encodedBuffer.array());
        go.finish();
        go.close();

        assertEquals(test, baos.toString());
    }

    @Test
    public void testEncodingVsDeflaterOutputStream() throws Throwable {
        final MemoryManager mm = MemoryManager.DEFAULT_MEMORY_MANAGER;
        final String test = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        final Buffer buffer = Buffers.wrap(mm, test);
        final AttributeStorage storage = new AttributeStorage() {
            final AttributeHolder holder = AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createSafeAttributeHolder();
            @Override
            public AttributeHolder getAttributes() {
                return holder;
            }
        };

        // Encode with Encoder
        final DeflateEncoder enc = new DeflateEncoder();
        final TransformationResult<Buffer, Buffer> encoded = enc.transform(storage, buffer);
        final Buffer readyBuffer = encoded.getMessage();
        final Buffer finishBuffer = enc.finish(storage);

        final ByteBuffer encodedBuffer = Buffers.appendBuffers(mm, readyBuffer, finishBuffer).toByteBuffer();

        // Encode with DeflaterOutputStream
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DeflaterOutputStream go = new DeflaterOutputStream(baos);
        go.write(test.getBytes());
        go.finish();
        go.close();

        assertArrayEquals(encodedBuffer.array(), baos.toByteArray());
    }


    @Test
    public void testSimpleEcho() throws Exception {
        doTest("Hello world");
    }

    @Test
    public void test10Echoes() throws Exception {
        String[] array = new String[10];
        for (int i = 0; i < array.length; i++) {
            array[i] = "abcdefghijklmnop#" + i;
        }

        doTest(array);
    }

    @Test
    public void testLargeEcho() throws Exception {
        final int len = 1024 * 256;
        StringBuilder sb = new StringBuilder(len);
        String a = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890";
        int totalLen = a.length() - 1;
        Random r = new Random(System.currentTimeMillis());
        for (int i = 0; i < len; i++) {
            sb.append(a.charAt(r.nextInt(totalLen)));
        }
        doTest(sb.toString());
    }

    @Test
    public void testChunkedEcho() throws Exception {
        doTest(true, "Hello world");
    }

    @Test
    public void testChunked10Echoes() throws Exception {
        String[] array = new String[10];
        for (int i = 0; i < array.length; i++) {
            array[i] = "Hello world #" + i;
        }

        doTest(true, array);
    }

    void doTest(String... messages) throws Exception {
        doTest(false, messages);
    }

    void doTest(boolean applyChunking, String... messages) throws Exception {
        Connection connection = null;

        FilterChainBuilder serverChainBuilder = FilterChainBuilder.stateless();
        serverChainBuilder.add(new TransportFilter());
        if (applyChunking) {
            serverChainBuilder.add(new ChunkingFilter(2));
            serverChainBuilder.add(new DelayFilter(50, 50));
        }
        
        serverChainBuilder.add(new DeflateFilter());
        serverChainBuilder.add(new StringFilter());
        serverChainBuilder.add(new EchoFilter());

        TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(serverChainBuilder.build());
        transport.setMemoryManager(manager);
        transport.getAsyncQueueIO().getWriter().setMaxPendingBytesPerConnection(-1);

        try {
            transport.bind(PORT);
            transport.start();

            FutureImpl<Boolean> completeFuture = SafeFutureImpl.create();
            FilterChainBuilder clientChainBuilder = FilterChainBuilder.stateless();
            clientChainBuilder.add(new TransportFilter());
            clientChainBuilder.add(new DeflateFilter());
            clientChainBuilder.add(new StringFilter());
            clientChainBuilder.add(new ClientEchoCheckFilter(completeFuture, messages));

            SocketConnectorHandler connectorHandler = TCPNIOConnectorHandler.builder(transport)
                .processor(clientChainBuilder.build()).build();
            
            Future<Connection> future = connectorHandler.connect("localhost", PORT);

            connection = future.get(10, TimeUnit.SECONDS);
            assertTrue(connection != null);

            assertTrue(completeFuture.get(120, TimeUnit.SECONDS));
        } finally {
            if (connection != null) {
                connection.closeSilently();
            }

            transport.shutdownNow();
        }
    }

    private static final class ClientEchoCheckFilter extends BaseFilter {
        private final String[] messages;
        private final FutureImpl<Boolean> future;

        private final AtomicInteger idx = new AtomicInteger();

        public ClientEchoCheckFilter(FutureImpl<Boolean> future, String... messages) {
            this.messages = messages;
            this.future = future;
        }

        @Override
        public NextAction handleConnect(FilterChainContext ctx) throws IOException {
            ctx.write(messages[idx.get()]);
            return ctx.getStopAction();
        }

        @Override
        public NextAction handleRead(FilterChainContext ctx) throws IOException {
            final String echoedMessage = (String) ctx.getMessage();
            final int currentIdx = idx.getAndIncrement();
            final String messageToCompare = messages[currentIdx];
            if (messageToCompare.equals(echoedMessage)) {
                final int nextIdx = currentIdx + 1;
                if (nextIdx >= messages.length) {
                    future.result(true);
                } else {
                    ctx.write(messages[nextIdx]);
                }
            } else {
                future.failure(new IllegalStateException("Message #" +
                        currentIdx + " is incorrect. Expected: " +
                        messageToCompare + " received: " + echoedMessage));
            }

            return ctx.getStopAction();
        }

        @Override
        public void exceptionOccurred(FilterChainContext ctx, Throwable error) {
            if (!future.isDone()) {
                future.failure(error);
            }
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx) throws IOException {
            if (!future.isDone()) {
                future.failure(new EOFException("handleClose was called"));
            }

            return ctx.getStopAction();
        }
    }
}

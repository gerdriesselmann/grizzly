/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import java.io.IOException;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.HttpServerFilter;
import org.glassfish.grizzly.http.Note;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DataStructures;

/**
 * Filter is working as Codec between Ajp and Http packets.
 * In other words it's responsible for decoding Ajp message to HttpRequestPacket,
 * and encoding HttpResponsePacket to Ajp message back.
 *
 * @author Alexey Stashok
 */
public class AjpHandlerFilter extends BaseFilter {
    private static final Logger LOGGER =
            Grizzly.logger(AjpHandlerFilter.class);

    private final Attribute<HttpRequestPacketImpl> httpRequestInProcessAttr =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
            HttpServerFilter.HTTP_SERVER_REQUEST_ATTR_NAME);
    
    private String requiredSecret;

    private final Buffer NEED_MORE_DATA_MESSAGE = Buffers.cloneBuffer(
            Buffers.EMPTY_BUFFER);

    public static final Note<DataChunk> SSL_CERT_NOTE = HttpRequestPacket.createNote("SSL_CERT_NOTE");
    public static final Note<String> SECRET_NOTE = HttpRequestPacket.createNote("secret");

    private Queue<ShutdownHandler> shutdownHandlers =
            DataStructures.getLTQInstance(ShutdownHandler.class);

    /**
     * Add the {@link ShutdownHandler}, which will be called, when shutdown
     * request received.
     * 
     * @param handler {@link ShutdownHandler}
     */
    public void addShutdownHandler(final ShutdownHandler handler) {
        shutdownHandlers.add(handler);
    }

    /**
     * Remove the {@link ShutdownHandler}.
     *
     * @param handler {@link ShutdownHandler}
     */
    public void removeShutdownHandler(final ShutdownHandler handler) {
        shutdownHandlers.remove(handler);
    }

    /**
     * Handle the Ajp message.
     * 
     * @param ctx
     * @return
     * @throws IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();

        if (message == NEED_MORE_DATA_MESSAGE) {
            // Upper layer tries to read additional data
            // We need to send request to a server to obtain another data chunk.
            sendMoreDataRequestIfNeeded(ctx);
            return ctx.getStopAction();
        }

        final int type = extractType(ctx.getConnection(), message);
        
        switch (type) {
            case AjpConstants.JK_AJP13_FORWARD_REQUEST:
            {
                return processForwardRequest(ctx, message);
            }
            case AjpConstants.JK_AJP13_DATA:
            {
                return processData(ctx, message);
            }
            
            case AjpConstants.JK_AJP13_SHUTDOWN:
            {
                return processShutdown(ctx, message);
            }
            
            case AjpConstants.JK_AJP13_CPING_REQUEST:
            {
                return processCPing(ctx, message);
            }
                
            default:
            {
                throw new IllegalStateException("Unknown message " + type);
            }
            
        }
    }

    /**
     * Encoding HttpResponsePacket or HttpContent to Ajp message.
     * 
     * @param ctx
     * @return
     * @throws IOException
     */
    @Override
    public NextAction handleWrite(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();
        final HttpPacket httpPacket = ctx.getMessage();
        
        final Buffer encodedPacket = encodeHttpPacket(connection, httpPacket);
        ctx.setMessage(encodedPacket);

        return ctx.getInvokeAction();
    }

    private Buffer encodeHttpPacket(final Connection connection,
            final HttpPacket httpPacket) {

        final MemoryManager memoryManager = connection.getTransport().getMemoryManager();
        final boolean isHeader = httpPacket.isHeader();
        final HttpHeader httpHeader = isHeader ? (HttpHeader) httpPacket :
            ((HttpContent) httpPacket).getHttpHeader();
        final HttpResponsePacket httpResponsePacket = (HttpResponsePacket) httpHeader;
        Buffer encodedBuffer = null;
        if (!httpHeader.isCommitted()) {
            encodedBuffer = AjpMessageUtils.encodeHeaders(memoryManager, httpResponsePacket);
            if (httpResponsePacket.isAcknowledgement()) {
                encodedBuffer.trim();
                
                httpResponsePacket.acknowledged();
                return encodedBuffer; // DO NOT MARK COMMITTED
            }

            httpHeader.setCommitted(true);
        }

        if (!isHeader) {
            final HttpContent httpContentPacket = (HttpContent) httpPacket;
            final Buffer contentBuffer = httpContentPacket.getContent();
            if (contentBuffer.hasRemaining()) {
                return AjpMessageUtils.appendContentAndTrim(memoryManager,
                        encodedBuffer, contentBuffer);
            }
        }
        
        encodedBuffer.trim();
        return encodedBuffer;
    }

    /**
     * Handling Http request completion event sent by Http server filter and
     * send the Ajp end response message.
     * 
     * @param ctx
     * @param event
     * @return
     * @throws IOException
     */
    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {

        final Connection c = ctx.getConnection();

        if (event.type() == HttpServerFilter.RESPONSE_COMPLETE_EVENT.type()
                && c.isOpen()) {
            final HttpRequestPacketImpl httpRequest;
            if ((httpRequest = httpRequestInProcessAttr.remove(c)) != null) {
                onRequestProcessed(httpRequest);
                sendEndResponse(ctx);
            }

        }

        return ctx.getStopAction();
    }

    private NextAction processData(final FilterChainContext ctx,
            final Buffer messageContent) {
        
        final Connection connection = ctx.getConnection();
        final HttpRequestPacketImpl httpRequestPacket = httpRequestInProcessAttr.get(connection);

        if (messageContent.hasRemaining()) {
            // Skip the content length field - we know the size from the packet header
            messageContent.position(messageContent.position() + 2);
        }
        
        // Figure out if the content is last
        if (httpRequestPacket.isExpectContent()) {
            int contentBytesRemaining = httpRequestPacket.getContentBytesRemaining();
            // if we know the content-length
            if (contentBytesRemaining > 0) {
                contentBytesRemaining -= messageContent.remaining();
                httpRequestPacket.setContentBytesRemaining(contentBytesRemaining);
                // do we have more content remaining?
                if (contentBytesRemaining <= 0) {
                    httpRequestPacket.setExpectContent(false);
                }
            } else if (!messageContent.hasRemaining()) {
                // if chunked and zero-length content came
                httpRequestPacket.setExpectContent(false);
            }
        }

        final HttpContent content = HttpContent.builder(httpRequestPacket)
                .content(messageContent)
                .last(!httpRequestPacket.isExpectContent())
                .build();
        
        ctx.setMessage(content);

        // If we may expect more data - do the following trick:
        // set NEED_MORE_DATA_MESSAGE as remainder, so when more data will be requested
        // this filter will be invoked. This way we'll be able to send a request
        // for more data to web server.
        // See handleRead() and sendMoreDataRequestIfNeeded() methods
        return ctx.getInvokeAction(httpRequestPacket.isExpectContent() ?
            NEED_MORE_DATA_MESSAGE : null);
    }

    /**
     * Process ForwardRequest request message.
     *
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private NextAction processForwardRequest(final FilterChainContext ctx,
            final Buffer content) throws IOException {
        final Connection connection = ctx.getConnection();

        final HttpRequestPacketImpl httpRequestPacket =
                HttpRequestPacketImpl.create();
        httpRequestPacket.setConnection(connection);

        AjpMessageUtils.decodeRequest(content, httpRequestPacket, false);

        httpRequestInProcessAttr.set(connection, httpRequestPacket);
        ctx.setMessage(HttpContent.builder(httpRequestPacket).build());

        final long contentLength = httpRequestPacket.getContentLength();
        if (contentLength > 0) {
            // if content-length > 0 - the first data chunk will come immediately,
            // so let's wait for it
            httpRequestPacket.setContentBytesRemaining((int) contentLength);
            httpRequestPacket.setExpectContent(true);
            return ctx.getStopAction();
        } else if (contentLength < 0) {
            // We don't know if there is any content in the message, but we're
            // sure no message is following immediately
            httpRequestPacket.setExpectContent(true);
            return ctx.getInvokeAction(NEED_MORE_DATA_MESSAGE);
        } else {
            // content-length == 0 - no content is expected
            httpRequestPacket.setExpectContent(false);
            return ctx.getInvokeAction();
        }
    }

    /**
     * Process CPing request message.
     * We send CPong response back as plain Grizzly {@link Buffer}.
     * 
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private NextAction processCPing(final FilterChainContext ctx,
            final Buffer message) throws IOException {

        message.clear();

        message.put((byte) 'A');
        message.put((byte) 'B');
        message.putShort((short) 1);
        message.put(AjpConstants.JK_AJP13_CPONG_REPLY);
        message.flip();

        // Write the buffer
        ctx.write(message);
        
        // Notify about response complete event
        ctx.notifyDownstream(HttpServerFilter.RESPONSE_COMPLETE_EVENT);

        return ctx.getStopAction();
    }

    /**
     * Process Shutdown request message.
     * For now just ignore it.
     * 
     * @param ctx
     * @param message
     * @return
     * @throws IOException
     */
    private NextAction processShutdown(final FilterChainContext ctx,
            final Buffer message) {

        final Connection connection = ctx.getConnection();

        for (ShutdownHandler handler : shutdownHandlers) {
            try {
                handler.onShutdown(connection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception during ShutdownHandler execution", e);
            }
        }

        return ctx.getStopAction();
    }

    private void sendMoreDataRequestIfNeeded(final FilterChainContext ctx)
            throws IOException {
        
        final Connection connection = ctx.getConnection();
        
        // Check if message is still in process
        if (httpRequestInProcessAttr.isSet(connection)) {
            final MemoryManager mm = connection.getTransport().getMemoryManager();
            final Buffer buffer = mm.allocate(7);

            buffer.put((byte) 'A');
            buffer.put((byte) 'B');
            buffer.putShort((short) 3);
            buffer.put(AjpConstants.JK_AJP13_GET_BODY_CHUNK);
            buffer.putShort((short) AjpConstants.MAX_READ_SIZE);

            buffer.flip();
            buffer.allowBufferDispose(true);

            ctx.write(buffer);
        }
    }

    private void sendEndResponse(final FilterChainContext ctx) throws IOException {
        final Connection connection = ctx.getConnection();

        final MemoryManager mm = connection.getTransport().getMemoryManager();
        final Buffer buffer = mm.allocate(6);

        buffer.put((byte) 'A');
        buffer.put((byte) 'B');
        buffer.putShort((short) 2);
        buffer.put(AjpConstants.JK_AJP13_END_RESPONSE);
        buffer.put((byte) 1);

        buffer.flip();
        buffer.allowBufferDispose(true);

        ctx.write(buffer);
    }

    private void onRequestProcessed(final HttpRequestPacketImpl httpRequest) {
        final DataChunk dc = httpRequest.getNote(SSL_CERT_NOTE);
        if (dc != null) {
            dc.recycle();
        }

        httpRequest.removeNote(SECRET_NOTE);
    }

    private int extractType(final Connection connection, final Buffer buffer) {
        final int type;
        if (!httpRequestInProcessAttr.isSet(connection)) {
            // if request is no in process - it should be a new Ajp message
            type = buffer.get() & 0xFF;
        } else {
            // Ajp Data Packet
            type = AjpConstants.JK_AJP13_DATA;
        }

        return type;

    }
}
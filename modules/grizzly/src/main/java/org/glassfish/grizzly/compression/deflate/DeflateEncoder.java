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

package org.glassfish.grizzly.compression.deflate;

import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.MemoryManager;

import java.nio.ByteBuffer;
import java.util.zip.Deflater;

/**
 * This class implements a {@link org.glassfish.grizzly.Transformer} which encodes plain data to
 * the Deflate format.
 *
 * @author Gerd Riesselmann
 */
public class DeflateEncoder extends AbstractTransformer<Buffer, Buffer> {
    private final int bufferSize;

    public DeflateEncoder() {
        this(512);
    }

    public DeflateEncoder(int bufferSize) {
        this.bufferSize = bufferSize;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "deflate-encoder";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input.hasRemaining();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected DeflateOutputState createStateObject() {
        return new DeflateOutputState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            AttributeStorage storage, Buffer input) throws TransformationException {

        final MemoryManager memoryManager = obtainMemoryManager(storage);
        final DeflateOutputState state = (DeflateOutputState) obtainStateObject(storage);

        if (!state.isInitialized) {
            state.initialize();
        }

        Buffer encodedBuffer = null;
        if (input != null && input.hasRemaining()) {
            encodedBuffer = encodeBuffer(input, state, memoryManager);
        }

        if (encodedBuffer == null) {
            return TransformationResult.createIncompletedResult(null);
        }

        return TransformationResult.createCompletedResult(encodedBuffer, null);
    }

    /**
     * Finishes to compress data to the output stream without closing
     * the underlying stream. Use this method when applying multiple filters
     * in succession to the same output stream.
     *
     * @return {@link org.glassfish.grizzly.Buffer} with the last Deflated data to be sent.
     */
    public Buffer finish(AttributeStorage storage) {
        final MemoryManager memoryManager = obtainMemoryManager(storage);
        final DeflateOutputState state = (DeflateOutputState) obtainStateObject(storage);

        Buffer resultBuffer = null;

        if (state.isInitialized) {
            final Deflater deflater = state.deflater;
            if (!deflater.finished()) {
                deflater.finish();

                while (!deflater.finished()) {
                    resultBuffer = Buffers.appendBuffers(memoryManager,
                            resultBuffer,
                            deflate(deflater, memoryManager));
                }
            }

            state.reset();
        }

        return resultBuffer;
    }

    private Buffer encodeBuffer(Buffer buffer,
            DeflateOutputState state, MemoryManager memoryManager) {
        final Deflater deflater = state.deflater;

        if (deflater.finished()) {
            throw new IllegalStateException("write beyond end of stream");
        }

        // Deflate no more than stride bytes at a time.  This avoids
        // excess copying in deflateBytes (see Deflater.c)
        int stride = bufferSize;
        Buffer resultBuffer = null;
        final ByteBufferArray byteBufferArray = buffer.toByteBufferArray();
        final ByteBuffer[] buffers = byteBufferArray.getArray();
        final int size = byteBufferArray.size();

        for (int i = 0; i < size; i++) {
            final ByteBuffer byteBuffer = buffers[i];
            final int len = byteBuffer.remaining();
            if (len > 0) {
                final byte[] buf;
                final int off;
                if (byteBuffer.hasArray()) {
                    buf = byteBuffer.array();
                    off = byteBuffer.arrayOffset() + byteBuffer.position();
                } else {
                    // @TODO allocate byte array via MemoryUtils
                    buf = new byte[len];
                    off = 0;
                    byteBuffer.get(buf);
                    byteBuffer.position(byteBuffer.position() - len);
                }

                for (int j = 0; j < len; j += stride) {
                    deflater.setInput(buf, off + j, Math.min(stride, len - j));
                    while (!deflater.needsInput()) {
                        final Buffer deflated = deflate(deflater, memoryManager);
                        if (deflated != null) {
                            resultBuffer = Buffers.appendBuffers(
                                    memoryManager, resultBuffer, deflated);
                        }
                    }
                }
            }
        }

        byteBufferArray.restore();
        byteBufferArray.recycle();

        buffer.position(buffer.limit());
        buffer.flip();
        
        return resultBuffer;
    }

    /**
     * Writes next block of compressed data to the output stream.
     */
     protected Buffer deflate(Deflater deflater, MemoryManager memoryManager) {
        final Buffer buffer = memoryManager.allocate(bufferSize);
        final ByteBuffer byteBuffer = buffer.toByteBuffer();
        final byte[] array = byteBuffer.array();
        final int offset = byteBuffer.arrayOffset() + byteBuffer.position();

	    int len = deflater.deflate(array, offset, bufferSize);
        if (len <= 0) {
            buffer.dispose();
            return null;
        }

        buffer.position(len);
        buffer.trim();

        return buffer;
    }

    protected static final class DeflateOutputState
            extends LastResultAwareState<Buffer, Buffer> {
        private boolean isInitialized;

        /**
         * Compressor for this stream.
         */
        private Deflater deflater;

        private void initialize() {
            final Deflater newDeflater = new Deflater(); //Deflater.DEFAULT_COMPRESSION, false);
            deflater = newDeflater;
            isInitialized = true;
        }
        
        private void reset() {
            isInitialized = false;
            deflater = null;
        }
    }
}

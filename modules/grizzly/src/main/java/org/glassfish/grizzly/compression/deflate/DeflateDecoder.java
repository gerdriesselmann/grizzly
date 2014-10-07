/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * This class implements a {@link org.glassfish.grizzly.Transformer} which decodes data
 * represented in the Deflate format.
 *
 * @author Gerd Riesselmann
 */
public class DeflateDecoder extends AbstractTransformer<Buffer, Buffer> {
    protected enum DecodeStatus {
        PAYLOAD, DONE
    }

    private final int bufferSize;

    public DeflateDecoder() {
        this(512);
    }

    public DeflateDecoder(int bufferSize) {
        this.bufferSize = bufferSize;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "deflate-decoder";
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
    protected DeflateInputState createStateObject() {
        return new DeflateInputState();
    }

    @Override
    protected TransformationResult<Buffer, Buffer> transformImpl(
            AttributeStorage storage, Buffer input) throws TransformationException {
        final MemoryManager memoryManager = obtainMemoryManager(storage);

        final DeflateInputState state = (DeflateInputState) obtainStateObject(storage);

        if (!state.isInitialized()) {
            if (!initializeInput(input, state)) {
                return TransformationResult.createIncompletedResult(input);
            }
        }

        Buffer decodedBuffer = null;

        if (state.getDecodeStatus() == DecodeStatus.PAYLOAD) {
            if (input.hasRemaining()) {
                decodedBuffer = decodeBuffer(memoryManager, input, state);
            }
        }

        if (state.getDecodeStatus() == DecodeStatus.DONE) {
            state.setInitialized(false);
        }

        final boolean hasRemainder = input.hasRemaining();

        if (decodedBuffer == null || !decodedBuffer.hasRemaining()) {
            return TransformationResult.createIncompletedResult(hasRemainder ? input : null);
        }

        return TransformationResult.createCompletedResult(decodedBuffer,
                hasRemainder ? input : null);
    }

    private Buffer decodeBuffer(MemoryManager memoryManager, Buffer buffer,
            DeflateInputState state) {

        final Inflater inflater = state.getInflater();

        final ByteBufferArray byteBufferArray = buffer.toByteBufferArray();
        final ByteBuffer[] byteBuffers = byteBufferArray.getArray();
        final int size = byteBufferArray.size();

        Buffer resultBuffer = null;

        for (int i = 0; i < size; i++) {
            final ByteBuffer byteBuffer = byteBuffers[i];
            final int len = byteBuffer.remaining();

            final byte[] array;
            final int offset;
            if (byteBuffer.hasArray()) {
                array = byteBuffer.array();
                offset = byteBuffer.arrayOffset() + byteBuffer.position();
            } else {
                // @TODO allocate byte array via MemoryUtils
                array = new byte[len];
                offset = 0;
                byteBuffer.get(array);
                byteBuffer.position(byteBuffer.position() - len);
            }

            inflater.setInput(array, offset, len);

            int lastInflated;
            do {
                final Buffer decodedBuffer = memoryManager.allocate(bufferSize);
                final ByteBuffer decodedBB = decodedBuffer.toByteBuffer();
                final byte[] decodedArray = decodedBB.array();
                final int decodedArrayOffs = decodedBB.arrayOffset() + decodedBB.position();

                try {
                    lastInflated = inflater.inflate(decodedArray, decodedArrayOffs, bufferSize);
                } catch (DataFormatException e) {
                    decodedBuffer.dispose();
                    String s = e.getMessage();
                    throw new IllegalStateException(s != null ? s : "Invalid ZLIB data format");
                }

                if (lastInflated > 0) {
                    decodedBuffer.position(lastInflated);
                    decodedBuffer.trim();
                    resultBuffer = Buffers.appendBuffers(memoryManager,
                            resultBuffer, decodedBuffer);
                } else {
                    decodedBuffer.dispose();
                    if (inflater.finished() || inflater.needsDictionary()) {
                        final int remainder = inflater.getRemaining();

                        final int remaining = byteBuffer.remaining();

                        byteBufferArray.restore();
                        byteBufferArray.recycle();

                        buffer.position(
                                buffer.position() + remaining - remainder);

                        state.setDecodeStatus(DecodeStatus.DONE);
                        return resultBuffer;
                    }
                }
            } while (lastInflated > 0);

            final int remaining = byteBuffer.remaining();

            byteBufferArray.restore();
            byteBufferArray.recycle();

            buffer.position(buffer.position() + remaining);
        }

        return resultBuffer;
    }

    private boolean initializeInput(final Buffer buffer,
            final DeflateInputState state) {

        Inflater inflater = state.getInflater();
        if (inflater == null) {
            inflater = new Inflater();
            state.setInflater(inflater);
        } else if (state.getDecodeStatus() == DecodeStatus.DONE) {
            state.setDecodeStatus(DecodeStatus.PAYLOAD);
            inflater.reset();
        }

        state.setInitialized(true);

        return true;
    }


    protected static final class DeflateInputState
            extends LastResultAwareState<Buffer, Buffer> {
        private boolean isInitialized;

        /**
         * Decompressor for this stream.
         */
        private Inflater inflater;

        private DecodeStatus decodeStatus = DecodeStatus.PAYLOAD;

        public boolean isInitialized() {
            return isInitialized;
        }

        public void setInitialized(boolean isInitialized) {
            this.isInitialized = isInitialized;
        }

        public Inflater getInflater() {
            return inflater;
        }

        public void setInflater(Inflater inflater) {
            this.inflater = inflater;
        }

        public DecodeStatus getDecodeStatus() {
            return decodeStatus;
        }

        public void setDecodeStatus(DecodeStatus decodeStatus) {
            this.decodeStatus = decodeStatus;
        }
    }
}

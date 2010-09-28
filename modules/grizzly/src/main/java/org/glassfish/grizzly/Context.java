/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.attributes.AttributeStorage;
import org.glassfish.grizzly.attributes.IndexedAttributeHolder;
import java.util.logging.Logger;

/**
 * Object, which is responsible for holding context during I/O event processing.
 *
 * @author Alexey Stashok
 */
public class Context implements AttributeStorage, Cacheable {

    private static final Logger LOGGER = Grizzly.logger(Context.class);
    private static final Processor NULL_PROCESSOR = new NullProcessor();
    private static final ThreadCache.CachedTypeIndex<Context> CACHE_IDX =
            ThreadCache.obtainIndex(Context.class, 4);

    public static Context create(Connection connection) {
        Context context = ThreadCache.takeFromCache(CACHE_IDX);
        if (context == null) {
            context = new Context();
        }

        context.setConnection(connection);
        return context;
    }

    public static Context create(final Connection connection,
            final Processor processor, final IOEvent ioEvent) {
        final Context context;

        if (processor != null) {
            context = processor.obtainContext(connection);
        } else {
            context = NULL_PROCESSOR.obtainContext(connection);
        }

        context.setIoEvent(ioEvent);

        return context;
    }
    /**
     * Processing Connection
     */
    private Connection connection;
    /**
     * Processing IOEvent
     */
    private IOEvent ioEvent = IOEvent.NONE;
    /**
     * Processor, responsible for I/O event processing
     */
    private Processor processor;
    /**
     * Attributes, associated with the processing Context
     */
    private final AttributeHolder attributes;
    /**
     * PostProcessor is called, when Context is ready to be recycled,
     * though the task might be still processed.
     */
    private PostProcessor postProcessor;

    public Context() {
        attributes = new IndexedAttributeHolder(Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
    }

    /**
     * Get the processing {@link IOEvent}.
     *
     * @return the processing {@link IOEvent}.
     */
    public IOEvent getIoEvent() {
        return ioEvent;
    }

    /**
     * Set the processing {@link IOEvent}.
     *
     * @param ioEvent the processing {@link IOEvent}.
     */
    public void setIoEvent(IOEvent ioEvent) {
        this.ioEvent = ioEvent;
    }

    /**
     * Get the processing {@link Connection}.
     *
     * @return the processing {@link Connection}.
     */
    public Connection getConnection() {
        return connection;
    }

    /**
     * Set the processing {@link Connection}.
     *
     * @param connection the processing {@link Connection}.
     */
    public void setConnection(Connection connection) {
        this.connection = connection;
    }

    /**
     * Get the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     * 
     * @return the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public Processor getProcessor() {
        return processor;
    }

    /**
     * Set the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     *
     * @param processor the {@link Processor}, which is responsible to process
     * the {@link IOEvent}.
     */
    public void setProcessor(Processor processor) {
        this.processor = processor;
    }

    public PostProcessor getPostProcessor() {
        return postProcessor;
    }

    public void setPostProcessor(PostProcessor postProcessor) {
        this.postProcessor = postProcessor;
    }

    /**
     * Get attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}. {@link AttributeHolder} is cleared after each I/O event
     * processing.
     * Method may return <tt>null</tt>, if there were no attributes added before.
     *
     * @return attributes ({@link AttributeHolder}), associated with the processing
     * {@link Context}. 
     */
    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    /**
     * If implementation uses {@link org.glassfish.grizzly.utils.ObjectPool}
     * to store and reuse {@link Context} instances - this method will be
     * called before {@link Context} will be offered to pool.
     */
    public void reset() {
        attributes.recycle();

        processor = null;
        postProcessor = null;
        connection = null;
        ioEvent = IOEvent.NONE;
    }

    /**
     * Recycle this {@link Context}
     */
    @Override
    public void recycle() {
        reset();
        ThreadCache.putToCache(CACHE_IDX, this);
    }

    private final static class NullProcessor implements Processor {

        @Override
        public Context obtainContext(Connection connection) {
            final Context context = Context.create(connection);
            context.setProcessor(this);

            return context;
        }

        @Override
        public ProcessorResult process(Context context) throws IOException {
            return ProcessorResult.createNotRun();
        }

        @Override
        public GrizzlyFuture read(Connection connection,
                CompletionHandler completionHandler) throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public GrizzlyFuture write(Connection connection, Object dstAddress,
                Object message, CompletionHandler completionHandler)
                throws IOException {
            throw new UnsupportedOperationException("Not supported.");
        }

        @Override
        public boolean isInterested(IOEvent ioEvent) {
            return true;
        }

        @Override
        public void setInterested(IOEvent ioEvent, boolean isInterested) {
        }
    }
}
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

package org.glassfish.grizzly.filterchain;

import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.utils.IOEventMask;

/**
 * Abstract {@link FilterChain} implementation,
 * which redirects {@link org.glassfish.grizzly.Processor#process(org.glassfish.grizzly.Context)}
 * call to the {@link AbstractFilterChain#execute(org.glassfish.grizzly.filterchain.FilterChainContext)}
 *
 * @see FilterChain
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractFilterChain implements FilterChain {
    // By default interested in all client connection related events
    protected final IOEventMask interestedIoEventsMask = IOEventMask.ALL_EVENTS_MASK;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInterested(IOEvent ioEvent) {
        return interestedIoEventsMask.isInterested(ioEvent);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setInterested(IOEvent ioEvent, boolean isInterested) {
        interestedIoEventsMask.setInterested(ioEvent, isInterested);
    }

    @Override
    public final FilterChainContext obtainFilterChainContext(
            final Connection connection) {

        final FilterChainContext context = FilterChainContext.create(connection);
        context.internalContext.setProcessor(this);
        return context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public final Context obtainContext(final Connection connection) {
        return obtainFilterChainContext(connection).internalContext;
    }

    @Override
    protected void finalize() throws Throwable {
        clear();
        super.finalize();
    }
}
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

package org.glassfish.grizzly.nio;

import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;

/**
 *
 * @author oleksiys
 */
public abstract class NIOTransportFactory extends TransportFactory {

    protected SelectorHandler defaultSelectorHandler;
    protected SelectionKeyHandler defaultSelectionKeyHandler;
    protected TemporarySelectorPool defaultTemporarySelectorPool;
    
    public SelectorHandler getDefaultSelectorHandler() {
        return defaultSelectorHandler;
    }

    public void setDefaultSelectorHandler(SelectorHandler defaultSelectorHandler) {
        this.defaultSelectorHandler = defaultSelectorHandler;
    }

    public SelectionKeyHandler getDefaultSelectionKeyHandler() {
        return defaultSelectionKeyHandler;
    }

    public void setDefaultSelectionKeyHandler(SelectionKeyHandler defaultSelectionKeyHandler) {
        this.defaultSelectionKeyHandler = defaultSelectionKeyHandler;
    }

    public TemporarySelectorPool getDefaultTemporarySelectorPool() {
        return defaultTemporarySelectorPool;
    }

    public void setDefaultTemporarySelectorPool(TemporarySelectorPool defaultTemporarySelectorPool) {
        this.defaultTemporarySelectorPool = defaultTemporarySelectorPool;
    }

    @Override
    public void initialize() {
        super.initialize();
        
        defaultSelectorHandler = new DefaultSelectorHandler();
        defaultSelectionKeyHandler = new DefaultSelectionKeyHandler();
        
        /* By default TemporarySelector pool size should be equal
        to the number of processing threads */
        int selectorPoolSize = TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
        if (defaultWorkerThreadPool instanceof AbstractThreadPool) {
                selectorPoolSize = Math.min(
                       ((AbstractThreadPool) defaultWorkerThreadPool).getConfig().getMaxPoolSize(),
                       selectorPoolSize);
        }
        defaultTemporarySelectorPool = 
                new TemporarySelectorPool(selectorPoolSize);
    }

    @Override
    protected <T extends Transport> T setupTransport(T transport) {
        transport = super.setupTransport(transport);
        NIOTransport nioTransport = (NIOTransport) transport;
        nioTransport.setSelectorHandler(defaultSelectorHandler);
        nioTransport.setSelectionKeyHandler(defaultSelectionKeyHandler);
        
        return transport;
    }
}

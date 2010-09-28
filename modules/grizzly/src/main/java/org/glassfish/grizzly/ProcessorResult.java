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

/**
 * The interface represents the result of {@link Processor} execution.
 * 
 * @author Alexey Stashok
 */
public class ProcessorResult {

    private static final ProcessorResult NOT_RUN_RESULT = new ProcessorResult(Status.NOT_RUN, null, false);
    private static final ProcessorResult COMPLETE_RESULT = new ProcessorResult(Status.COMPLETE, null, false);
    private static final ProcessorResult COMPLETE_LEAVE_RESULT = new ProcessorResult(Status.COMPLETE_LEAVE, null, false);
    private static final ProcessorResult REREGISTER_RESULT = new ProcessorResult(Status.REREGISTER, null, false);
    private static final ProcessorResult ERROR_RESULT = new ProcessorResult(Status.ERROR);
    private static final ProcessorResult TERMINATE_RESULT = new ProcessorResult(Status.TERMINATE, null, false);
    
    private static final ThreadCache.CachedTypeIndex<ProcessorResult> CACHE_IDX =
            ThreadCache.obtainIndex(ProcessorResult.class, 1);

    private static ProcessorResult create() {
        final ProcessorResult result = ThreadCache.takeFromCache(CACHE_IDX);
        if (result != null) {
            return result;
        }

        return new ProcessorResult();
    }

    /**
     * Enumeration represents the status/code of {@link ProcessorResult}.
     */
    public enum Status {
        COMPLETE, COMPLETE_LEAVE, REREGISTER, RERUN, ERROR, TERMINATE, NOT_RUN
    }
    
    private final boolean canRecycle;
    /**
     * Result status
     */
    private Status status;
    /**
     * Result description
     */
    private Object context;

    public static ProcessorResult createComplete() {
        return COMPLETE_RESULT;
    }

    public static ProcessorResult createCompleteLeave() {
        return COMPLETE_LEAVE_RESULT;
    }

    public static ProcessorResult createReregister() {
        return REREGISTER_RESULT;
    }

    public static ProcessorResult createError() {
        return ERROR_RESULT;
    }

    public static ProcessorResult createError(Object description) {
        return create().setStatus(Status.ERROR).setContext(description);
    }

    public static ProcessorResult createRerun(Context context) {
        return create().setStatus(Status.RERUN).setContext(context);
    }

    public static ProcessorResult createTerminate() {
        return TERMINATE_RESULT;
    }

    public static ProcessorResult createNotRun() {
        return NOT_RUN_RESULT;
    }

    private ProcessorResult() {
        this(null, null);
    }

    private ProcessorResult(Status status) {
        this(status, null);
    }

    private ProcessorResult(Status status, Object context) {
        this(status, context, true);
    }

    private ProcessorResult(Status status, Object context, boolean canRecycle) {
        this.status = status;
        this.context = context;
        this.canRecycle = canRecycle;
    }

    /**
     * Get the result status.
     *
     * @return the result status.
     */
    public Status getStatus() {
        return status;
    }

    protected ProcessorResult setStatus(Status status) {
        this.status = status;
        return this;
    }

    /**
     * Get the {@link ProcessorResult} context.
     *
     * @return the {@link ProcessorResult} context.
     */
    public Object getContext() {
        return context;
    }

    protected ProcessorResult setContext(Object context) {
        this.context = context;
        return this;
    }

    public void recycle() {
        if (canRecycle) {
            status = null;
            context = null;
        }
    }
}
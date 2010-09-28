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

package org.glassfish.grizzly.http;

import org.glassfish.grizzly.http.util.BufferChunk;
import org.glassfish.grizzly.http.util.Utils;

import java.util.Locale;


/**
 * The {@link HttpHeader} object, which represents HTTP response message.
 *
 * @see HttpHeader
 * @see HttpRequestPacket
 *
 * @author Alexey Stashok
 */
public abstract class HttpResponsePacket extends HttpHeader {
    public static final int NON_PARSED_STATUS = Integer.MIN_VALUE;
    
    // ----------------------------------------------------- Instance Variables

    /**
     * The request that triggered this response.
     */
    private HttpRequestPacket request;

    /**
     * The {@link Locale} of the entity body being sent by this response.
     */
    private Locale locale;

    /**
     * The value of the <code>Content-Language</code> response header.
     */
    private String contentLanguage;


    /**
     * Status code.
     */
    protected int parsedStatusInt = NON_PARSED_STATUS;    
    protected final BufferChunk statusBC = new StatusBufferChunk();

    /**
     * Status message.
     */
    private final BufferChunk reasonPhraseBC = BufferChunk.newInstance();


    /**
     * Does this HttpResponsePacket represent an acknowledgment to
     * an Expect header.
     */
    private boolean acknowledgment;


    /**
     * Returns {@link HttpResponsePacket} builder.
     *
     * @return {@link Builder}.
     */
    public static Builder builder(HttpRequestPacket request) {
        return new Builder(request);
    }

    // ----------------------------------------------------------- Constructors
    protected HttpResponsePacket() {
    }

    // -------------------- State --------------------
    /**
     * Gets the status code for this response as {@link BufferChunk} (avoid
     * the status code parsing}.
     *
     * @return the status code for this response as {@link BufferChunk} (avoid
     * the status code parsing}.
     */
    public BufferChunk getStatusBC() {
        return statusBC;
    }

    /**
     * Gets the status code for this response.
     *
     * @return the status code for this response.
     */
    public int getStatus() {
        if (parsedStatusInt == NON_PARSED_STATUS) {
            parsedStatusInt = Integer.parseInt(statusBC.toString());
        }

        return parsedStatusInt;
    }
    
    /**
     * Sets the status code for this response.
     *
     * @param status the status code for this response.
     */
    public void setStatus(int status) {
        // the order is important here as statusBC.setXXX will reset the parsedIntStatus
        statusBC.setString(Integer.toString(status));
        parsedStatusInt = status;
    }


    /**
     * Gets the status reason phrase for this response as {@link BufferChunk}
     * (avoid creation of a String object}.
     *
     * @param useDefault if <code>true</code> and no reason phase has been
     *  explicitly set, the default as defined by <code>RFC 2616</code> will
     *  be returned. 
     *
     * @return the status reason phrase for this response as {@link BufferChunk}
     * (avoid creation of a String object}.
     */
    public BufferChunk getReasonPhraseBC(boolean useDefault) {
        if (useDefault && reasonPhraseBC.isNull()) {
            return Utils.getHttpStatusMessage(getStatus());
        }
        return reasonPhraseBC;
    }

    /**
     * Gets the status reason phrase for this response.
     *
     * @return the status reason phrase for this response.
     */
    public String getReasonPhrase() {
        return reasonPhraseBC.toString();
    }


    /**
     * Sets the status reason phrase for this response.
     *
     * @param message the status reason phrase for this response.
     */
    public void setReasonPhrase(String message) {
        reasonPhraseBC.setString(message);
    }


    /**
     * @return the request that triggered this response
     */
    public HttpRequestPacket getRequest() {
        return request;
    }


    /**
     * @return <code>true</code> if this response packet is intended
     *  as an acknowledgement to an expectation from a client request.
     */
    public boolean isAcknowledgement() {
        return acknowledgment;
    }


    /**
     * Mark this packet as an acknowledgement to a client expectation.
     *
     * @param acknowledgement <code>true</code> if this packet is an
     *  acknowledgement to a client expectation.
     */
    public void setAcknowledgement(boolean acknowledgement) {
        this.acknowledgment = acknowledgement;
    }


    /**
     * Mark this packet as having been acknowledged.
     */
    public void acknowledged() {
        acknowledgment = false;
        parsedStatusInt = NON_PARSED_STATUS;
        reasonPhraseBC.recycle();
    }


    // --------------------


    /**
     * {@inheritDoc}
     */
    @Override
    protected void reset() {
        statusBC.recycle();
        parsedStatusInt = NON_PARSED_STATUS;
        acknowledgment = false;
        reasonPhraseBC.recycle();
        locale = null;
        contentLanguage = null;
        request = null;

        super.reset();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public final boolean isRequest() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(256);
        sb.append("HttpResponsePacket (status=").append(getStatus())
                .append(" reason=").append(getReasonPhrase())
                .append(" protocol=").append(getProtocol())
                .append(" content-length=").append(getContentLength())
                .append(" headers=").append(getHeaders())
                .append(" committed=").append(isCommitted())
                .append(')');
        
        return sb.toString();
    }


    /**
     * @inheritDoc
     */
    @Override public void setHeader(String name, String value) {
        char c = name.charAt(0);
        if((c=='C' || c=='c') && checkSpecialHeader(name, value)) {
            return;
        }
        super.setHeader(name, value);
    }

    
    /**
     * @inheritDoc
     */
    @Override public void addHeader(String name, String value) {
        char c = name.charAt(0);
        if((c=='C' || c=='c') && checkSpecialHeader(name, value)) {
            return;
        }
        super.addHeader(name, value);
    }


    /**
     * @return the {@link Locale} of this response.
     */
    public Locale getLocale() {
        return locale;
    }


    /**
     * Called explicitly by user to set the Content-Language and
     * the default encoding
     */
    public void setLocale(Locale locale) {

        if (locale == null) {
            return;  // throw an exception?
        }

        // Save the locale for use by getLocale()
        this.locale = locale;

        // Set the contentLanguage for header output
        contentLanguage = locale.getLanguage();
        if ((contentLanguage != null) && (contentLanguage.length() > 0)) {
            String country = locale.getCountry();
            StringBuilder value = new StringBuilder(contentLanguage);
            if ((country != null) && (country.length() > 0)) {
                value.append('-');
                value.append(country);
            }
            contentLanguage = value.toString();
        }

    }


    /**
     * @return the value that will be used by the <code>Content-Language</code>
     *  response header
     */
    public String getContentLanguage() {
        return contentLanguage;
    }


    /**
     * Set the value that will be used by the <code>Content-Language</code>
     * response header.
     */
    public void setContentLanguage(String contentLanguage) {
        this.contentLanguage = contentLanguage;
    }


    // ------------------------------------------------- Package Private Methods


    /**
     * Associates the request that triggered this response.
     * @param request the request that triggered this response
     */
    public void setRequest(HttpRequestPacket request) {
        this.request = request;
    }


    // --------------------------------------------------------- Private Methods


    /**
     * Set internal fields for special header names.
     * Called from set/addHeader.
     * Return true if the header is special, no need to set the header.
     */
    private boolean checkSpecialHeader(String name, String value) {
        // XXX Eliminate redundant fields !!!
        // ( both header and in special fields )
        if (name.equalsIgnoreCase("Content-Type")) {
            setContentType(value);
            return true;
        }
        if (name.equalsIgnoreCase("Content-Length")) {
            try {
                int cL = Integer.parseInt(value);
                setContentLength(cL);
                return true;
            } catch (NumberFormatException ex) {
                // Do nothing - the spec doesn't have any "throws"
                // and the user might know what he's doing
                return false;
            }
        }
        if (name.equalsIgnoreCase("Content-Language")) {
            // TODO XXX XXX Need to construct Locale or something else
        }
        return false;
    }


    // ---------------------------------------------------------- Nested Classes

    /**
     * {@link BufferChunk} implementation which resets parsedStatusInt on change.
     */
    private class StatusBufferChunk extends BufferChunk {
        @Override
        protected void onContentChanged() {
            parsedStatusInt = NON_PARSED_STATUS;
        }
    }

    /**
     * <tt>HttpResponsePacket</tt> message builder.
     */
    public static class Builder extends HttpHeader.Builder<Builder> {
        protected Builder(HttpRequestPacket request) {
            packet = request.getResponse();
            if (packet == null) {
                packet = HttpResponsePacketImpl.create();
                ((HttpResponsePacket) packet).setRequest(request);
                packet.setSecure(request.isSecure());
            }
        }

        /**
         * Sets the status code for this response.
         *
         * @param status the status code for this response.
         */
        public Builder status(int status) {
            ((HttpResponsePacket) packet).setStatus(status);
            return this;
        }

        /**
         * Sets the status reason phrase for this response.
         *
         * @param reasonPhrase the status reason phrase for this response.
         */
        public Builder reasonPhrase(String reasonPhrase) {
            ((HttpResponsePacket) packet).setReasonPhrase(reasonPhrase);
            return this;
        }

        /**
         * Build the <tt>HttpResponsePacket</tt> message.
         *
         * @return <tt>HttpResponsePacket</tt>
         */
        public final HttpResponsePacket build() {
            return (HttpResponsePacket) packet;
        }
    }
}
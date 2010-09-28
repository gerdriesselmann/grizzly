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

package org.glassfish.grizzly.nio.transport;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.ProcessorSelector;
import org.glassfish.grizzly.asyncqueue.AsyncQueueIO;
import org.glassfish.grizzly.nio.RegisterChannelResult;
import org.glassfish.grizzly.nio.RoundRobinConnectionDistributor;
import org.glassfish.grizzly.nio.DefaultSelectorHandler;
import org.glassfish.grizzly.nio.DefaultSelectionKeyHandler;
import org.glassfish.grizzly.nio.AbstractNIOTransport;
import org.glassfish.grizzly.asyncqueue.AsyncQueueEnabledTransport;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.asyncqueue.AsyncQueueReader;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.filterchain.FilterChainEnabledTransport;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorPool;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorsEnabledTransport;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Collection;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.EmptyCompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.PostProcessor;
import org.glassfish.grizzly.ProcessorExecutor;
import org.glassfish.grizzly.Reader;
import org.glassfish.grizzly.SocketBinder;
import org.glassfish.grizzly.StandaloneProcessor;
import org.glassfish.grizzly.StandaloneProcessorSelector;
import org.glassfish.grizzly.WriteResult;
import org.glassfish.grizzly.Writer;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.monitoring.jmx.JmxObject;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorIO;
import org.glassfish.grizzly.strategies.WorkerThreadStrategy;
import org.glassfish.grizzly.threadpool.AbstractThreadPool;
import org.glassfish.grizzly.threadpool.GrizzlyExecutorService;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;
import org.glassfish.grizzly.threadpool.WorkerThread;
import java.io.EOFException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

/**
 * TCP Transport NIO implementation
 * 
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public final class TCPNIOTransport extends AbstractNIOTransport implements
        SocketBinder, AsyncQueueEnabledTransport, FilterChainEnabledTransport,
        TemporarySelectorsEnabledTransport {

    private static final Logger LOGGER = Grizzly.logger(TCPNIOTransport.class);

    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;
    private static final int DEFAULT_WRITE_BUFFER_SIZE = 4096;
    
    private static final String DEFAULT_TRANSPORT_NAME = "TCPNIOTransport";
    /**
     * The Server connections.
     */
    protected final Collection<TCPNIOServerConnection> serverConnections;
    /**
     * Transport AsyncQueueIO
     */
    protected AsyncQueueIO asyncQueueIO;
    /**
     * Server socket backlog.
     */
    protected TemporarySelectorIO temporarySelectorIO;
    /**
     * The server socket time out
     */
    protected int serverSocketSoTimeout = 0;
    /**
     * The socket tcpDelay.
     * 
     * Default value for tcpNoDelay is disabled (set to true).
     */
    protected boolean tcpNoDelay = true;
    /**
     * The socket reuseAddress
     */
    protected boolean reuseAddress = true;
    /**
     * The socket linger.
     */
    protected int linger = -1;
    /**
     * The socket keepAlive mode.
     */
    protected boolean isKeepAlive = false;
    /**
     * The socket time out
     */
    protected int clientSocketSoTimeout = -1;
    /**
     * Default channel connection timeout
     */
    protected int connectionTimeout =
            TCPNIOConnectorHandler.DEFAULT_CONNECTION_TIMEOUT;

    private int maxReadAttempts = 3;
    
    private Filter defaultTransportFilter;
    final RegisterChannelCompletionHandler selectorRegistrationHandler;

    /**
     * Default {@link TCPNIOConnectorHandler}
     */
    private final TCPNIOConnectorHandler connectorHandler =
            new TransportConnectorHandler();
    
    public TCPNIOTransport() {
        this(DEFAULT_TRANSPORT_NAME);
    }

    protected TCPNIOTransport(String name) {
        super(name);
        
        readBufferSize = DEFAULT_READ_BUFFER_SIZE;
        writeBufferSize = DEFAULT_WRITE_BUFFER_SIZE;

        selectorRegistrationHandler = new RegisterChannelCompletionHandler();

        asyncQueueIO = new AsyncQueueIO(new TCPNIOAsyncQueueReader(this),
                new TCPNIOAsyncQueueWriter(this));

        temporarySelectorIO = new TemporarySelectorIO(
                new TCPNIOTemporarySelectorReader(this),
                new TCPNIOTemporarySelectorWriter(this));

        attributeBuilder = Grizzly.DEFAULT_ATTRIBUTE_BUILDER;
        defaultTransportFilter = new TCPNIOTransportFilter(this);
        serverConnections = new ConcurrentLinkedQueue<TCPNIOServerConnection>();
    }

    @Override
    public void start() throws IOException {
        state.getStateLocker().writeLock().lock();
        try {
            State currentState = state.getState();
            if (currentState != State.STOP) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in STOP or BOUND state!");
            }

            state.setState(State.STARTING);

            if (selectorHandler == null) {
                selectorHandler = new DefaultSelectorHandler();
            }

            if (selectionKeyHandler == null) {
                selectionKeyHandler = new DefaultSelectionKeyHandler();
            }

            if (processor == null && processorSelector == null) {
                processor = new StandaloneProcessor();
            }

            if (selectorRunnersCount <= 0) {
                selectorRunnersCount = Math.max(1, Runtime.getRuntime().availableProcessors() / 2 * 3);
            }

            if (nioChannelDistributor == null) {
                nioChannelDistributor = new RoundRobinConnectionDistributor(this);
            }

            if (threadPool == null) {
                setThreadPool(GrizzlyExecutorService.createInstance(
                        ThreadPoolConfig.DEFAULT.clone().
                        setCorePoolSize(selectorRunnersCount * 2).
                        setMaxPoolSize(selectorRunnersCount * 2)));
            }

            if (strategy == null) {
                strategy = new WorkerThreadStrategy(threadPool);
            }

            /* By default TemporarySelector pool size should be equal
            to the number of processing threads */
            int selectorPoolSize =
                    TemporarySelectorPool.DEFAULT_SELECTORS_COUNT;
            if (threadPool instanceof AbstractThreadPool) {
                selectorPoolSize = Math.min(
                       ((AbstractThreadPool) threadPool).getConfig().getMaxPoolSize(),
                       selectorPoolSize);
            }
            temporarySelectorIO.setSelectorPool(
                    new TemporarySelectorPool(selectorPoolSize));

            startSelectorRunners();

            listenServerConnections();

            state.setState(State.START);

            notifyProbesStart(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    private void listenServerConnections() {
        for (TCPNIOServerConnection serverConnection : serverConnections) {
            try {
                listenServerConnection(serverConnection);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING,
                        "Exception occurred when starting server connection: " +
                        serverConnection, e);
            }
        }
    }

    private void listenServerConnection(TCPNIOServerConnection serverConnection)
            throws IOException {
        serverConnection.listen();
    }

    @Override
    public void stop() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            unbindAll();
            state.setState(State.STOP);

            stopSelectorRunners();

            if (threadPool != null) {
                threadPool.shutdown();
                threadPool = null;
            }

            notifyProbesStop(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void pause() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.START) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in START state!");
            }
            state.setState(State.PAUSE);
            notifyProbesPause(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void resume() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (state.getState() != State.PAUSE) {
                LOGGER.log(Level.WARNING,
                        "Transport is not in PAUSE state!");
            }
            state.setState(State.START);
            notifyProbesResume(this);
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(int port) throws IOException {
        return bind(new InetSocketAddress(port));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(String host, int port)
            throws IOException {
        return bind(host, port, 50);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(String host, int port, int backlog)
            throws IOException {
        return bind(new InetSocketAddress(host, port), backlog);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress)
            throws IOException {
        return bind(socketAddress, 4096);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public TCPNIOServerConnection bind(SocketAddress socketAddress, int backlog)
            throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            final TCPNIOServerConnection serverConnection =
                    obtainServerNIOConnection(serverSocketChannel);

            serverConnections.add(serverConnection);
            
            ServerSocket serverSocket = serverSocketChannel.socket();
            serverSocket.setReuseAddress(reuseAddress);
            serverSocket.setSoTimeout(serverSocketSoTimeout);

            serverSocket.bind(socketAddress, backlog);

            serverSocketChannel.configureBlocking(false);

            serverConnection.resetAddresses();

            if (!isStopped()) {
                listenServerConnection(serverConnection);
            }

            return serverConnection;
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void unbind(Connection connection) throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            if (connection != null
                    && serverConnections.remove(connection)) {
                final GrizzlyFuture future = connection.close();
                try {
                    future.get(1000, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error unbinding connection: " + connection, e);
                } finally {
                    future.markForRecycle(true);
                }
            }
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    @Override
    public void unbindAll() throws IOException {
        state.getStateLocker().writeLock().lock();

        try {
            for (Connection serverConnection : serverConnections) {
                try {
                    unbind(serverConnection);
                } catch (Exception e) {
                    LOGGER.log(Level.FINE,
                            "Exception occurred when closing server connection: "
                            + serverConnection, e);
                }
            }

            serverConnections.clear();
        } finally {
            state.getStateLocker().writeLock().unlock();
        }
    }

    /**
     * Creates, initializes and connects socket to the specific remote host
     * and port and returns {@link Connection}, representing socket.
     *
     * @param host remote host to connect to.
     * @param port remote port to connect to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect(String host, int port)
            throws IOException {
        return connectorHandler.connect(host, port);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress)
            throws IOException {
        return connectorHandler.connect(remoteAddress);
    }

    /**
     * Creates, initializes and connects socket to the specific
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            CompletionHandler<Connection> completionHandler)
            throws IOException {
        return connectorHandler.connect(remoteAddress, completionHandler);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress) throws IOException {
        return connectorHandler.connect(remoteAddress, localAddress);
    }

    /**
     * Creates, initializes socket, binds it to the specific local and remote
     * {@link SocketAddress} and returns {@link Connection}, representing socket.
     *
     * @param remoteAddress remote address to connect to.
     * @param localAddress local address to bind socket to.
     * @param completionHandler {@link CompletionHandler}.
     * @return {@link GrizzlyFuture} of connect operation, which could be used to get
     * resulting {@link Connection}.
     *
     * @throws java.io.IOException
     */
    public GrizzlyFuture<Connection> connect(SocketAddress remoteAddress,
            SocketAddress localAddress,
            CompletionHandler<Connection> completionHandler)
            throws IOException {
        return connectorHandler.connect(remoteAddress, localAddress,
                completionHandler);
    }

    @Override
    protected void closeConnection(Connection connection) throws IOException {
        SelectableChannel nioChannel = ((NIOConnection) connection).getChannel();

        if (nioChannel != null) {
            try {
                nioChannel.close();
            } catch (IOException e) {
                LOGGER.log(Level.FINE,
                        "TCPNIOTransport.closeChannel exception", e);
            }
        }

        if (asyncQueueIO != null) {
            AsyncQueueReader reader = asyncQueueIO.getReader();
            if (reader != null) {
                reader.onClose(connection);
            }

            AsyncQueueWriter writer = asyncQueueIO.getWriter();
            if (writer != null) {
                writer.onClose(connection);
            }

        }
    }

    TCPNIOConnection obtainNIOConnection(SocketChannel channel) {
        TCPNIOConnection connection = new TCPNIOConnection(this, channel);
        configureNIOConnection(connection);
        
        return connection;
    }

    TCPNIOServerConnection obtainServerNIOConnection(ServerSocketChannel channel) {
        TCPNIOServerConnection connection = new TCPNIOServerConnection(this, channel);
        configureNIOConnection(connection);

        return connection;
    }

    protected void configureNIOConnection(TCPNIOConnection connection) {
        connection.configureBlocking(isBlocking);
        connection.configureStandalone(isStandalone);
        connection.setProcessor(processor);
        connection.setProcessorSelector(processorSelector);
        connection.setMonitoringProbes(connectionMonitoringConfig.getProbes());
    }
    
    /**
     * Configuring <code>SocketChannel</code> according the transport settings
     * @param channel <code>SocketChannel</code> to configure
     * @throws java.io.IOException
     */
    protected void configureChannel(SocketChannel channel) throws IOException {
        Socket socket = channel.socket();

        channel.configureBlocking(false);

        try {
            if (linger >= 0) {
                socket.setSoLinger(true, linger);
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set linger to " + linger, e);
        }

        try {
            socket.setKeepAlive(isKeepAlive);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set keepAlive to " + isKeepAlive, e);
        }
        
        try {
            socket.setTcpNoDelay(tcpNoDelay);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Can not set TcpNoDelay to " + tcpNoDelay, e);
        }
        socket.setReuseAddress(reuseAddress);
    }

    @Override
    public AsyncQueueIO getAsyncQueueIO() {
        return asyncQueueIO;
    }

    @Override
    public synchronized void configureStandalone(boolean isStandalone) {
        if (this.isStandalone != isStandalone) {
            this.isStandalone = isStandalone;
            if (isStandalone) {
                processor = StandaloneProcessor.INSTANCE;
                processorSelector = StandaloneProcessorSelector.INSTANCE;
            } else {
                processor = null;
                processorSelector = null;
            }
        }
    }

    public int getLinger() {
        return linger;
    }

    public void setLinger(int linger) {
        this.linger = linger;
        notifyProbesConfigChanged(this);
    }

    public boolean isKeepAlive() {
        return isKeepAlive;
    }

    public void setKeepAlive(boolean isKeepAlive) {
        this.isKeepAlive = isKeepAlive;
        notifyProbesConfigChanged(this);
    }

    public boolean isReuseAddress() {
        return reuseAddress;
    }

    public void setReuseAddress(boolean reuseAddress) {
        this.reuseAddress = reuseAddress;
        notifyProbesConfigChanged(this);
    }

    public int getClientSocketSoTimeout() {
        return clientSocketSoTimeout;
    }

    public void setClientSocketSoTimeout(int socketTimeout) {
        this.clientSocketSoTimeout = socketTimeout;
        notifyProbesConfigChanged(this);
    }

    public int getConnectionTimeout() {
        return connectionTimeout;
    }

    public void setConnectionTimeout(int connectionTimeout) {
        this.connectionTimeout = connectionTimeout;
        notifyProbesConfigChanged(this);
    }

    public boolean isTcpNoDelay() {
        return tcpNoDelay;
    }

    public void setTcpNoDelay(boolean tcpNoDelay) {
        this.tcpNoDelay = tcpNoDelay;
        notifyProbesConfigChanged(this);
    }

    public int getServerSocketSoTimeout() {
        return serverSocketSoTimeout;
    }

    public void setServerSocketSoTimeout(int serverSocketSoTimeout) {
        this.serverSocketSoTimeout = serverSocketSoTimeout;
        notifyProbesConfigChanged(this);
    }

    @Override
    public Filter getTransportFilter() {
        return defaultTransportFilter;
    }

    @Override
    public TemporarySelectorIO getTemporarySelectorIO() {
        return temporarySelectorIO;
    }

    @Override
    public void setTemporarySelectorIO(TemporarySelectorIO temporarySelectorIO) {
        this.temporarySelectorIO = temporarySelectorIO;
        notifyProbesConfigChanged(this);
    }

    @Override
    public IOEventReg fireIOEvent(final IOEvent ioEvent,
            final Connection connection, final PostProcessor postProcessor)
            throws IOException {

        try {
            if (ioEvent == IOEvent.SERVER_ACCEPT) {
                ((TCPNIOServerConnection) connection).onAccept();
                return IOEventReg.REGISTER;
            } else if (ioEvent == IOEvent.CLIENT_CONNECTED) {
                ((TCPNIOConnection) connection).onConnect();
                return IOEventReg.REGISTER;
            }
            
            final Processor conProcessor = connection.obtainProcessor(ioEvent);

                if (ProcessorExecutor.execute(connection, ioEvent,
                        conProcessor, postProcessor)) {
                    return IOEventReg.REGISTER;
                } else {
                    return IOEventReg.DEREGISTER;
                }
        } catch (IOException e) {
            LOGGER.log(Level.FINE, "IOException occurred on fireIOEvent(). "
                    + "Connection={0} event={1}", new Object[] {connection, ioEvent});
            throw e;
        } catch (Exception e) {
            String text = new StringBuilder(256).append("Unexpected exception occurred fireIOEvent().").
                    append("connection=").append(connection).
                    append(" event=").append(ioEvent).toString();

            LOGGER.log(Level.WARNING, text, e);
            throw new IOException(e.getClass() + ": " + text);
        }
    }
    
    /**
     * {@inheritDoc}
     */
    @Override
    public Reader getReader(final Connection connection) {
        return getReader(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Reader getReader(final boolean isBlocking) {
        if (isBlocking) {
            return getTemporarySelectorIO().getReader();
        } else {
            return getAsyncQueueIO().getReader();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer getWriter(final Connection connection) {
        return getWriter(connection.isBlocking());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Writer getWriter(final boolean isBlocking) {
        if (isBlocking) {
            return getTemporarySelectorIO().getWriter();
        } else {
            return getAsyncQueueIO().getWriter();
        }
    }

    public Buffer read(final Connection connection, Buffer buffer)
            throws IOException {

        final Thread currentThread = Thread.currentThread();
        final boolean isSelectorThread = (currentThread instanceof WorkerThread) &&
                ((WorkerThread) currentThread).isSelectorThread();
        
        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int read;

        final boolean isAllocate = (buffer == null);
        if (isAllocate) {
            buffer = memoryManager.allocate(connection.getReadBufferSize());
            final ByteBuffer byteBuffer = buffer.toByteBuffer();
            
            try {
                final SocketChannel socketChannel =
                        (SocketChannel) tcpConnection.getChannel();
                if (!isSelectorThread) {
                    read = doReadInLoop(socketChannel, byteBuffer);
                } else {
                    read = socketChannel.read(byteBuffer);
                }

                tcpConnection.onRead(buffer, read);
            } catch (Exception e) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPNIOConnection (" + connection + ") (allocated) read exception", e);
                }
                read = -1;
            }

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (allocated) read {1} bytes",
                        new Object[]{connection, read});
            }
            
            if (read <= 0) {
                buffer.dispose();
                buffer = null;
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        } else {
            if (buffer.hasRemaining()) {
                final SocketChannel socketChannel =
                        (SocketChannel) tcpConnection.getChannel();
                
                if (buffer.isComposite()) {
                    final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();

                    if (!isSelectorThread) {
                        read = doReadInLoop(socketChannel, byteBuffers);
                    } else {
                        read = (int) socketChannel.read(byteBuffers);
                    }

                    if (read > 0) {
                        resetByteBuffers(byteBuffers, read);
                        buffer.position(buffer.position() + read);
                    }
                } else {
                    final ByteBuffer byteBuffer = buffer.toByteBuffer();
                    if (!isSelectorThread) {
                        read = doReadInLoop(socketChannel, byteBuffer);
                    } else {
                        read = socketChannel.read(byteBuffer);
                    }
                }

                tcpConnection.onRead(buffer, read);
                
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (nonallocated) read {1} bytes", new Object[] {connection, read});
                }
                
                if (read < 0) {
                    throw new EOFException();
                }
            }
        }

        return buffer;
    }

    private int doReadInLoop(SocketChannel socketChannel,
            ByteBuffer byteBuffer) throws IOException {
        int read = 0;
        int readAttempt = 0;
        int readNow;
        while ((readNow = socketChannel.read(byteBuffer)) >= 0) {
            read += readNow;
            if (!byteBuffer.hasRemaining()
                    || ++readAttempt >= maxReadAttempts) {
                return read;
            }
        }

        if (read == 0) {
            // Assign last readNow (may be -1)
            read = readNow;
        }

        return read;
    }
    
    private int doReadInLoop(SocketChannel socketChannel,
            ByteBuffer[] byteBuffers) throws IOException {
        
        int read = 0;
        int readAttempt = 0;
        int readNow;
        final ByteBuffer lastByteBuffer = byteBuffers[byteBuffers.length - 1];
        
        while ((readNow = (int) socketChannel.read(byteBuffers)) >= 0) {
            read += readNow;
            if (!lastByteBuffer.hasRemaining()
                    || ++readAttempt >= maxReadAttempts) {
                return read;
            }
        }

        if (read == 0) {
            // Assign last readNow (may be -1)
            read = readNow;
        }
        
        return read;
    }

    public int write(Connection connection, Buffer buffer) throws IOException {
        return write(connection, buffer, null);
    }

    public int write(Connection connection, Buffer buffer,
            WriteResult currentResult) throws IOException {

        final TCPNIOConnection tcpConnection = (TCPNIOConnection) connection;
        int written;
        if (buffer.isComposite()) {
            final ByteBuffer[] byteBuffers = buffer.toByteBufferArray();
            written = (int) ((SocketChannel) tcpConnection.getChannel()).write(byteBuffers);

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (composite) write {1} bytes",
                        new Object[]{connection, written});
            }

            if (written > 0) {
                resetByteBuffers(byteBuffers, written);
                buffer.position(buffer.position() + written);
            }
        } else {
            final ByteBuffer byteBuffer = buffer.toByteBuffer();

            written = ((SocketChannel) tcpConnection.getChannel()).write(byteBuffer);
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "TCPNIOConnection ({0}) (plain) write {1} bytes",
                        new Object[]{connection, written});
            }
        }

        tcpConnection.onWrite(buffer, written);

        if (written >= 0) {
            if (currentResult != null) {
                currentResult.setMessage(buffer);
                currentResult.setWrittenSize(currentResult.getWrittenSize()
                        + written);
                currentResult.setDstAddress(
                        connection.getPeerAddress());
            }
        } else {
            throw new IOException("Error writing to peer");
        }

        return written;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected JmxObject createJmxManagementObject() {
        return new org.glassfish.grizzly.nio.transport.jmx.TCPNIOTransport(this);
    }

    private static void resetByteBuffers(final ByteBuffer[] byteBuffers, int processed) {
        int index = 0;
        while(processed > 0) {
            final ByteBuffer byteBuffer = byteBuffers[index++];
            byteBuffer.position(0);
            processed -= byteBuffer.remaining();
        }
    }

    class RegisterChannelCompletionHandler
            extends EmptyCompletionHandler<RegisterChannelResult> {

        @Override
        public void completed(final RegisterChannelResult result) {
            try {
                final SelectionKey selectionKey = result.getSelectionKey();

                final TCPNIOConnection connection =
                        (TCPNIOConnection) getSelectionKeyHandler().
                        getConnectionForKey(selectionKey);

                if (connection != null) {
                    final SelectorRunner selectorRunner = result.getSelectorRunner();
                    connection.setSelectionKey(selectionKey);
                    connection.setSelectorRunner(selectorRunner);
                }
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Exception happened, when " +
                        "trying to register the channel", e);
            }
        }
    }

    /**
     * Transport default {@link TCPNIOConnectorHandler}.
     */
     class TransportConnectorHandler extends TCPNIOConnectorHandler {
        public TransportConnectorHandler() {
            super(TCPNIOTransport.this);
        }

        @Override
        public Processor getProcessor() {
            return TCPNIOTransport.this.getProcessor();
        }

        @Override
        public ProcessorSelector getProcessorSelector() {
            return TCPNIOTransport.this.getProcessorSelector();
        }
    }
}
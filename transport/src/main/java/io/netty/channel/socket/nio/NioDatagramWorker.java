/*
 * Copyright 2011 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.channel.socket.nio;

import static io.netty.channel.Channels.fireChannelDisconnected;
import static io.netty.channel.Channels.fireChannelDisconnectedLater;
import static io.netty.channel.Channels.fireExceptionCaught;
import static io.netty.channel.Channels.fireExceptionCaughtLater;
import static io.netty.channel.Channels.fireMessageReceived;
import static io.netty.channel.Channels.succeededFuture;
import io.netty.buffer.ChannelBufferFactory;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ReceiveBufferSizePredictor;

import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.util.concurrent.Executor;

/**
 * A class responsible for registering channels with {@link Selector}.
 * It also implements the {@link Selector} loop.
 */
public class NioDatagramWorker extends AbstractNioWorker {

    /**
     * Sole constructor.
     *
     * @param executor the {@link Executor} used to execute {@link Runnable}s
     *                 such as {@link ChannelRegistionTask}
     */
    NioDatagramWorker(final Executor executor) {
        super(executor);
    }

    NioDatagramWorker(final Executor executor, boolean allowShutdownOnIdle) {
        super(executor, allowShutdownOnIdle);
    }
    
    @Override
    protected boolean read(final SelectionKey key) {
        final NioDatagramChannel channel = (NioDatagramChannel) key.attachment();
        ReceiveBufferSizePredictor predictor =
            channel.getConfig().getReceiveBufferSizePredictor();
        final ChannelBufferFactory bufferFactory = channel.getConfig().getBufferFactory();
        final DatagramChannel nioChannel = (DatagramChannel) key.channel();

        // Allocating a non-direct buffer with a max udp packge size.
        // Would using a direct buffer be more efficient or would this negatively
        // effect performance, as direct buffer allocation has a higher upfront cost
        // where as a ByteBuffer is heap allocated.
        final ByteBuffer byteBuffer = ByteBuffer.allocate(
                predictor.nextReceiveBufferSize()).order(bufferFactory.getDefaultOrder());

        boolean failure = true;
        SocketAddress remoteAddress = null;
        try {
            // Receive from the channel in a non blocking mode. We have already been notified that
            // the channel is ready to receive.
            remoteAddress = nioChannel.receive(byteBuffer);
            failure = false;
        } catch (ClosedChannelException e) {
            // Can happen, and does not need a user attention.
        } catch (Throwable t) {
            fireExceptionCaught(channel, t);
        }

        if (remoteAddress != null) {
            // Flip the buffer so that we can wrap it.
            byteBuffer.flip();

            int readBytes = byteBuffer.remaining();
            if (readBytes > 0) {
                // Update the predictor.
                predictor.previousReceiveBufferSize(readBytes);

                // Notify the interested parties about the newly arrived message.
                fireMessageReceived(
                        channel, bufferFactory.getBuffer(byteBuffer), remoteAddress);
            }
        }

        if (failure) {
            key.cancel(); // Some JDK implementations run into an infinite loop without this.
            close(channel, succeededFuture(channel));
            return false;
        }

        return true;
    }

    void disconnect(NioDatagramChannel channel, ChannelFuture future) {
        boolean connected = channel.isConnected();
        boolean iothread = isIoThread();
        try {
            channel.getJdkChannel().disconnectSocket();
            future.setSuccess();
            if (connected) {
                if (iothread) {
                    fireChannelDisconnected(channel);
                } else {
                    fireChannelDisconnectedLater(channel);
                }
            }
        } catch (Throwable t) {
            future.setFailure(t);
            if (iothread) {
                fireExceptionCaught(channel, t);
            } else {
                fireExceptionCaughtLater(channel, t);
            }
        }
    }


    @Override
    protected void registerTask(AbstractNioChannel channel, ChannelFuture future) {
        final SocketAddress localAddress = channel.getLocalAddress();
        if (localAddress == null) {
            if (future != null) {
                future.setFailure(new ClosedChannelException());
            }
            close(channel, succeededFuture(channel));
            return;
        }

        try {
            synchronized (channel.interestOpsLock) {
                channel.getJdkChannel().register(
                        selector, channel.getRawInterestOps(), channel);
            }
            if (future != null) {
                future.setSuccess();
            }
        } catch (final ClosedChannelException e) {
            if (future != null) {
                future.setFailure(e);
            }
            close(channel, succeededFuture(channel));
            throw new ChannelException(
                    "Failed to register a socket to the selector.", e);
        }
    }


}

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

import static io.netty.channel.Channels.fireChannelOpen;
import io.netty.channel.ChannelException;
import io.netty.channel.ChannelFactory;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelSink;
import io.netty.channel.socket.DatagramChannelConfig;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;

/**
 * Provides an NIO based {@link io.netty.channel.socket.DatagramChannel}.
 */
public final class NioDatagramChannel extends AbstractNioChannel implements io.netty.channel.socket.DatagramChannel {

    /**
     * The {@link DatagramChannelConfig}.
     */
    private final NioDatagramChannelConfig config;

   
    static NioDatagramChannel create(ChannelFactory factory,
            ChannelPipeline pipeline, ChannelSink sink, NioDatagramWorker worker) {
        NioDatagramChannel instance =
                new NioDatagramChannel(factory, pipeline, sink, worker);
        fireChannelOpen(instance);
        return instance;
    }

    private NioDatagramChannel(final ChannelFactory factory,
            final ChannelPipeline pipeline, final ChannelSink sink,
            final NioDatagramWorker worker) {
        super(null, factory, pipeline, sink, worker, new NioDatagramJdkChannel(openNonBlockingChannel()));
        config = new DefaultNioDatagramChannelConfig(getJdkChannel().getChannel().socket());
    }

    private static DatagramChannel openNonBlockingChannel() {
        try {
            final DatagramChannel channel = DatagramChannel.open();
            channel.configureBlocking(false);
            return channel;
        } catch (final IOException e) {
            throw new ChannelException("Failed to open a DatagramChannel.", e);
        }
    }


    @Override
    protected NioDatagramJdkChannel getJdkChannel() {
        return (NioDatagramJdkChannel) super.getJdkChannel();
    }

    @Override
    public NioDatagramWorker getWorker() {
        return (NioDatagramWorker) super.getWorker();
    }

    @Override
    public boolean isBound() {
        return isOpen() && getJdkChannel().isSocketBound();
    }

    @Override
    public boolean isConnected() {
        return getJdkChannel().isConnected();
    }

    @Override
    protected boolean setClosed() {
        return super.setClosed();
    }

    @Override
    public NioDatagramChannelConfig getConfig() {
        return config;
    }

    @Override
    public void joinGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void joinGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void leaveGroup(InetAddress multicastAddress) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void leaveGroup(InetSocketAddress multicastAddress,
            NetworkInterface networkInterface) {
        throw new UnsupportedOperationException();
    }



    @Override
    public ChannelFuture write(Object message, SocketAddress remoteAddress) {
        if (remoteAddress == null || remoteAddress.equals(getRemoteAddress())) {
            return super.write(message, null);
        } else {
            return super.write(message, remoteAddress);
        }

    }
}

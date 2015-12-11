package com.github.pukkaone.gelf.protocol;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.ConcurrentSet;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

public class GelfNettyTCPSender extends GelfSender {
    private final AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool> poolMap;
    private final InetSocketAddress address;
    private boolean shutdown;
    private Set<String> usedChannels = new ConcurrentSet<>();
    private AtomicLong offerredCount = new AtomicLong(0);
    private AtomicLong completedCount = new AtomicLong(0);

    public GelfNettyTCPSender(int nThreads, String host, int port) throws IOException {
        EventLoopGroup group = new NioEventLoopGroup(nThreads);
        final Bootstrap cb = new Bootstrap()
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.SO_LINGER, 0);

        cb.group(group).channel(NioSocketChannel.class);

        poolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
            @Override
            protected SimpleChannelPool newPool(InetSocketAddress key) {
                return new SimpleChannelPool(cb.remoteAddress(key), new AbstractChannelPoolHandler() {
                    @Override
                    public void channelCreated(Channel ch) throws Exception {
                        if (usedChannels.size() < 1000) {
                            usedChannels.add(ch.toString());
                        }
//                        System.out.println("Channel created: " + ch);
                    }

                    @Override
                    public void channelReleased(Channel ch) throws Exception {
//                        System.out.println("Channel released: " + ch);
                    }

                    @Override
                    public void channelAcquired(Channel ch) throws Exception {
//                        System.out.println("Channel acquired: " + ch);
                    }
                });
            }
        };

        address = new InetSocketAddress(host, port);
    }

    public InetSocketAddress getAddress() {
        return address;
    }

    public Set<String> getUsedChannels() {
        return usedChannels;
    }

    @Override
    public boolean sendMessage(final GelfMessage message) {
        if (shutdown || !message.isValid()) {
            return false;
        }

        try {
            final SimpleChannelPool pool = poolMap.get(address);
            Future<Channel> f = pool.acquire();
            offerredCount.incrementAndGet();
            f.addListener(new FutureListener<Channel>() {
                @Override
                public void operationComplete(Future<Channel> f) {
                    if (f.isSuccess()) {
                        try {
                            Channel ch = f.getNow();
                            ch.writeAndFlush(Unpooled.copiedBuffer(message.toJson() + '\0', CharsetUtil.UTF_8));
                            // Release back to pool
                            pool.release(ch);
                        } finally {
                            completedCount.incrementAndGet();
                        }
                    }
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public long getOfferredCount() {
        return offerredCount.longValue();
    }

    public long getCompletedCount() {
        return completedCount.longValue();
    }

    public long getPendingCount() {
        return getOfferredCount() - getCompletedCount();
    }

    @Override
    public void close() {
        shutdown = true;
        poolMap.close();
    }
}
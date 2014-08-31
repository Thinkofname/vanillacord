package uk.co.thinkofdeath.vanillacord.util;

import io.netty.channel.Channel;

import java.net.SocketAddress;

public interface INetworkManager {

    SocketAddress getAddress();

    void setAddress(SocketAddress socketAddress);

    Channel getChannel();
}
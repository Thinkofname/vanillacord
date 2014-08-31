package uk.co.thinkofdeath.vanillacord.util;

import com.google.gson.Gson;
import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import io.netty.util.AttributeKey;

import java.net.InetSocketAddress;
import java.util.UUID;

public class BungeeHelper {

    private static final Gson gson = new Gson();
    public static AttributeKey<UUID> UUID_KEY = new AttributeKey<>("spoofed-uuid");
    public static AttributeKey<Property[]> PROPERTIES_KEY = new AttributeKey<>("spoofed-props");

    public static void parseHandshake(INetworkManager networkManager, IHandshakePacket handshake) {
        String host = handshake.getHostname();

        String[] split = host.split("\00");
        if (split.length != 4) {
            throw new RuntimeException("If you wish to use IP forwarding, please enable it in your BungeeCord config as well!");
        }

        // split[0]; Vanilla doesn't use this
        networkManager.setAddress(new InetSocketAddress(split[1], ((InetSocketAddress) networkManager.getAddress()).getPort()));

        String uuid = split[2];
        networkManager.getChannel()
            .attr(UUID_KEY).set(UUID.fromString(uuid.substring(0, 8) + "-" + uuid.substring(8, 12) + "-" + uuid.substring(12, 16) + "-" + uuid.substring(16, 20) + "-" + uuid.substring(20, 32)));

        networkManager.getChannel()
            .attr(PROPERTIES_KEY).set(gson.fromJson(split[3], Property[].class));
    }

    public static GameProfile injectProfile(INetworkManager networkManager, GameProfile gameProfile) {
        GameProfile profile = new GameProfile(networkManager.getChannel().attr(UUID_KEY).get(), gameProfile.getName());
        for (Property property : networkManager.getChannel().attr(PROPERTIES_KEY).get()) {
            profile.getProperties().put(property.getName(), property);
        }
        return profile;
    }
}

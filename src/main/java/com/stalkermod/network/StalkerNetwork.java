package com.stalkermod.network;

import com.stalkermod.StalkerMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public class StalkerNetwork {
    private static final String PROTOCOL = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(StalkerMod.MOD_ID, "main"),
            () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);

    private static boolean registered;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        CHANNEL.registerMessage(0, JumpscarePacket.class,
                JumpscarePacket::encode, JumpscarePacket::decode, JumpscarePacket::handle);
    }

    public static void sendJumpscare(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new JumpscarePacket());
    }
}

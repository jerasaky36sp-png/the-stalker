package com.stalkermod.network;

import com.stalkermod.client.ClientJumpscare;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server -> client: show the full-screen jumpscare image. Carries no data. */
public class JumpscarePacket {
    public static void encode(JumpscarePacket msg, FriendlyByteBuf buf) {
    }

    public static JumpscarePacket decode(FriendlyByteBuf buf) {
        return new JumpscarePacket();
    }

    public static void handle(JumpscarePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientJumpscare::start));
        ctx.get().setPacketHandled(true);
    }
}

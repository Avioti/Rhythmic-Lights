package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.util.RhythmConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Server -> Client packet to open the URL input screen.
 */
public record OpenURLScreenPacket(
    String currentUrl,
    String currentTitle,
    int currentDuration,
    boolean currentLoop
) implements CustomPacketPayload {

    // ==================== Compact Constructor ====================

    public OpenURLScreenPacket {
        currentUrl = currentUrl != null ? currentUrl : "";
        currentTitle = currentTitle != null ? currentTitle : "";
    }

    // ==================== Packet Registration ====================

    public static final Type<OpenURLScreenPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "open_url_screen"));

    public static final StreamCodec<FriendlyByteBuf, OpenURLScreenPacket> CODEC = StreamCodec.of(
        OpenURLScreenPacket::encode,
        OpenURLScreenPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, OpenURLScreenPacket packet) {
        buf.writeUtf(packet.currentUrl);
        buf.writeUtf(packet.currentTitle);
        buf.writeInt(packet.currentDuration);
        buf.writeBoolean(packet.currentLoop);
    }

    private static OpenURLScreenPacket decode(FriendlyByteBuf buf) {
        return new OpenURLScreenPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Server Helper ====================

    public static void sendToPlayer(ServerPlayer player, String url, String title, int duration, boolean loop) {
        OpenURLScreenPacket packet = new OpenURLScreenPacket(url, title, duration, loop);
        PacketSender.getInstance().sendToPlayer(player, packet);

        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.debug("[OpenURLScreen] Sent to {}", player.getName().getString());
        }
    }
}


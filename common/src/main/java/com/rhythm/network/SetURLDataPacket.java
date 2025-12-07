package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.item.RhythmURLDisc;
import com.rhythm.util.RhythmConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import java.net.URI;
import java.util.stream.Stream;

/**
 * Client -> Server packet to set URL data on a RhythmURL disc.
 */
public record SetURLDataPacket(
    String url,
    String title,
    int duration,
    boolean loop,
    boolean lock
) implements CustomPacketPayload {

    // ==================== Constants ====================

    public static final int MAX_URL_LENGTH = RhythmConstants.MAX_URL_LENGTH;
    public static final int MAX_TITLE_LENGTH = 100;

    private static final float CONFIRM_SOUND_VOLUME = 1.0f;
    private static final float CONFIRM_SOUND_PITCH = 1.2f;

    private static final String MSG_NO_DISC = "Hold a Rhythm URL Disc to set its URL!";
    private static final String MSG_INVALID_URL = "Invalid URL!";
    private static final String MSG_URL_TOO_LONG = "URL is too long!";

    // ==================== Packet Registration ====================

    public static final Type<SetURLDataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "set_url_data"));

    public static final StreamCodec<FriendlyByteBuf, SetURLDataPacket> CODEC = StreamCodec.of(
        SetURLDataPacket::encode,
        SetURLDataPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, SetURLDataPacket packet) {
        buf.writeUtf(packet.url);
        buf.writeUtf(packet.title);
        buf.writeInt(packet.duration);
        buf.writeBoolean(packet.loop);
        buf.writeBoolean(packet.lock);
    }

    private static SetURLDataPacket decode(FriendlyByteBuf buf) {
        return new SetURLDataPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readBoolean()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Server Handler ====================

    public void handleOnServer(ServerPlayer player) {
        ItemStack stack = findUrlDiscInHand(player);

        if (stack == null) {
            sendError(player, MSG_NO_DISC);
            return;
        }

        String validatedUrl = validateUrl(player);
        if (validatedUrl == null) {
            return;
        }

        String validatedTitle = truncateTitle(title);
        applyDiscData(player, stack, validatedUrl, validatedTitle);
    }

    // ==================== Helpers ====================

    private ItemStack findUrlDiscInHand(ServerPlayer player) {
        return Stream.of(InteractionHand.values())
            .map(player::getItemInHand)
            .filter(s -> s.getItem() instanceof RhythmURLDisc)
            .findFirst()
            .orElse(null);
    }

    private String validateUrl(ServerPlayer player) {
        try {
            String validated = new URI(url).toURL().toString();

            if (validated.length() > MAX_URL_LENGTH) {
                sendError(player, MSG_URL_TOO_LONG);
                return null;
            }

            return validated;
        } catch (Exception e) {
            sendError(player, MSG_INVALID_URL);
            return null;
        }
    }

    private String truncateTitle(String rawTitle) {
        String result = rawTitle != null ? rawTitle : "";
        return result.length() > MAX_TITLE_LENGTH
            ? result.substring(0, MAX_TITLE_LENGTH)
            : result;
    }

    private void applyDiscData(ServerPlayer player, ItemStack stack,
                                String validatedUrl, String validatedTitle) {
        RhythmURLDisc.setDiscData(stack, validatedUrl, validatedTitle, duration, loop, lock);
        playConfirmationSound(player);

        RhythmConstants.LOGGER.info("Player {} configured URL disc: {}",
            player.getName().getString(), validatedUrl);
    }

    private void sendError(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }

    private void playConfirmationSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER,
            CONFIRM_SOUND_VOLUME, CONFIRM_SOUND_PITCH);
    }
}


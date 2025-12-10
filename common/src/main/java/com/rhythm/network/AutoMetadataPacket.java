package com.rhythm.network;

import com.rhythm.RhythmMod;
import com.rhythm.item.RhythmURLDisc;
import com.rhythm.util.RhythmConstants;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

/**
 * Client -> Server packet for automatic URL disc metadata updates.
 * <p>
 * Sent when metadata is fetched from yt-dlp (title, artist, duration).
 */
public record AutoMetadataPacket(
    String url,
    String title,
    String artist,
    int duration
) implements CustomPacketPayload {

    // ==================== Constants ====================

    private static final String ARTIST_TITLE_SEPARATOR = " - ";
    private static final float CONFIRM_SOUND_VOLUME = 0.5f;
    private static final float CONFIRM_SOUND_PITCH = 1.5f;

    // ==================== Packet Registration ====================

    public static final Type<AutoMetadataPacket> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(RhythmMod.MOD_ID, "auto_metadata"));

    public static final StreamCodec<FriendlyByteBuf, AutoMetadataPacket> CODEC = StreamCodec.of(
        AutoMetadataPacket::encode,
        AutoMetadataPacket::decode
    );

    private static void encode(FriendlyByteBuf buf, AutoMetadataPacket packet) {
        buf.writeUtf(packet.url);
        buf.writeUtf(packet.title);
        buf.writeUtf(packet.artist);
        buf.writeInt(packet.duration);
    }

    private static AutoMetadataPacket decode(FriendlyByteBuf buf) {
        return new AutoMetadataPacket(
            buf.readUtf(),
            buf.readUtf(),
            buf.readUtf(),
            buf.readInt()
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ==================== Server Handler ====================

    /**
     * Handle this packet on the server side.
     */
    public void handleOnServer(ServerPlayer player) {
        ItemStack stack = RhythmURLDisc.findInPlayerHand(player);

        if (stack == null) {
            logWarning("Player not holding a URL disc");
            return;
        }

        if (!isUrlMatching(stack)) {
            logWarning("URL mismatch, ignoring. Expected: {}, Got: {}", RhythmURLDisc.getUrl(stack), url);
            return;
        }

        applyMetadataToDisc(player, stack);
    }

    // ==================== Helpers ====================


    private boolean isUrlMatching(ItemStack stack) {
        String existingUrl = RhythmURLDisc.getUrl(stack);
        return url.equals(existingUrl);
    }

    private void applyMetadataToDisc(ServerPlayer player, ItemStack stack) {
        String displayTitle = buildDisplayTitle();
        RhythmURLDisc.setDiscData(stack, url, displayTitle, duration, false, false);
        playConfirmationSound(player);
        logInfo("Auto-updated disc metadata: {} ({}s)", displayTitle, duration);
    }

    private String buildDisplayTitle() {
        if (artist != null && !artist.isEmpty()) {
            return artist + ARTIST_TITLE_SEPARATOR + title;
        }
        return title;
    }

    private void playConfirmationSound(ServerPlayer player) {
        player.playNotifySound(SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.MASTER,
            CONFIRM_SOUND_VOLUME, CONFIRM_SOUND_PITCH);
    }

    // ==================== Logging ====================

    private void logWarning(String message, Object... args) {
        if (RhythmConstants.DEBUG_NETWORK) {
            RhythmConstants.LOGGER.warn("[AutoMetadata] " + message, args);
        }
    }

    private void logInfo(String message, Object... args) {
        RhythmConstants.LOGGER.info("[AutoMetadata] " + message, args);
    }
}


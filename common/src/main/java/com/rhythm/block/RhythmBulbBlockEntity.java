package com.rhythm.block;

import com.rhythm.RhythmMod;
import com.rhythm.audio.ClientSongManager;
import com.rhythm.audio.FrequencyChannel;
import com.rhythm.audio.FrequencyData;
import com.rhythm.audio.PlaybackState;
import com.rhythm.client.light.ColoredLightRegistry;
import com.rhythm.particle.ColoredParticleEffect;
import com.rhythm.util.RhythmConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for Rhythm Bulb - stores controller link, frequency channel, and color.
 * <p>
 * Features:
 * <ul>
 *   <li>Client tick support for synced particle effects</li>
 *   <li>Smooth brightness transitions</li>
 *   <li>Colored light overlay via shader</li>
 *   <li>Redstone power override support</li>
 * </ul>
 */
public class RhythmBulbBlockEntity extends BlockEntity {

    // ==================== Constants ====================

    private static final int DEFAULT_COLOR = 0xFFFFFF;
    private static final float DEFAULT_LIGHT_RADIUS = 8.0f;
    private static final float INITIAL_INTENSITY = 0.0f;
    private static final float FULL_INTENSITY = 1.0f;

    // Loading animation settings
    private static final float LOADING_BASE_INTENSITY = 0.3f;
    private static final float LOADING_AMPLITUDE = 0.2f;
    private static final double LOADING_CYCLE_SPEED = Math.PI / 10.0;

    // Particle settings
    private static final int PARTICLE_BASE_LIFETIME = 12;
    private static final int PARTICLE_INTENSITY_BONUS = 8;
    private static final float HIGH_INTENSITY_THRESHOLD = 0.5f;
    private static final float MEDIUM_INTENSITY_THRESHOLD = 0.3f;
    private static final float STRONG_BEAT_THRESHOLD = 0.7f;
    private static final int HIGH_INTENSITY_PARTICLE_COUNT = 6;
    private static final int MEDIUM_INTENSITY_PARTICLE_COUNT = 4;
    private static final int LOW_INTENSITY_PARTICLE_COUNT = 3;
    private static final int STRONG_BEAT_EXTRA_PARTICLES = 5;

    // NBT keys
    private static final String NBT_CONTROLLER_POS = "ControllerPos";
    private static final String NBT_CHANNEL = "Channel";
    private static final String NBT_COLOR = "Color";
    private static final String NBT_REDSTONE_POWERED = "RedstonePowered";

    // ==================== State Fields ====================

    private BlockPos controllerPos = null;
    private FrequencyChannel channel = FrequencyChannel.BASS;
    private int color = DEFAULT_COLOR;
    private boolean redstonePowered = false;

    // Client-side rendering state
    private float currentBrightness = 0.0f;
    private float lastIntensity = 0.0f;

    // ==================== Constructor ====================

    public RhythmBulbBlockEntity(BlockPos pos, BlockState state) {
        super(RhythmMod.RHYTHM_BULB_BLOCK_ENTITY.get(), pos, state);
        this.color = this.channel.getDefaultColor();
    }

    // ==================== Controller Link ====================

    public void setControllerPos(BlockPos pos) {
        this.controllerPos = pos;
        syncToClients();
    }

    @Nullable
    public BlockPos getControllerPos() {
        return controllerPos;
    }

    public boolean isLinked() {
        return controllerPos != null;
    }

    // ==================== Channel & Color ====================

    public FrequencyChannel getChannel() {
        return channel;
    }

    public void setChannel(FrequencyChannel channel) {
        this.channel = channel;
        this.color = channel.getDefaultColor();
        syncToClients();
        updateColoredLightOnClient();
    }

    public int getColor() {
        return color;
    }

    public void setColor(int color) {
        this.color = color;
        syncToClients();
        updateColoredLightOnClient();
    }

    // ==================== Redstone Power ====================

    public boolean isRedstonePowered() {
        return redstonePowered;
    }

    public void setRedstonePowered(boolean powered) {
        boolean wasChanged = this.redstonePowered != powered;
        this.redstonePowered = powered;
        syncToClients();

        if (level != null && level.isClientSide && wasChanged) {
            updateLightState(powered ? FULL_INTENSITY : INITIAL_INTENSITY);
        }
    }

    // ==================== Sync Helpers ====================

    private void syncToClients() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private void updateColoredLightOnClient() {
        if (level != null && level.isClientSide) {
            ColoredLightRegistry.getInstance().removeLight(worldPosition);
            registerColoredLight();
        }
    }

    // ==================== Brightness Management ====================

    public float getCurrentBrightness() {
        return currentBrightness;
    }

    public void updateBrightness(float targetIntensity, float deltaTime) {
        currentBrightness = targetIntensity;
    }

    public float getLastIntensity() {
        return lastIntensity;
    }

    // ==================== NBT Serialization ====================

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong(NBT_CONTROLLER_POS, controllerPos.asLong());
        }
        tag.putInt(NBT_CHANNEL, channel.getId());
        tag.putInt(NBT_COLOR, color);
        tag.putBoolean(NBT_REDSTONE_POWERED, redstonePowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        loadControllerPos(tag);
        loadChannel(tag);
        loadColor(tag);
        loadRedstonePowered(tag);
        onLoad();
    }

    private void loadControllerPos(CompoundTag tag) {
        if (tag.contains(NBT_CONTROLLER_POS)) {
            controllerPos = BlockPos.of(tag.getLong(NBT_CONTROLLER_POS));
        }
    }

    private void loadChannel(CompoundTag tag) {
        if (tag.contains(NBT_CHANNEL)) {
            channel = FrequencyChannel.fromId(tag.getInt(NBT_CHANNEL));
        }
    }

    private void loadColor(CompoundTag tag) {
        if (tag.contains(NBT_COLOR)) {
            color = tag.getInt(NBT_COLOR);
        }
    }

    private void loadRedstonePowered(CompoundTag tag) {
        if (tag.contains(NBT_REDSTONE_POWERED)) {
            redstonePowered = tag.getBoolean(NBT_REDSTONE_POWERED);
        }
    }

    // ==================== Lifecycle ====================

    @Override
    public void setRemoved() {
        super.setRemoved();
        cleanupColoredLight();
    }

    private void cleanupColoredLight() {
        if (level == null || !level.isClientSide) {
            return;
        }

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Cleaning up colored light at {}", worldPosition);
        }

        ColoredLightRegistry.getInstance().updateLightIntensity(worldPosition, INITIAL_INTENSITY);
        ColoredLightRegistry.getInstance().removeLight(worldPosition);
    }

    private void onLoad() {
        if (level == null || !level.isClientSide) {
            return;
        }

        registerColoredLight();

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Loaded block entity at {} | Channel: {} | Color: #{}",
                worldPosition, channel, String.format("%06X", color));
        }
    }

    /**
     * Registers this bulb as a colored light source.
     */
    public void registerColoredLight() {
        if (level == null || !level.isClientSide) {
            return;
        }

        if (RhythmConstants.DEBUG_BLOCK_ENTITIES) {
            RhythmConstants.LOGGER.debug("Registering colored light at {} | Channel: {} | Color: #{}",
                worldPosition, channel, String.format("%06X", color));
        }

        ColoredLightRegistry.getInstance().registerLight(
            worldPosition,
            color,
            DEFAULT_LIGHT_RADIUS,
            INITIAL_INTENSITY
        );
    }

    // ==================== Light State Management ====================

    private void updateLightState(float intensity) {
        updateLightState(intensity, this.channel);
    }

    private void updateLightState(float intensity, FrequencyChannel channel) {
        if (level == null) {
            return;
        }

        try {
            updateRandomColorIfEnabled(intensity);
            ColoredLightRegistry.getInstance().updateLightIntensity(worldPosition, intensity);
            updateVanillaLightState(intensity, channel);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error in updateLightState at {}: {}", worldPosition, e.getMessage());
        }
    }

    private void updateRandomColorIfEnabled(float intensity) {
        if (RhythmMod.CONFIG.useRandomColors && intensity > 0.1f) {
            int randomColor = RhythmMod.CONFIG.generateRandomColor();
            ColoredLightRegistry.getInstance().updateLightColor(worldPosition, randomColor);
        }
    }

    private void updateVanillaLightState(float intensity, FrequencyChannel channel) {
        float threshold = getLampThreshold(channel);
        boolean shouldBeLit = intensity > threshold;

        BlockState currentState = getBlockState();
        boolean isCurrentlyLit = currentState.getValue(RhythmBulbBlock.LIT);

        if (shouldBeLit != isCurrentlyLit) {
            level.setBlock(worldPosition, currentState.setValue(RhythmBulbBlock.LIT, shouldBeLit), 3);
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    // ==================== Client Tick ====================

    /**
     * Client tick method - called every game tick (20 TPS).
     * Handles music-synced lighting and particle spawning.
     */
    public static void clientTick(Level level, BlockPos pos, BlockState state, RhythmBulbBlockEntity entity) {
        if (!level.isClientSide || entity == null) {
            return;
        }

        try {
            if (entity.isRedstonePowered()) {
                entity.updateLightState(FULL_INTENSITY);
                return;
            }

            Float intensity = calculateIntensityFromMusic(level, entity);
            if (intensity == null) {
                entity.updateLightState(INITIAL_INTENSITY);
                return;
            }

            entity.updateLightState(intensity, entity.getChannel());
            entity.lastIntensity = intensity;

            spawnParticlesIfNeeded(level, pos, entity, intensity);

        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Error in clientTick at {}: {}", pos, e.getMessage());
        }
    }

    @Nullable
    private static Float calculateIntensityFromMusic(Level level, RhythmBulbBlockEntity entity) {
        BlockPos controllerPos = entity.getControllerPos();
        if (controllerPos == null) {
            return null;
        }

        if (!(level.getBlockEntity(controllerPos) instanceof RhythmControllerBlockEntity controller)) {
            return null;
        }

        BlockPos jukeboxPos = controller.getJukeboxPos();
        if (jukeboxPos == null) {
            return null;
        }

        PlaybackState playbackState = ClientSongManager.getInstance().getState(jukeboxPos);
        if (playbackState != PlaybackState.PLAYING) {
            return null;
        }

        FrequencyData freqData = ClientSongManager.getInstance().getFrequencyData(jukeboxPos);
        if (freqData == null) {
            return null;
        }

        if (freqData.isLoading()) {
            return calculateLoadingAnimation(level);
        }

        return calculatePlaybackIntensity(level, jukeboxPos, freqData, entity);
    }

    private static float calculateLoadingAnimation(Level level) {
        long gameTime = level.getGameTime();
        return LOADING_BASE_INTENSITY + LOADING_AMPLITUDE * (float) Math.sin(gameTime * LOADING_CYCLE_SPEED);
    }

    @Nullable
    private static Float calculatePlaybackIntensity(Level level, BlockPos jukeboxPos,
                                                     FrequencyData freqData, RhythmBulbBlockEntity entity) {
        Long playbackStartTime = ClientSongManager.getInstance().getPlaybackStartTime(jukeboxPos);
        if (playbackStartTime == null) {
            playbackStartTime = freqData.getStartTime();
        }

        long ticksSinceSongStart = level.getGameTime() - playbackStartTime;

        if (ticksSinceSongStart < 0 || ticksSinceSongStart >= freqData.getDurationTicks()) {
            return null;
        }

        return getChannelIntensity(freqData, ticksSinceSongStart, entity.getChannel());
    }

    // ==================== Particle Spawning ====================

    private static void spawnParticlesIfNeeded(Level level, BlockPos pos,
                                                RhythmBulbBlockEntity entity, float intensity) {
        float particleThreshold = getParticleThreshold(entity.getChannel());
        if (intensity <= particleThreshold) {
            return;
        }

        double x = pos.getX() + 0.5;
        double y = pos.getY() + 0.5;
        double z = pos.getZ() + 0.5;

        ColoredParticleEffect effect = createParticleEffect(entity, intensity);
        int particleCount = calculateParticleCount(intensity);

        spawnParticles(level, x, y, z, effect, particleCount, intensity);

        if (intensity > STRONG_BEAT_THRESHOLD) {
            spawnExtraParticles(level, x, y, z, effect);
        }
    }

    private static ColoredParticleEffect createParticleEffect(RhythmBulbBlockEntity entity, float intensity) {
        int channelColor = entity.getColor();
        int particleColor = RhythmMod.CONFIG.getParticleColor(channelColor);
        String particleTexture = RhythmMod.CONFIG.getParticleTexture();
        float scale = RhythmMod.CONFIG.getParticleScale();
        int lifetime = PARTICLE_BASE_LIFETIME + (int)(intensity * PARTICLE_INTENSITY_BONUS);

        return new ColoredParticleEffect(particleColor, lifetime, particleTexture, scale, true);
    }

    private static int calculateParticleCount(float intensity) {
        int baseCount;
        if (intensity > HIGH_INTENSITY_THRESHOLD) {
            baseCount = HIGH_INTENSITY_PARTICLE_COUNT;
        } else if (intensity > MEDIUM_INTENSITY_THRESHOLD) {
            baseCount = MEDIUM_INTENSITY_PARTICLE_COUNT;
        } else {
            baseCount = LOW_INTENSITY_PARTICLE_COUNT;
        }
        return baseCount + (int)(intensity * 4.0f);
    }

    private static void spawnParticles(Level level, double x, double y, double z,
                                        ColoredParticleEffect effect, int count, float intensity) {
        for (int i = 0; i < count; i++) {
            double[] velocity = calculateParticleVelocity(intensity, 0.1, 0.2, 0.5, 0.05);
            level.addParticle(effect, x, y, z, velocity[0], velocity[1], velocity[2]);
        }
    }

    private static void spawnExtraParticles(Level level, double x, double y, double z, ColoredParticleEffect effect) {
        for (int i = 0; i < STRONG_BEAT_EXTRA_PARTICLES; i++) {
            double[] velocity = calculateParticleVelocity(1.0f, 0.15, 0.35, 0.6, 0.08);
            level.addParticle(effect, x, y, z, velocity[0], velocity[1], velocity[2]);
        }
    }

    private static double[] calculateParticleVelocity(float intensity, double minSpeed, double speedRange,
                                                       double upwardMultiplier, double upwardBase) {
        double theta = Math.random() * 2 * Math.PI;
        double phi = Math.random() * Math.PI;
        double speed = minSpeed + Math.random() * speedRange * (1.0 + intensity * 0.5);

        double vx = speed * Math.sin(phi) * Math.cos(theta);
        double vy = speed * Math.sin(phi) * Math.sin(theta);
        double vz = speed * Math.cos(phi);

        vy = Math.abs(vy) * upwardMultiplier + upwardBase;

        return new double[]{vx, vy, vz};
    }

    // ==================== Frequency & Threshold Helpers ====================

    private static float getChannelIntensity(FrequencyData data, long tickOffset, FrequencyChannel channel) {
        return switch (channel) {
            case SUB_BASS -> data.getSubBassIntensity(tickOffset);
            case DEEP_BASS -> data.getDeepBassIntensity(tickOffset);
            case BASS -> data.getBassIntensity(tickOffset);
            case LOW_MIDS -> data.getLowMidIntensity(tickOffset);
            case MID_LOWS -> data.getMidLowIntensity(tickOffset);
            case MIDS -> data.getMidIntensity(tickOffset);
            case MID_HIGHS -> data.getMidHighIntensity(tickOffset);
            case HIGH_MIDS -> data.getHighMidIntensity(tickOffset);
            case HIGHS -> data.getHighIntensity(tickOffset);
            case VERY_HIGHS -> data.getVeryHighIntensity(tickOffset);
            case ULTRA -> data.getUltraIntensity(tickOffset);
            case TOP -> data.getTopIntensity(tickOffset);
            case ALL -> calculateAverageIntensity(data, tickOffset);
        };
    }

    private static float calculateAverageIntensity(FrequencyData data, long tickOffset) {
        float sum = data.getSubBassIntensity(tickOffset) + data.getDeepBassIntensity(tickOffset) +
                    data.getBassIntensity(tickOffset) + data.getLowMidIntensity(tickOffset) +
                    data.getMidLowIntensity(tickOffset) + data.getMidIntensity(tickOffset) +
                    data.getMidHighIntensity(tickOffset) + data.getHighMidIntensity(tickOffset) +
                    data.getHighIntensity(tickOffset) + data.getVeryHighIntensity(tickOffset) +
                    data.getUltraIntensity(tickOffset) + data.getTopIntensity(tickOffset);
        return sum / 12.0f;
    }

    /**
     * Gets the lamp activation threshold for a specific channel.
     * Lower frequencies need higher thresholds due to sustained energy.
     */
    private static float getLampThreshold(FrequencyChannel channel) {
        return switch (channel) {
            case SUB_BASS -> 0.20f;
            case DEEP_BASS -> 0.18f;
            case BASS -> 0.15f;
            case LOW_MIDS -> 0.12f;
            case MID_LOWS -> 0.10f;
            case MIDS -> 0.08f;
            case MID_HIGHS -> 0.06f;
            case HIGH_MIDS -> 0.05f;
            case HIGHS -> 0.04f;
            case VERY_HIGHS -> 0.03f;
            case ULTRA -> 0.02f;
            case TOP -> 0.01f;
            case ALL -> 0.10f;
        };
    }

    private static final float PARTICLE_THRESHOLD_BUFFER = 0.05f;

    private static float getParticleThreshold(FrequencyChannel channel) {
        return getLampThreshold(channel) + PARTICLE_THRESHOLD_BUFFER;
    }
}


package com.rhythm.config;

import com.google.gson.*;
import com.rhythm.config.annotation.ConfigComment;
import com.rhythm.config.annotation.SyncToClient;
import com.rhythm.util.RhythmConstants;
import dev.architectury.platform.Platform;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * RhythmMod Configuration Manager.
 * <p>
 * Handles loading, saving, and syncing of configuration values with JSON5 support.
 */
public class ConfigManager {

    // ==================== Constants ====================

    private static final String CONFIG_EXTENSION = ".json5";
    private static final String COMMENT_PREFIX = "// ";
    private static final String NEWLINE = "\n";
    private static final int INDENT_SPACES = 2;
    private static final int ROOT_INDENT = 0;

    // ==================== JSON Serializers ====================

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .serializeNulls()
        .disableHtmlEscaping()
        .create();

    private static final Gson SYNC_GSON = new GsonBuilder()
        .addSerializationExclusionStrategy(new SyncOnlyExclusionStrategy())
        .create();

    // ==================== Public API: Load/Save ====================

    /**
     * Load config from file, or create with defaults if doesn't exist.
     */
    public static <T> T load(Class<T> configClass, String fileName) {
        Path configPath = getConfigPath(fileName);
        File configFile = configPath.toFile();

        try {
            return configFile.exists()
                ? loadExisting(configClass, configFile, fileName)
                : createDefault(configClass, fileName);
        } catch (Exception e) {
            return handleLoadError(configClass, fileName, e);
        }
    }

    /**
     * Save config to file with inline comments.
     */
    public static void save(Object config, String fileName) {
        Path configPath = getConfigPath(fileName);
        File configFile = configPath.toFile();

        try {
            ensureParentDirectoryExists(configFile);
            JsonObject jsonObject = GSON.toJsonTree(config).getAsJsonObject();
            writeConfigFile(configFile, jsonObject, config.getClass());
            RhythmConstants.LOGGER.info("Config saved: {}", fileName);
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to save config: {}", fileName, e);
        }
    }

    // ==================== Public API: Sync ====================

    /**
     * Serialize config for server->client sync (only @SyncToClient fields).
     */
    public static String serializeForSync(Object config) {
        return SYNC_GSON.toJson(config);
    }

    /**
     * Deserialize synced config from server and apply to client.
     */
    @SuppressWarnings("unchecked")
    public static <T> void applySync(T config, String json) {
        try {
            T syncedData = (T) SYNC_GSON.fromJson(json, config.getClass());
            applySyncableFields(config, syncedData);
            RhythmConstants.LOGGER.info("Applied server config sync");
        } catch (Exception e) {
            RhythmConstants.LOGGER.error("Failed to apply config sync", e);
        }
    }

    /**
     * Check if config has any syncable fields.
     */
    public static boolean hasSyncableFields(Object config) {
        return Arrays.stream(getConfigFields(config.getClass()))
            .anyMatch(field -> field.isAnnotationPresent(SyncToClient.class));
    }

    // ==================== Load Helpers ====================

    private static <T> T loadExisting(Class<T> configClass, File configFile, String fileName) throws Exception {
        try (FileReader reader = new FileReader(configFile)) {
            T config = GSON.fromJson(reader, configClass);
            RhythmConstants.LOGGER.info("Config loaded: {}", fileName);
            return config;
        }
    }

    private static <T> T createDefault(Class<T> configClass, String fileName) throws Exception {
        T defaultConfig = configClass.getDeclaredConstructor().newInstance();
        save(defaultConfig, fileName);
        RhythmConstants.LOGGER.info("Created default config: {}", fileName);
        return defaultConfig;
    }

    private static <T> T handleLoadError(Class<T> configClass, String fileName, Exception e) {
        RhythmConstants.LOGGER.error("Failed to load config: {}", fileName, e);
        try {
            return configClass.getDeclaredConstructor().newInstance();
        } catch (Exception ex) {
            throw new RuntimeException("Failed to create default config", ex);
        }
    }

    // ==================== Sync Helpers ====================

    private static <T> void applySyncableFields(T config, T syncedData) throws IllegalAccessException {
        for (Field field : getConfigFields(config.getClass())) {
            if (field.isAnnotationPresent(SyncToClient.class)) {
                field.setAccessible(true);
                field.set(config, field.get(syncedData));
            }
        }
    }

    // ==================== File Operations ====================

    private static Path getConfigPath(String fileName) {
        return Platform.getConfigFolder().resolve(fileName + CONFIG_EXTENSION);
    }

    private static void ensureParentDirectoryExists(File file) {
        File parentDir = file.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            parentDir.mkdirs();
        }
    }

    private static void writeConfigFile(File configFile, JsonObject json, Class<?> configClass) throws IOException {
        try (FileWriter writer = new FileWriter(configFile)) {
            writeJsonWithComments(writer, json, configClass, ROOT_INDENT);
        }
    }

    // ==================== JSON Writing ====================

    private static void writeJsonWithComments(FileWriter writer, JsonObject json,
                                               Class<?> configClass, int indent) throws IOException {
        writer.write("{" + NEWLINE);

        boolean isFirst = true;
        for (String key : json.keySet()) {
            if (!isFirst) {
                writer.write("," + NEWLINE);
            }
            isFirst = false;

            writeFieldComment(writer, configClass, key, indent);
            writeFieldValue(writer, json, key, indent);
        }

        writer.write(NEWLINE);
        writeIndent(writer, indent);
        writer.write("}");
    }

    private static void writeFieldComment(FileWriter writer, Class<?> configClass,
                                           String fieldName, int indent) throws IOException {
        try {
            Field field = configClass.getDeclaredField(fieldName);
            ConfigComment comment = field.getAnnotation(ConfigComment.class);
            if (comment != null) {
                writeCommentLines(writer, comment.value(), indent);
            }
        } catch (NoSuchFieldException ignored) {
            // Field not found in class, skip comment
        }
    }

    private static void writeCommentLines(FileWriter writer, String commentText, int indent) throws IOException {
        writer.write(NEWLINE);
        for (String line : commentText.split(NEWLINE)) {
            writeIndent(writer, indent + 1);
            writer.write(COMMENT_PREFIX + line.trim() + NEWLINE);
        }
    }

    private static void writeFieldValue(FileWriter writer, JsonObject json,
                                         String key, int indent) throws IOException {
        writeIndent(writer, indent + 1);
        writer.write("\"" + key + "\": ");
        writer.write(GSON.toJson(json.get(key)));
    }

    private static void writeIndent(FileWriter writer, int indent) throws IOException {
        int spaces = indent * INDENT_SPACES;
        for (int i = 0; i < spaces; i++) {
            writer.write(" ");
        }
    }

    // ==================== Reflection Utilities ====================

    private static Field[] getConfigFields(Class<?> clazz) {
        return Arrays.stream(clazz.getDeclaredFields())
            .filter(ConfigManager::isConfigField)
            .toArray(Field[]::new);
    }

    private static boolean isConfigField(Field field) {
        int modifiers = field.getModifiers();
        return !Modifier.isStatic(modifiers) && !Modifier.isTransient(modifiers);
    }

    // ==================== Exclusion Strategy ====================

    private static class SyncOnlyExclusionStrategy implements ExclusionStrategy {
        @Override
        public boolean shouldSkipField(FieldAttributes field) {
            return field.getAnnotation(SyncToClient.class) == null;
        }

        @Override
        public boolean shouldSkipClass(Class<?> clazz) {
            return false;
        }
    }
}


package com.rhythm.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config field as syncable from server to client.
 * When a player joins a server with RhythmMod installed,
 * fields marked with @SyncToClient will be sent to the client.
 *
 * Part of RhythmMod's integrated configuration system.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface SyncToClient {
}


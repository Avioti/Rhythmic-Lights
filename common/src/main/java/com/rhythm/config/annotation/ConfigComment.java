package com.rhythm.config.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a config field with a comment that appears in the config file.
 * Part of RhythmMod's integrated configuration system.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigComment {
    /**
     * The comment to display above this config field.
     * Supports multi-line comments via \n.
     */
    String value();
}


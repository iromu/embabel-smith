package com.embabel.smith.studio.rest.dto;

/**
 * A saved pack's listing entry: its name, the goal it exports, its step count, and when it was
 * saved (ISO-8601).
 */
public record StoredPackInfo(String name, String goal, int steps, String savedAt) {
}

package com.embabel.smith.studio.rest.dto;

/** A skill source the studio offers: its URI and kind ({@code folder} or {@code git}). */
public record SourceInfo(String uri, String kind) {
}

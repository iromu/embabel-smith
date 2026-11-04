package com.embabel.smith.unified.dto;

/** A directed canvas edge: {@code from}'s output feeds {@code to}'s input. */
public record Edge(String from, String to) {}

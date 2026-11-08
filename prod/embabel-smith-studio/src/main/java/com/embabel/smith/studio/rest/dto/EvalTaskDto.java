package com.embabel.smith.studio.rest.dto;

/** One acceptance case for evaluating a skill (mirrors the builder's {@code EvalTask}). */
public record EvalTaskDto(String name, String input, String expected) {
}

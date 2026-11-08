package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * An eval request (the Eval pane): the source and skill to evaluate, and an optional task set
 * (a default task is used when omitted). Endpoint-gated — the evaluation runs the skill.
 */
public record EvalRequest(String source, String skill, List<EvalTaskDto> tasks) {
}

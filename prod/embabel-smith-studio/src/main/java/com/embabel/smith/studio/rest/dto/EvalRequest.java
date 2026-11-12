package com.embabel.smith.studio.rest.dto;

import java.util.List;

/**
 * An eval request (the Eval pane): the source and spec to evaluate, and an optional task set
 * (a default task is used when omitted). Endpoint-gated — the evaluation runs the spec.
 */
public record EvalRequest(String source, String spec, List<EvalTaskDto> tasks) {
}

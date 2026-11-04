package com.embabel.smith.unified.dto;

import java.util.List;

/**
 * The model-dependent result of compiling a natural-language description into a draft (POC 2): the
 * goal shape, the actions, the {@code DraftQuality} verdict, and any issues to review. Produced by
 * the endpoint-gated compile collaborator; rendered by the Author pane.
 */
public record AuthorDraft(
        String goalName,
        String goalOutputType,
        boolean goalExport,
        List<ActionInfo> actions,
        String qualityVerdict,
        boolean degenerate,
        String qualityReason,
        List<String> issues) {}

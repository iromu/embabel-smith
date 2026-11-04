package com.embabel.smith.unified.dto;

/**
 * The result of the author pane: the compiled {@link AuthorDraft}, or a clear error. {@link #error}
 * is non-null when the request could not be compiled (blank description, no endpoint configured, or
 * the compile failed) — in which case {@link #draft} is null.
 */
public record CompileResponse(String error, AuthorDraft draft) {

    public static CompileResponse ok(AuthorDraft draft) {
        return new CompileResponse(null, draft);
    }

    public static CompileResponse badRequest(String error) {
        return new CompileResponse(error, null);
    }

    public static CompileResponse noEndpoint(String error) {
        return new CompileResponse(error, null);
    }

    public static CompileResponse error(String error) {
        return new CompileResponse(error, null);
    }
}

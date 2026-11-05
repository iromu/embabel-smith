package com.embabel.smith.skillsource;

/**
 * A skill source cannot be materialized: missing folder, unreachable remote, unknown ref, or a
 * non-git directory. The message names the offending source and the underlying failure.
 */
public class SkillSourceException extends RuntimeException {

    public SkillSourceException(String message) {
        super(message);
    }

    public SkillSourceException(String message, Throwable cause) {
        super(message, cause);
    }
}

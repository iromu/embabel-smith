package com.embabel.smith.builder.publish;

import java.nio.file.Path;

/**
 * The outcome of a publish: where the pack now lives, and (for git) whether the local commit was
 * pushed to the remote.
 */
public record PublishResult(Path location, boolean pushed) {
}

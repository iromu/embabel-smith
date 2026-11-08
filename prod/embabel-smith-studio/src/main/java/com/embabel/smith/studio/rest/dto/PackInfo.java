package com.embabel.smith.studio.rest.dto;

/**
 * A pack in a source with its lifecycle state and drift status (the Lifecycle pane): the
 * {@code lifecycleState}/{@code baselinePassRate}/{@code updatedAt} are {@code null} when the pack
 * has no lifecycle sidecar; {@code drift} is true when the pack's content hash has moved from the
 * baseline recorded in the sidecar.
 */
public record PackInfo(String name, String contentHash, String lifecycleState,
                       Double baselinePassRate, boolean drift, String updatedAt) {
}

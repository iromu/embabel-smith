package com.embabel.smith.builder.publish;

/**
 * A build cannot be published because a pack of the same name already exists at the destination.
 * The build is blocked; the user may retry with {@code --overwrite} to replace it.
 */
public class CollisionException extends RuntimeException {

    public CollisionException(String message) {
        super(message);
    }
}

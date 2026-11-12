package com.embabel.smith.builder.security;

/**
 * The overall pre-publish verdict for a scanned agent spec.
 */
public enum ScanVerdict {

    /** No findings — safe to publish. */
    SAFE,

    /** Warning-level findings — publish only under scrutiny. */
    REVIEW,

    /** A critical finding — do not publish; block the build. */
    BLOCK
}

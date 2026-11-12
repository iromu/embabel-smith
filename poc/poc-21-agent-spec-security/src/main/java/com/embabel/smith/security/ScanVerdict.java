package com.embabel.smith.security;

/**
 * The overall install-time verdict for a scanned agent spec.
 */
public enum ScanVerdict {

    /** No findings — safe to load at the recommended tier. */
    SAFE,

    /** Warning-level findings — load only under scrutiny, at the recommended (higher) tier. */
    REVIEW,

    /** A critical finding — do not load; quarantine the pack. */
    BLOCK
}

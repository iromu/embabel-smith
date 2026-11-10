package com.embabel.smith.studio.rest.dto;

/**
 * Whether a model endpoint is resolvable, the default model it would use, and any configuration
 * error. Drives the studio header's live "ready / no endpoint" indicator so the user can see at a
 * glance whether model-backed actions (Run, Eval, Auto-improve) will work.
 */
public record StatusResponse(boolean configured, String model, String error) {}

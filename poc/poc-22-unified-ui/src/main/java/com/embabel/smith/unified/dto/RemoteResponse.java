package com.embabel.smith.unified.dto;

/**
 * The result of a remote invocation (POC 16): whether the backing process was reachable, the action
 * name, and its result. When the sidecar is down, {@link #running} is false and {@link #error} names
 * the not-running state (the pane does not crash).
 */
public record RemoteResponse(boolean running, String error, String action, String result, String mcpUrl) {

    public static RemoteResponse ok(String action, String result, String mcpUrl) {
        return new RemoteResponse(true, null, action, result, mcpUrl);
    }

    public static RemoteResponse notRunning(String error, String mcpUrl) {
        return new RemoteResponse(false, error, null, null, mcpUrl);
    }
}

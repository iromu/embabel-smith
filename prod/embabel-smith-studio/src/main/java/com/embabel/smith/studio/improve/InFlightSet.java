package com.embabel.smith.studio.improve;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the specs with an improvement currently in flight. Enforces "at most one active
 * improvement per spec": a spec already in flight is skipped for the current iteration.
 */
public final class InFlightSet {

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Marks the spec in flight.
     *
     * @return {@code true} when it was not already in flight (the caller may proceed)
     */
    public boolean begin(String spec) {
        return inFlight.add(spec);
    }

    public void end(String spec) {
        inFlight.remove(spec);
    }

    public boolean inFlight(String spec) {
        return inFlight.contains(spec);
    }

    public boolean isEmpty() {
        return inFlight.isEmpty();
    }
}

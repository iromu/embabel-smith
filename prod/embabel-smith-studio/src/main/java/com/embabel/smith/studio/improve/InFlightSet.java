package com.embabel.smith.studio.improve;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks the skills with an improvement currently in flight. Enforces "at most one active
 * improvement per skill": a skill already in flight is skipped for the current iteration.
 */
public final class InFlightSet {

    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Marks the skill in flight.
     *
     * @return {@code true} when it was not already in flight (the caller may proceed)
     */
    public boolean begin(String skill) {
        return inFlight.add(skill);
    }

    public void end(String skill) {
        inFlight.remove(skill);
    }

    public boolean inFlight(String skill) {
        return inFlight.contains(skill);
    }

    public boolean isEmpty() {
        return inFlight.isEmpty();
    }
}

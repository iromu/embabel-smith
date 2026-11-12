package com.embabel.smith.retrieval.remote;

import com.embabel.smith.common.samples.SampleAgentSpecs;
import com.embabel.smith.retrieval.SpecEntry;
import com.embabel.smith.retrieval.scale.ScaleSpecLibrary;

import java.util.ArrayList;
import java.util.List;

/**
 * A <em>larger</em> same-typed spec library for the scaling dimension (POC 23). POC 12/20's
 * {@link ScaleSpecLibrary} tops out at 281 specs; this generator produces a configurable, larger
 * library (default well past a thousand) of {@code UserInput -> BackgroundMessage} specs with
 * distinct topics, so the scaling curve (retrieval latency and one-time embedding cost as the
 * library grows) is measurable beyond the shared C10 scenario.
 *
 * <p>Every spec is the same type, so a type-based (GOAP) router cannot disambiguate — only the
 * descriptions do, exactly as in POC 12. The topics are generated from two cycling word lists, so
 * they stay distinct (clean retrieval) while the library size grows.
 */
public final class LargeScaleLibrary {

    private LargeScaleLibrary() {
    }

    public static final String USER_INPUT = SampleAgentSpecs.USER_INPUT;
    public static final String BACKGROUND_MESSAGE = SampleAgentSpecs.BACKGROUND_MESSAGE;

    /** One verb family: a description template and a matching query template over the same topic. */
    private record Family(String key, String template, String queryTemplate) {
    }

    private static final List<Family> FAMILIES = List.of(
            new Family("explain", "Explains how a %s works", "explain how a %s works"),
            new Family("summarize", "Summarizes a %s into plain language", "summarize a %s"),
            new Family("translate", "Translates a %s passage into English", "translate a %s passage"),
            new Family("write", "Writes a short %s about a subject", "write a short %s"),
            new Family("compute", "Computes %s and returns them", "compute the %s"),
            new Family("plan", "Plans a %s for a destination", "plan a %s"));

    /** Cycling word lists; {@code ADJ[i % ADJ.length] + " " + NOUN[(i / ADJ.length) % NOUN.length]}
     * yields distinct topics as {@code i} grows. */
    private static final String[] ADJ = {
            "portable", "industrial", "domestic", "maritime", "aerial", "submersible", "modular",
            "hybrid", "autonomous", "manual", "pneumatic", "hydraulic", "electronic", "mechanical",
            "digital", "analog", "compact", "heavy", "rugged", "precision", "low-power",
            "high-voltage", "single-phase", "three-phase", "variable", "fixed", "reversible",
            "telescopic", "rotary", "linear", "cyclic", "intermittent", "continuous", "batch",
            "inline", "offshore", "onboard", "embedded", "standalone", "wireless"
    };
    private static final String[] NOUN = {
            "engine", "motor", "pump", "valve", "sensor", "actuator", "gauge", "filter",
            "compressor", "generator", "transformer", "reactor", "turbine", "piston", "camshaft",
            "manifold", "carburetor", "igniter", "dynamo", "alternator", "condenser", "evaporator",
            "regulator", "contactor", "relay", "capacitor", "inductor", "resistor", "diode",
            "transistor", "amplifier", "oscillator", "modulator", "detector", "transducer",
            "servo", "governor", "flywheel", "crankshaft", "manifold"
    };

    /**
     * Generate exactly {@code totalSize} distinct-topic specs, round-robin across the six
     * families so the count is exact (not rounded down to a multiple of the family count). The
     * first spec of each family (topic 0) is that family's canonical benchmark target.
     */
    public static List<SpecEntry> generate(int totalSize) {
        List<SpecEntry> all = new ArrayList<>(totalSize);
        for (int p = 0; p < totalSize; p++) {
            Family family = FAMILIES.get(p % FAMILIES.size());
            all.add(entry(family, topic(p / FAMILIES.size())));
        }
        return all;
    }

    /**
     * The six benchmark queries, one per family. Each maps to that family's canonical target — the
     * first generated spec, whose description the query mirrors, so the target is the unique best
     * match in a distinct-topic library.
     */
    public static List<ScaleSpecLibrary.QueryTarget> benchmark() {
        List<ScaleSpecLibrary.QueryTarget> targets = new ArrayList<>();
        for (Family f : FAMILIES) {
            String topic = topic(0);
            targets.add(new ScaleSpecLibrary.QueryTarget(f.queryTemplate.formatted(topic), entry(f, topic).id()));
        }
        return targets;
    }

    private static String topic(int i) {
        return ADJ[i % ADJ.length] + " " + NOUN[(i / ADJ.length) % NOUN.length];
    }

    private static SpecEntry entry(Family f, String topic) {
        String id = f.key() + "-" + slug(topic);
        return new SpecEntry(id, id, f.template().formatted(topic), List.of(USER_INPUT), BACKGROUND_MESSAGE);
    }

    private static String slug(String topic) {
        return topic.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
}

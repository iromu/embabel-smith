package com.embabel.smith.retrieval.scale;

import com.embabel.smith.common.samples.SampleSkillPacks;
import com.embabel.smith.retrieval.SkillEntry;

import java.util.ArrayList;
import java.util.List;

/**
 * A same-typed skill library at the 200+ scale that doc 02 §3.4 and the "More Skills, Worse Agents?"
 * result (arXiv:2605.24050, up to −21% at 202 skills) describe.
 *
 * <p>Every skill is {@code UserInput -> BackgroundMessage}, so a type-based (GOAP) router cannot
 * disambiguate — only the descriptions do. The library has two parts:
 *
 * <ul>
 *   <li><b>core</b> — distinct-topic skills across six verb families (the "big folder"). Here the
 *       right skill is the unique best text match, so retrieval should be clean.</li>
 *   <li><b>near-duplicates</b> — same-topic variants of each benchmark target (the shadowing
 *       pressure). As these accumulate, the canonical target is no longer the uniquely-best match,
 *       which is exactly the selection failure the literature attributes to skill shadowing.</li>
 * </ul>
 */
public final class ScaleSkillLibrary {

    private ScaleSkillLibrary() {
    }

    public static final String USER_INPUT = SampleSkillPacks.USER_INPUT;
    public static final String BACKGROUND_MESSAGE = SampleSkillPacks.BACKGROUND_MESSAGE;

    /** A benchmark target: the task query and the id of the skill that should satisfy it. */
    public record QueryTarget(String query, String targetId) {
    }

    /** One verb family: a description template, its distinct topics, and the benchmark target. */
    private record Family(String template, String targetTopic, String targetDetail, String query,
                          List<String> topics) {
        String describe(String topic) {
            return template.formatted(topic);
        }
    }

    private static final List<Family> FAMILIES = List.of(
            new Family("Explains how a %s works", "coffee machine",
                    ", including the boiler and the portafilter", "explain how a coffee machine works",
                    List.of("coffee machine", "espresso machine", "toaster", "electric kettle", "microwave oven",
                            "blender", "dishwasher", "washing machine", "refrigerator", "air conditioner",
                            "space heater", "water heater", "vacuum cleaner", "lawn mower", "robot vacuum",
                            "inkjet printer", "laser scanner", "digital projector", "flat screen television",
                            "fm radio", "bluetooth speaker", "stereo amplifier", "lithium battery",
                            "solar panel", "wind turbine", "hydro generator", "car engine", "diesel engine",
                            "jet engine", "rocket engine", "steam engine", "electric motor", "gearbox",
                            "car transmission", "alternator", "starter motor", "disc brake system",
                            "suspension spring", "power steering", "bicycle pump", "bicycle", "skateboard",
                            "kayak", "surfboard", "snowboard", "canoe", "sailboat", "jet ski", "helicopter",
                            "airplane", "quadcopter drone", "high speed train", "subway", "tram",
                            "electric scooter")),
            new Family("Summarizes a %s into plain language", "legal contract",
                    ", with the key obligations and deadlines", "summarize this legal contract",
                    List.of("legal contract", "sales agreement", "lease agreement", "employment contract",
                            "non-disclosure agreement", "patent filing", "court ruling", "tax return",
                            "medical report", "research paper", "news article", "book chapter",
                            "meeting minutes", "project proposal", "investment memo", "insurance policy",
                            "credit agreement", "service level agreement", "partnership deed",
                            "merger document", "shareholder report", "audit report", "budget summary",
                            "product spec", "user manual", "technical white paper", "grant application",
                            "research abstract", "policy brief", "term sheet", "loan agreement",
                            "settlement document")),
            new Family("Translates a %s passage into English", "French",
                    ", preserving idiomatic phrasing", "translate this French passage",
                    List.of("French", "Spanish", "German", "Italian", "Portuguese", "Dutch", "Swedish",
                            "Norwegian", "Danish", "Finnish", "Polish", "Czech", "Hungarian", "Romanian",
                            "Greek", "Turkish", "Russian", "Japanese", "Korean", "Mandarin", "Cantonese",
                            "Arabic", "Hebrew", "Hindi", "Thai", "Vietnamese", "Indonesian", "Malay")),
            new Family("Writes a short %s about a subject", "haiku",
                    ", in the traditional 5-7-5 structure", "write a short haiku",
                    List.of("haiku", "limerick", "sonnet", "acrostic poem", "ballad", "epic verse",
                            "free verse", "pantoum", "couplet", "quatrain", "septet", "villanelle",
                            "sestina", "ode", "elegy", "epigram", "aphorism", "short story", "fable",
                            "parable", "allegory", "anecdote", "monologue", "dialogue", "speech", "toast",
                            "eulogy", "manifesto")),
            new Family("Computes %s and returns them", "Fibonacci numbers",
                    ", with the sum of each term", "compute the fibonacci numbers",
                    List.of("Fibonacci numbers", "prime numbers", "factorial values", "powers of two",
                            "triangular numbers", "square numbers", "cube numbers", "Lucas numbers",
                            "Catalan numbers", "Bell numbers", "binomial coefficients", "gcd pairs",
                            "lcm pairs", "modular inverses", "matrix products", "eigenvalues",
                            "vector magnitudes", "dot products", "cross products", "matrix inverses",
                            "determinants", "regression coefficients", "standard deviations", "variances",
                            "correlation coefficients", "z-scores", "p-values", "confidence intervals")),
            new Family("Plans a %s for a destination", "three-day trip",
                    ", with lodging and meals included", "plan a three day trip",
                    List.of("three-day trip", "weekend getaway", "city break", "road trip", "hiking itinerary",
                            "beach holiday", "ski weekend", "museum tour", "food tour", "wine tasting route",
                            "photography walk", "cycling tour", "camping trip", "island hop", "river cruise",
                            "safari", "backpacking route", "rail tour", "roadshow", "festival itinerary")));

    /** Same-topic variant tails: appended to a target's base description to make near-duplicates. */
    private static final List<String> NEAR_DUP_TAILS = List.of(
            "", " for a child", " for an expert", " step by step", " with examples", " in one paragraph",
            " in bullet points", " with diagrams", " in under a minute", " for a general audience",
            " with analogies", " in simple terms", " as a quick guide", " for a blog post",
            " for a presentation");

    private static final int NEAR_DUPS_PER_TARGET = NEAR_DUP_TAILS.size();

    /** The core library: every distinct-topic skill across all families (no near-duplicates). */
    public static List<SkillEntry> core() {
        List<SkillEntry> all = new ArrayList<>();
        for (Family f : FAMILIES) {
            for (String topic : f.topics()) {
                String id = slug(topic);
                String desc = topic.equals(f.targetTopic())
                        ? f.describe(topic) + f.targetDetail()
                        : f.describe(topic);
                all.add(entry(id, desc));
            }
        }
        return all;
    }

    /**
     * The shadowing pressure: {@link #NEAR_DUPS_PER_TARGET} same-topic variants of the skill with id
     * {@code targetId}. Each is the target's base description (no distinguishing detail) plus a
     * variant tail — so the first variant is a near-exact match to the benchmark query.
     */
    public static List<SkillEntry> nearDuplicatesOf(String targetId) {
        Family f = familyForTarget(targetId);
        String base = f.describe(f.targetTopic());
        List<SkillEntry> dups = new ArrayList<>();
        for (int i = 0; i < NEAR_DUPS_PER_TARGET; i++) {
            dups.add(entry(targetId + "-v" + i, base + NEAR_DUP_TAILS.get(i)));
        }
        return dups;
    }

    /**
     * The ordered near-duplicates to add after {@link #core()} for the growth curve, interleaved
     * across the six targets so each target's shadowing pressure builds up as the library grows.
     */
    public static List<SkillEntry> growthSequence() {
        List<List<SkillEntry>> perTarget =
                benchmark().stream().map(bt -> nearDuplicatesOf(bt.targetId())).toList();
        List<SkillEntry> sequence = new ArrayList<>();
        for (int i = 0; i < NEAR_DUPS_PER_TARGET; i++) {
            for (List<SkillEntry> per : perTarget) {
                sequence.add(per.get(i));
            }
        }
        return sequence;
    }

    /** The full library: core plus every near-duplicate (the 200+ same-typed "big folder"). */
    public static List<SkillEntry> full() {
        List<SkillEntry> all = new ArrayList<>(core());
        for (QueryTarget bt : benchmark()) {
            all.addAll(nearDuplicatesOf(bt.targetId()));
        }
        return all;
    }

    /** The six benchmark queries, one per family, each mapping to that family's canonical target. */
    public static List<QueryTarget> benchmark() {
        return FAMILIES.stream().map(f -> new QueryTarget(f.query(), slug(f.targetTopic()))).toList();
    }

    private static Family familyForTarget(String targetId) {
        for (Family f : FAMILIES) {
            if (slug(f.targetTopic()).equals(targetId)) {
                return f;
            }
        }
        throw new IllegalArgumentException("no family targets " + targetId);
    }

    private static SkillEntry entry(String id, String description) {
        return new SkillEntry(id, id, description, List.of(USER_INPUT), BACKGROUND_MESSAGE);
    }

    private static String slug(String topic) {
        return topic.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]+", "-");
    }
}

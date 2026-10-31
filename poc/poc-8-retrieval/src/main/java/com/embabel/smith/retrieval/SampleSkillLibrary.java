package com.embabel.smith.retrieval;

import com.embabel.smith.common.samples.SampleSkillPacks;

import java.util.List;
import java.util.stream.Stream;

/**
 * A same-typed skill library for the shadowing scenario: every skill consumes {@code UserInput} and
 * produces a {@code BackgroundMessage}, so a type-based (GOAP) router cannot tell them apart — only
 * their descriptions do. Several skills share the "explain X" verb on purpose, to show the
 * pre-filter discriminates within a same-typed, same-verb family.
 */
public final class SampleSkillLibrary {

    private SampleSkillLibrary() {
    }

    public static final String USER_INPUT = SampleSkillPacks.USER_INPUT;
    public static final String BACKGROUND_MESSAGE = SampleSkillPacks.BACKGROUND_MESSAGE;

    /** The base library (14 same-typed skills). */
    public static List<SkillEntry> base() {
        return List.of(
                entry("coffee-explainer", "Explains how a coffee machine works in plain language for a general audience"),
                entry("rocket-explainer", "Explains how a rocket engine works, covering thrust and staging"),
                entry("engine-explainer", "Explains how a car engine works, from intake to exhaust"),
                entry("contract-summarizer", "Summarizes a legal contract into plain-language key obligations"),
                entry("fr-translator", "Translates a French passage into idiomatic English"),
                entry("haiku-writer", "Writes a short haiku about a given subject"),
                entry("fibonacci-computer", "Computes the first N Fibonacci numbers and returns them"),
                entry("trip-planner", "Plans a three-day travel itinerary for a destination"),
                entry("java-debugger", "Diagnoses a Java stack trace and suggests the likely fix"),
                entry("sql-generator", "Generates a SQL query from a natural-language request"),
                entry("cover-letter-writer", "Writes a tailored cover letter for a job application"),
                entry("temp-converter", "Converts a temperature between Celsius and Fahrenheit"),
                entry("slogan-drafter", "Drafts a punchy marketing slogan for a product"),
                entry("checkbook-balancer", "Balances a checkbook from a list of transactions"));
    }

    /** The base library plus extra same-verb "explain X" distractors, to stress the pre-filter. */
    public static List<SkillEntry> withExplainDistractors() {
        List<SkillEntry> extra = List.of(
                entry("toaster-explainer", "Explains how a toaster works, including the heating elements"),
                entry("tap-explainer", "Explains how a household tap and its valve work"),
                entry("kettle-explainer", "Explains how an electric kettle works and its safety cut-off"),
                entry("lamp-explainer", "Explains how an LED lamp and its driver work"));
        return Stream.concat(base().stream(), extra.stream()).toList();
    }

    /** A realistic task query paired with the skill that should satisfy it — the accuracy benchmark. */
    public record QueryTarget(String query, String targetId) {
    }

    /** Benchmark queries: each maps to a distinct skill in {@link #base()}. */
    public static List<QueryTarget> benchmarkQueries() {
        return List.of(
                new QueryTarget("explain how a coffee machine works in plain language", "coffee-explainer"),
                new QueryTarget("summarize this legal contract for me", "contract-summarizer"),
                new QueryTarget("translate this French passage into English", "fr-translator"),
                new QueryTarget("write a short haiku about autumn", "haiku-writer"),
                new QueryTarget("compute the first ten fibonacci numbers", "fibonacci-computer"),
                new QueryTarget("plan a three day trip to Lisbon", "trip-planner"));
    }

    private static SkillEntry entry(String id, String description) {
        return new SkillEntry(id, id, description, List.of(USER_INPUT), BACKGROUND_MESSAGE);
    }
}

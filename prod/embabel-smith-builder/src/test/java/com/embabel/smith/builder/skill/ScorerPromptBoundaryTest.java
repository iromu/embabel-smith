/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import com.embabel.smith.common.io.AgentSpecReader;
import com.embabel.smith.common.model.ActionStepSpec;
import com.embabel.smith.common.model.AgentSpec;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The scorer prompts stay outside the text a pack can be mutated to contain.
 *
 * <p>
 * <b>The boundary this guards.</b> The builder's eight {@code .jinja} prompts split by
 * whom they serve, not by package. Five serve generation — {@code process_to_flow.jinja},
 * {@code process_to_flow_multi.jinja}, {@code business_process_discovery.jinja},
 * {@code generate_skill.jinja}, {@code generate_evals.jinja} — and are the surface an
 * improvement loop rewrites. Three are the evaluation instrumentation itself, all read by
 * {@link SkillPrompts}: {@code grade_eval.jinja} (the LLM-as-judge verdict rule) and the
 * two eval arms {@code agent_baseline.jinja} and {@code agent_with_skill.jinja} (what the
 * judge is asked to compare). Design decision D10 keeps those three off the mutable
 * surface <em>by placement</em>: they are classpath resources under
 * {@code src/main/resources}, while the builder's flow now also lives as packs under
 * {@code builder-agent-specs/}, and a pack's step prompts are the part a generation may
 * legitimately edit. So a mutation confined to pack files cannot reach the referee —
 * unless someone copies the referee's own words into a step. This test is the offline
 * guard that they have not been.
 *
 * <p>
 * <b>Why the assertion is on content and not on filenames.</b> A guard that greps the
 * packs for {@code grade_eval} is defeated by the realistic mutation: a step prompt that
 * reproduces the grading <em>instructions</em> under some other name. So the comparison
 * is between text — the three resources' text as {@link SkillPrompts} reads it from the
 * classpath (its own {@code gradeEval()}, {@code agentBaseline()},
 * {@code agentWithSkill()} accessors, so this measures the bytes production sends to the
 * judge, not a second copy of them), against the concatenated step prompts of all four
 * packs, read through {@link AgentSpecReader#read(Path)} — the same reader the product
 * uses and the same path-addressed convention {@code core/BuilderAgentSpecPackTest} uses,
 * since {@code builder-agent-specs/} sits at the module root and is not on the test
 * classpath.
 *
 * <p>
 * <b>The measure, and why this one.</b> Normalise both sides (drop Jinja {@code {#…#}}
 * comments, lowercase, non-alphanumerics to spaces), then take the <em>longest run of
 * consecutive words</em> of the scorer that occurs verbatim and in order in the pack
 * text, as a fraction of the scorer's own words — asymmetric, with the scorer as
 * denominator. Two properties are required of the measure and its predecessor satisfied
 * only the first. It must read a copy as a copy: a lifted block scores 1.0 however much
 * other text surrounds it, and the single concatenated haystack still catches a copy
 * split across steps or across packs. And it must not read builder text that legitimately
 * shares a subject with the packs as a copy — which the fraction of shared {@code n}-word
 * runs does not. Re-measured on this tree, that older containment puts the builder's own
 * generation prompts at 0.818 ({@code process_to_flow}) and 0.627
 * ({@code process_to_flow_multi}), both above the threshold, because the packs descend
 * from those prompts and share their output schema and field-name lists with them. Shared
 * vocabulary is not the signal; a block of consecutive words is, so the guard asks for
 * the block. A symmetric coefficient stays the wrong instrument for the reason it always
 * was: the haystack is an order of magnitude larger than even the largest scorer (2,750
 * normalised words against 218, 49 and 13), so a Jaccard-style coefficient reads a
 * verbatim copy of {@code grade_eval} at about 0.10. Measured on this tree (42c4377),
 * over the thirteen step prompts of the four packs — 17,906 characters as this test's own
 * {@link AgentSpecReader#read(Path)} reads them, 2,750 normalised words:
 * {@code grade_eval} 0.037, both arms 0.000; a planted verbatim copy 1.000.
 *
 * <p>
 * <b>Where the threshold comes from.</b> {@value #MATCH_THRESHOLD} as a named constant,
 * not a setting, and it did not move when the measure changed. It sits in the band
 * between the two ends it has to separate: the three scorer prompts measure 0.037, 0.000
 * and 0.000 against the pack text, a planted copy is 1.0. What changed is that the band
 * now has both edges — the builder's legitimately-related text also lands under it: 0.209
 * ({@code process_to_flow}), 0.104 ({@code process_to_flow_multi}), 0.076
 * ({@code generate_skill}), 0.028 ({@code generate_evals}), 0.000
 * ({@code business_process_discovery}) — asserted by
 * {@link #legitimatelyRelatedBuilderTextDoesNotReadAsACopy()} rather than argued here.
 * The longest block the packs share with {@code process_to_flow} is 96 words of its 459,
 * which is what "related, not copied" looks like on this measure; the old one read that
 * same pair as 0.818 and would have failed a build over it.
 *
 * <p>
 * <b>What this does and does not catch.</b> It catches a copy: text lifted verbatim
 * (1.000), and text under a different filename, which a content measure cannot tell apart
 * from a copy. It catches a copy with a term find-and-replaced through it for as long as
 * one block between the renames still spans 60% of the resource: one scattered rename of
 * {@code grade_eval}'s central term leaves 192 of its 218 words contiguous (0.881), two
 * leave 182 (0.835), but three or more cut the longest surviving block to 101 words
 * (0.463) and slip under — where the run-overlap measure this replaced held at 0.907 for
 * the same four renames. That is the price of the contiguity requirement, taken
 * deliberately and stated here rather than hidden: the evasion it costs is a copy edited
 * in three or more scattered places, which is already a rewrite, and the guard bought
 * independence from honest builder text to pay for it. The two arms are out of reach of a
 * rename at any count, since 13 and 49 words cannot spare a block 0.6 of themselves once
 * a word is replaced. Nor does this catch a grading step written in genuinely different
 * words: a paraphrase that replaces one word in ten of {@code grade_eval} in scattered
 * positions measures 0.041, against 0.528 under the old measure — under the threshold
 * under both, so such a paraphrase was never caught by either. Those are the honest
 * boundaries, and D10 records them as a review obligation rather than a closed gap — a
 * reviewer, not this test, is the control on a paraphrased grading step.
 * {@link #ANCHOR_WORDS} is not a sensitivity knob and cannot be traded against this: it
 * only seeds candidate alignments, every seeded alignment is then extended to its full
 * length, and the shortest run the threshold can trip is 8 words (0.6 of the 13-word
 * arm), well above the seed. Raising the seed above 8 would start hiding trips, which is
 * the only way it changes an answer.
 *
 * <p>
 * <b>The controls, which are the point of the file.</b> An assertion that only ever
 * reports "no match" is worth nothing if it is comparing empty strings, so four of the
 * five tests below assert a direction rather than an absence: the three resources load
 * and are non-empty; the scan really covered the thirteen step prompts of all four packs
 * and their prompts are non-blank; the measure detects a copy planted in that same
 * haystack; and builder text that is merely related to the packs does not read as a copy.
 * The planted-copy test is the positive control rather than scorer-against-itself,
 * because scorer-against-itself is 1.0 for any measure, including a degenerate one — the
 * pair pins both ends instead: a measure blind to a copy fails
 * {@link #theMeasureDetectsACopyOfEachScorerPromptPlantedInThePackText()}, and a measure
 * that always reports a match fails
 * {@link #noBuilderPackStepPromptCarriesTheTextOfAScorerPrompt()}. The calibration test
 * is what stops that pair from being satisfied by a measure that fires on any similarity:
 * it fails on the measure this file shipped with before, which is the failure mode the
 * guard had — a green build that was a statement about shared vocabulary, not about
 * copying.
 *
 * @author Ivan Rodriguez
 */
class ScorerPromptBoundaryTest {

	/**
	 * The packs whose step prompts form the mutable surface. Same four directories,
	 * addressed the same way, as {@code core/BuilderAgentSpecPackTest}.
	 */
	private static final List<String> PACK_NAMES = List.of("builder-single-spec", "builder-process-composition",
			"builder-skill-from-process", "builder-spec-one-shot");

	/**
	 * The evaluation instrumentation, by resource name, all read through
	 * {@link SkillPrompts}.
	 */
	private static final List<String> SCORER_PROMPTS = List.of("grade_eval", "agent_baseline", "agent_with_skill");

	/**
	 * The other half of the builder's eight prompts: the generation surface an
	 * improvement loop rewrites, which discusses the same subject matter as the packs by
	 * design.
	 */
	private static final List<String> GENERATION_PROMPTS = List.of("process_to_flow", "process_to_flow_multi",
			"business_process_discovery", "generate_skill", "generate_evals");

	/**
	 * Length of the word-run used to seed a candidate alignment in
	 * {@link #longestRunFraction(String, String)}. It is not itself a copied-text
	 * threshold: every seeded alignment is extended to its full length, so the shortest
	 * run the guard can trip on is 0.6 of the shortest scorer's words (8 of
	 * {@code agent_baseline}'s 13), comfortably above this seed.
	 */
	private static final int ANCHOR_WORDS = 5;

	/**
	 * Longest shared run at or above which pack text counts as carrying a scorer prompt.
	 * See the javadoc: it sits between the 0.037 the three scorer prompts reach against
	 * the packs and the 1.0 a planted copy reaches, with the builder's own generation
	 * prompts — the text most legitimately related to the packs — topping out at 0.209.
	 */
	private static final double MATCH_THRESHOLD = 0.6;

	/**
	 * The thirteen action steps the four packs hold today. A scan below this is not the
	 * scan this guard was written against, so it must not be allowed to pass quietly.
	 */
	private static final int MIN_STEPS_SCANNED = 13;

	/**
	 * Floor on the prompt text scanned, for the same reason, in characters rather than
	 * steps.
	 */
	private static final int MIN_CHARS_SCANNED = 10_000;

	private static final Pattern JINJA_COMMENT = Pattern.compile("\\{#.*?#\\}", Pattern.DOTALL);

	private static String scorerText(String resourceName) {
		return switch (resourceName) {
			case "grade_eval" -> SkillPrompts.gradeEval();
			case "agent_baseline" -> SkillPrompts.agentBaseline();
			case "agent_with_skill" -> SkillPrompts.agentWithSkill();
			default -> throw new IllegalStateException("Not a scorer prompt: " + resourceName);
		};
	}

	/**
	 * A generation prompt's text. No production accessor exposes these — each generator
	 * holds its resource path privately — so they are read as the classpath bytes the
	 * model receives.
	 */
	private static String generationText(String resourceName) {
		try (InputStream in = ScorerPromptBoundaryTest.class
			.getResourceAsStream("/prompts/" + resourceName + ".jinja")) {
			if (in == null) {
				throw new IllegalStateException("prompt resource not found: " + resourceName);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to load prompt " + resourceName, ex);
		}
	}

	/**
	 * Every prompt-bearing step of every pack, keyed {@code <pack>/<step>}. Goals carry
	 * no prompt ({@code GoalSpec} has no prompt field), so these thirteen strings are the
	 * whole prompt surface of the packs.
	 */
	private static Map<String, String> packStepPrompts() throws IOException {
		Map<String, String> prompts = new LinkedHashMap<>();
		for (String packName : PACK_NAMES) {
			Path dir = Path.of("builder-agent-specs", packName);
			assertThat(dir.resolve("manifest.yml")).as("pack %s is on disk at %s", packName, dir).isRegularFile();
			AgentSpec pack = new AgentSpecReader().read(dir);
			for (ActionStepSpec step : pack.steps()) {
				prompts.put(packName + "/" + step.name(), step.prompt() == null ? "" : step.prompt());
			}
		}
		return prompts;
	}

	private static String haystackOf(Map<String, String> steps) {
		return String.join("\n", steps.values());
	}

	private static String describeScan(Map<String, String> steps) {
		return steps.size() + " step prompts, " + haystackOf(steps).length() + " characters";
	}

	/**
	 * The word sequence of a text as the measure sees it: Jinja {@code {#…#}} comments
	 * dropped, lowercased, every run of non-alphanumerics collapsed to a single space.
	 */
	private static List<String> words(String text) {
		String normalized = JINJA_COMMENT.matcher(text)
			.replaceAll(" ")
			.toLowerCase(Locale.ROOT)
			.replaceAll("[^a-z0-9]+", " ")
			.trim();
		return List.of(normalized.isEmpty() ? new String[0] : normalized.split("\\s+"));
	}

	/**
	 * The {@value #ANCHOR_WORDS}-word run starting at {@code at}, used as a lookup key
	 * when looking for candidate alignments.
	 */
	private static String anchor(List<String> words, int at) {
		return String.join(" ", words.subList(at, at + ANCHOR_WORDS));
	}

	/**
	 * The longest run of consecutive words of {@code source} that occurs verbatim and in
	 * order somewhere in {@code haystack}, as a fraction of the source's words. The
	 * contiguity is the point: text lifted into the haystack reads 1.0 however much other
	 * text surrounds it, while text that only shares vocabulary — or that shares the same
	 * sentences in short, interrupted fragments — cannot reach the threshold without
	 * carrying a block that large.
	 * <p>
	 * A source too short to yield a single anchor is an error rather than a zero, because
	 * a zero from an empty source is indistinguishable from a zero from a genuinely clean
	 * haystack — which is the vacuity this file exists to rule out.
	 */
	private static double longestRunFraction(String source, String haystack) {
		List<String> sourceWords = words(source);
		List<String> hayWords = words(haystack);
		assertThat(sourceWords)
			.as("source text of %d characters yields no %d-word run to compare", source.length(), ANCHOR_WORDS)
			.hasSizeGreaterThanOrEqualTo(ANCHOR_WORDS);

		Map<String, List<Integer>> anchors = new HashMap<>();
		for (int at = 0; at + ANCHOR_WORDS <= hayWords.size(); at++) {
			anchors.computeIfAbsent(anchor(hayWords, at), key -> new ArrayList<>()).add(at);
		}

		int longest = 0;
		for (int at = 0; at + ANCHOR_WORDS <= sourceWords.size() && longest < sourceWords.size(); at++) {
			for (int here : anchors.getOrDefault(anchor(sourceWords, at), List.of())) {
				int run = 0;
				while (at + run < sourceWords.size() && here + run < hayWords.size()
						&& sourceWords.get(at + run).equals(hayWords.get(here + run))) {
					run++;
				}
				longest = Math.max(longest, run);
			}
		}
		return (double) longest / sourceWords.size();
	}

	/**
	 * The guard: no scorer prompt's text is present in the builder packs' step prompts.
	 * The message names the number of steps and characters actually scanned, so a pass
	 * here is a statement about a scan that ran, not one that found nothing to read.
	 */
	@Test
	void noBuilderPackStepPromptCarriesTheTextOfAScorerPrompt() throws IOException {
		Map<String, String> steps = packStepPrompts();
		String haystack = haystackOf(steps);

		for (String scorer : SCORER_PROMPTS) {
			assertThat(longestRunFraction(scorerText(scorer), haystack))
				.as("text of %s found in the %s of builder-agent-specs/", scorer, describeScan(steps))
				.isLessThan(MATCH_THRESHOLD);
		}
	}

	/**
	 * The conforming direction, first half: the three scorer resources load from the
	 * classpath and carry instruction text. If any of them were missing, empty, or
	 * comment-only, the guard above would be comparing against nothing and reporting
	 * success.
	 */
	@Test
	void theThreeScorerPromptsLoadFromTheClasspathCarryingText() {
		for (String scorer : SCORER_PROMPTS) {
			String text = scorerText(scorer);
			assertThat(text).as("%s loads from the classpath", scorer).isNotBlank();
			assertThat(words(text)).as("%s has comparable instruction text, not only comments", scorer)
				.hasSizeGreaterThanOrEqualTo(ANCHOR_WORDS);
		}
	}

	/**
	 * The conforming direction, second half: the text the guard scans is the whole prompt
	 * surface of all four packs and it is real. Every pack contributes steps, every step
	 * prompt is non-blank, and the totals clear the floor the packs hold today — so a
	 * renamed directory, an unread {@code steps/} or a step whose prompt went missing
	 * fails here rather than making the guard quietly green.
	 */
	@Test
	void theScanCoversEveryPromptOfEveryBuilderPack() throws IOException {
		Map<String, String> steps = packStepPrompts();

		for (String packName : PACK_NAMES) {
			assertThat(steps.keySet().stream().filter(key -> key.startsWith(packName + "/")).count())
				.as("%s contributes steps to the scan", packName)
				.isPositive();
		}
		assertThat(steps.values()).as("every scanned step prompt is non-blank")
			.allSatisfy(prompt -> assertThat(prompt).isNotBlank());
		assertThat(steps.size()).as("steps scanned, expected at least %d", MIN_STEPS_SCANNED)
			.isGreaterThanOrEqualTo(MIN_STEPS_SCANNED);
		assertThat(haystackOf(steps).length()).as("characters scanned, expected at least %d", MIN_CHARS_SCANNED)
			.isGreaterThanOrEqualTo(MIN_CHARS_SCANNED);
	}

	/**
	 * The positive control, through the same code path as the guard: text planted in the
	 * pack haystack registers as a match by the measure the guard uses. A verbatim plant
	 * is what every working measure catches, so the informative half of this test is the
	 * renamed plant: the guard must still fire on the copy with a term replaced through
	 * it, which is the evasion a filename-based guard would miss. Without either, a
	 * measure that reports zero for everything — an empty trace set, a broken normaliser
	 * — would satisfy the guard forever.
	 */
	@Test
	void theMeasureDetectsACopyOfEachScorerPromptPlantedInThePackText() throws IOException {
		String haystack = haystackOf(packStepPrompts());
		int middle = haystack.length() / 2;

		for (String scorer : SCORER_PROMPTS) {
			String text = scorerText(scorer);
			String planted = haystack.substring(0, middle) + "\n" + text + "\n" + haystack.substring(middle);

			assertThat(longestRunFraction(text, planted))
				.as("a copy of %s buried in the pack text should read as a match at %s", scorer, MATCH_THRESHOLD)
				.isGreaterThanOrEqualTo(MATCH_THRESHOLD);
		}

		// The renamed plant, asserted for one scattered rename of grade_eval's central
		// term. A
		// single replacement still leaves a 192-word block intact (0.881) and two leave
		// 182
		// words (0.835), but three or more cut the longest surviving block to 101 words
		// (0.463), under the threshold. The two arms cannot spare a block 0.6 of
		// themselves
		// after any rename (13 and 49 words), so both boundaries are recorded in the
		// class
		// javadoc rather than asserted as a control that would fail.
		String renamedOnce = scorerText("grade_eval").replaceFirst("(?i)\\bexpectation\\b", "criterion");
		assertThat(longestRunFraction(scorerText("grade_eval"), haystack + "\n" + renamedOnce))
			.as("a copy of grade_eval with one scattered term renamed should still read as a match at %s",
					MATCH_THRESHOLD)
			.isGreaterThanOrEqualTo(MATCH_THRESHOLD);
	}

	/**
	 * The calibration control: builder text that is legitimately related to the packs —
	 * the five generation prompts, which share the packs' subject matter and much of
	 * their schema vocabulary — must not read as a copy. Without it the threshold is
	 * calibrated only against a planted copy, and the next scorer added to
	 * {@link #SCORER_PROMPTS} would fail a build for honest topical overlap.
	 */
	@Test
	void legitimatelyRelatedBuilderTextDoesNotReadAsACopy() throws IOException {
		String haystack = haystackOf(packStepPrompts());

		for (String prompt : GENERATION_PROMPTS) {
			assertThat(longestRunFraction(generationText(prompt), haystack))
				.as("%s is related builder text, not a copy of it", prompt)
				.isLessThan(MATCH_THRESHOLD);
		}
	}

}

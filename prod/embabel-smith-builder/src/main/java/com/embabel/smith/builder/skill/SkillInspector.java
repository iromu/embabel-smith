/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.iromu.embabel.smith.skillevaluator.Tier1Runner;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Runs the Tier 1 skill checks over a generated {@link Skill}, so the skill writing
 * guidelines are part of the built process rather than advice. This is the builder's only
 * seam into {@code org.iromu.embabel.smith.skillevaluator}: everything else in the module
 * talks to this class.
 *
 * <p>
 * The evaluator reads files, never in-memory texts, so the skill is materialised to
 * {@code <system temp>/<skillName>/SKILL.md} for the run. The folder name is the skill
 * name because the schema check reports a HIGH {@code name_consistency} finding whenever
 * frontmatter and folder disagree, which would refuse every build. The folder is removed
 * afterwards, including when the inspection fails.
 *
 * <p>
 * The default lineup is every check the evaluator can run with no external tool. The
 * three that shell out ({@code security}, {@code secrets}, {@code code-integrity}) are
 * not requested: a missing binary marks its result failed, which would let the tool
 * inventory of the build machine decide whether a build succeeds. They are reported as
 * checks that did not run, so a reader of the build report is not left to assume they
 * passed.
 *
 * @author Ivan Rodriguez
 */
public final class SkillInspector {

	/** Checks the evaluator can run in-JVM, requested on every inspection. */
	private static final List<String> BINARY_FREE_CHECKS = List.of("schema", "version", "quality", "unicode", "lint",
			"license", "pii");

	/**
	 * Checks that need a binary, disclosed as not run unless the caller asks for a full
	 * inspection. {@code secrets} is disclosed by name because it is a scan of its own,
	 * but it is not a requestable check: the evaluator runs it inside
	 * {@code code-integrity}.
	 */
	private static final List<String> TOOL_BACKED_CHECKS = List.of("security", "secrets", "code-integrity");

	/** The one check whose findings never block a build, whatever their severity. */
	private static final String ADVISORY_CHECK = "pii";

	/** The check whose 0-100 score the build report quotes. */
	private static final String QUALITY_CHECK = "quality";

	/** Metadata key under which the quality validator records its per-skill scores. */
	private static final String QUALITY_SCORES_KEY = "quality_scores";

	/** Key within those scores holding the composite score. */
	private static final String OVERALL_SCORE_KEY = "overall_score";

	/**
	 * The evaluator's report names, mapped back to the check that produced them: a result
	 * carries its validator's display name, not the check name, and the quality and lint
	 * results are stamped with short names of their own. A name missing from this map is
	 * treated as blocking, so a renamed validator cannot silently downgrade its own
	 * findings.
	 */
	private static final Map<String, String> CHECK_BY_REPORT_NAME = Map.ofEntries(
			Map.entry("Schema & Repository Governance", "schema"), Map.entry("Semantic Version Validation", "version"),
			Map.entry("Quality Score (4-Dimension Analysis)", "quality"), Map.entry("QUALITY", "quality"),
			Map.entry("Unicode Smuggling Detection", "unicode"), Map.entry("Script Lint (Advisory)", "lint"),
			Map.entry("SCRIPT_LINT", "lint"), Map.entry("License Compliance", "license"), Map.entry("PII Scan", "pii"),
			Map.entry("Security Scan", "security"), Map.entry("Secrets Detection", "secrets"),
			Map.entry("Code Risk Analysis", "code-integrity"), Map.entry("Code Integrity & Hygiene", "code-integrity"));

	/**
	 * Inspects a generated skill: materialise, run the default lineup, clean up.
	 * @param skill the skill to inspect
	 * @param fullInspection {@code true} to also request the three binary-backed checks,
	 * which an operator sets only on a machine where those tools are installed
	 * @return the findings split by what they do to the build
	 */
	public Inspection inspect(Skill skill, boolean fullInspection) {
		List<String> checks = requestedChecks(fullInspection);
		Path folder = Path.of(System.getProperty("java.io.tmpdir")).resolve(skill.name());
		try {
			Files.createDirectories(folder);
			Files.writeString(folder.resolve("SKILL.md"), skill.toSkillMd(), StandardCharsets.UTF_8);
			List<ValidationResult> results = new Tier1Runner().run(folder,
					Tier1Runner.builder().checks(checks.toArray(String[]::new)).build());
			return classify(results, fullInspection ? List.of() : TOOL_BACKED_CHECKS);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Failed to materialise skill '" + skill.name() + "' for inspection", ex);
		}
		finally {
			deleteQuietly(folder);
		}
	}

	/**
	 * The checks to ask the evaluator for.
	 * @param fullInspection {@code true} to append the binary-backed checks
	 * @return the requested check names, in the evaluator's pipeline order
	 */
	static List<String> requestedChecks(boolean fullInspection) {
		return fullInspection ? Tier1Runner.DEFAULT_CHECKS : BINARY_FREE_CHECKS;
	}

	/**
	 * Translates one evaluator run into the three values the gate acts on.
	 * Package-private so the classification rules can be tested over canned results,
	 * which a real run cannot produce on demand.
	 * @param results the results of one {@link Tier1Runner} run
	 * @param checksNotRun the checks the lineup did not request
	 * @return the findings split by what they do to the build
	 */
	static Inspection classify(List<ValidationResult> results, List<String> checksNotRun) {
		List<String> blocking = new ArrayList<>();
		List<String> advisory = new ArrayList<>();
		List<String> incomplete = new ArrayList<>();
		Double qualityScore = null;
		for (ValidationResult result : results) {
			String check = CHECK_BY_REPORT_NAME.getOrDefault(result.getValidatorName(), result.getValidatorName());
			if (check.equals(QUALITY_CHECK)) {
				qualityScore = qualityScoreOf(result);
			}
			if ("incomplete".equals(result.getStatus())) {
				// Neither a pass nor a block: only the scanner name is reported. Its
				// findings and its "not installed" warning, which names a third-party
				// download, stay out of the build report.
				incomplete.addAll(result.getIncompleteScans());
			}
			else if (!result.isPassed() && !check.equals(ADVISORY_CHECK)) {
				int first = blocking.size();
				result.getFindings().forEach((finding) -> blocking.add(describe(finding)));
				blocking.addAll(result.getGateFailures());
				if (blocking.size() == first) {
					// A result can fail through addError alone, with neither a finding
					// nor
					// a gate failure behind it — an unknown check name is one — and
					// quoting the error is then the only explanation the report gets.
					// Without this the gate would pass a lineup that never ran.
					blocking.addAll(result.getErrors());
				}
			}
			else {
				// The warnings are this result's MEDIUM and LOW findings; the advisory
				// check's error-severity findings would have blocked had the check owned
				// the verdict, so they are named here rather than dropped.
				result.getFindings()
					.stream()
					.filter((finding) -> check.equals(ADVISORY_CHECK) && finding.getSeverity().isError(false, false))
					.forEach((finding) -> advisory.add(check + ": " + describe(finding)));
				result.getWarnings().forEach((warning) -> advisory.add(check + ": " + warning));
			}
		}
		return new Inspection(List.copyOf(blocking), List.copyOf(advisory), List.copyOf(incomplete), checksNotRun,
				qualityScore);
	}

	/**
	 * The quality validator's composite score, which it records in its result metadata
	 * rather than as a finding, so a passing run would otherwise report no number at all.
	 * @param result the {@code quality} result
	 * @return the 0-100 score, or {@code null} when the result carries none
	 */
	private static Double qualityScoreOf(ValidationResult result) {
		if (result.getMetadata().get(QUALITY_SCORES_KEY) instanceof Map<?, ?> scores
				&& scores.get(OVERALL_SCORE_KEY) instanceof Number score) {
			return score.doubleValue();
		}
		return null;
	}

	/**
	 * The finding as the evaluator's own reporters print it, with its suggestion attached
	 * when it has one — the text a redraft is asked to fix.
	 * @param finding the finding to render
	 * @return the legacy finding line, suffixed with the suggestion when present
	 */
	private static String describe(Finding finding) {
		String suggestion = finding.getSuggestion();
		return (suggestion == null || suggestion.isBlank()) ? finding.toLegacyString()
				: finding.toLegacyString() + " -- " + suggestion;
	}

	/**
	 * Removes the materialised skill. Only the one file this class wrote is deleted, and
	 * the folder itself only while it is empty: a recursive delete of a folder named
	 * after the skill could otherwise take a directory that already lived in the temp
	 * root.
	 * @param folder the materialised skill folder
	 */
	private static void deleteQuietly(Path folder) {
		try {
			Files.deleteIfExists(folder.resolve("SKILL.md"));
			Files.deleteIfExists(folder);
		}
		catch (IOException ignored) {
			// best-effort cleanup; a leftover temp folder must not fail a build whose
			// verdict is already known
		}
	}

	/**
	 * What one inspection found, split by what it does to the build.
	 *
	 * @param blockingFindings findings that refuse the skill, each the evaluator's own
	 * finding text, with the suggestion when it has one
	 * @param advisoryFindings findings that inform the build report and never refuse it,
	 * each labelled with the check that produced it
	 * @param incompleteScans scanners that ran without producing complete evidence;
	 * neither a pass nor a block
	 * @param checksNotRun checks the lineup did not request, so nothing was scanned for
	 * them
	 * @param qualityScore the quality check's composite 0-100 score, or {@code null} when
	 * it reported none
	 * @author Ivan Rodriguez
	 */
	public record Inspection(List<String> blockingFindings, List<String> advisoryFindings, List<String> incompleteScans,
			List<String> checksNotRun, Double qualityScore) {

		/**
		 * Whether the skill may proceed past the inspection gate.
		 * @return {@code true} when no blocking finding was reported
		 */
		public boolean passed() {
			return this.blockingFindings.isEmpty();
		}

	}

}

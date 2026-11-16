/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.ScanPaths;

/**
 * Abstract base for Tier 1 validators: manifest discovery plus the folder-or-single-skill
 * template method, ported from Python {@code validators/base.py}.
 */
public abstract class ValidatorBase {

	/**
	 * Port of the Python {@code _CONTINUE_ON_FAILURE} contextvar: while a
	 * {@link #continueOnFailureScope} is open, folder validation records every skill
	 * instead of stopping at the first CRITICAL finding.
	 */
	private static final ThreadLocal<Boolean> CONTINUE_ON_FAILURE = ThreadLocal.withInitial(() -> Boolean.FALSE);

	/** Opens a continue-on-failure scope for the current thread; close it when done. */
	public static AutoCloseable continueOnFailureScope(boolean enabled) {
		Boolean previous = CONTINUE_ON_FAILURE.get();
		CONTINUE_ON_FAILURE.set(enabled);
		return () -> CONTINUE_ON_FAILURE.set(previous);
	}

	public static boolean isContinueOnFailure() {
		return Boolean.TRUE.equals(CONTINUE_ON_FAILURE.get());
	}

	public abstract String getName();

	public abstract String getDescription();

	/** Validate a skill directory or a folder containing skills. */
	public abstract ValidationResult validate(Path skillPath);

	/**
	 * CRITICAL checks that judge one skill's own content: they fail that skill but never
	 * stop a folder scan early.
	 */
	protected Set<String> failFastExemptChecks() {
		return Set.of();
	}

	protected Path findSkillManifest(Path skillPath) {
		return ScanPaths.findSkillManifest(skillPath);
	}

	protected boolean isSkillDirectory(Path path) {
		return ScanPaths.isSkillDirectory(path);
	}

	protected List<Path> findAllSkills(Path rootPath) {
		return ScanPaths.findAllSkills(rootPath);
	}

	/**
	 * Template method: run {@code singleSkillValidator} on a single skill, or on every
	 * skill discovered under a folder, aggregating results.
	 * @param continueOnFailure keep scanning past a CRITICAL finding instead of returning
	 * early
	 */
	protected ValidationResult validateFolderOrSkill(Path skillPath,
			Function<Path, ValidationResult> singleSkillValidator, String actionDescription, boolean noSkillsFallback,
			boolean continueOnFailure) {
		if (isSkillDirectory(skillPath)) {
			return singleSkillValidator.apply(skillPath);
		}

		ValidationResult result = new ValidationResult();
		List<Path> skillDirs = findAllSkills(skillPath);

		if (skillDirs.isEmpty()) {
			if (noSkillsFallback) {
				result.addWarning("No skills found in " + skillPath + ". " + actionDescription + " folder directly.");
				return singleSkillValidator.apply(skillPath);
			}
			result.addError("No skills found in " + skillPath + ". Expected SKILL.md files in skill directories.");
			return result;
		}

		result.addSuccess("skill_discovery", actionDescription + " " + skillDirs.size() + " skill(s)",
				Map.of("skill_count", skillDirs.size()));

		for (Path skillDir : skillDirs) {
			ValidationResult skillResult = singleSkillValidator.apply(skillDir);
			String skillName = skillDir.getFileName().toString();

			if (skillResult.isPassed()) {
				List<Map<String, Object>> checkDetails = skillResult.getSuccessDetails()
					.stream()
					.map(detail -> Map.<String, Object>of("name", detail.getCheckName(), "description",
							detail.getMessage(), "metadata", detail.getMetadata()))
					.toList();
				List<String> notes = skillResult.getSuccessDetails()
					.stream()
					.filter(d -> "skillspector_baseline".equals(d.getCheckName())
							|| d.getMetadata().containsKey("suppressed_count"))
					.map(d -> d.getMessage())
					.toList();
				result.addSuccess(skillName,
						"All checks passed" + (notes.isEmpty() ? "" : "; " + String.join("; ", notes)),
						Map.of("checks", checkDetails, "total_checks", checkDetails.size()));
			}
			else {
				result.mergeWithPrefix(skillResult, skillName);
			}

			boolean stopEarly = skillResult.getFindings()
				.stream()
				.anyMatch(f -> f.getSeverity() == Severity.CRITICAL
						&& !failFastExemptChecks().contains(f.getCheckName()));
			if (stopEarly && !(continueOnFailure || isContinueOnFailure())) {
				return result;
			}
		}
		return result;
	}

	protected ValidationResult validateFolderOrSkill(Path skillPath,
			Function<Path, ValidationResult> singleSkillValidator, String actionDescription) {
		return validateFolderOrSkill(skillPath, singleSkillValidator, actionDescription, true, false);
	}

}

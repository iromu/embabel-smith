/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.SuccessDetail;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Ports of the highest-value cases from {@code tests/validators/test_license.py}: the
 * three detection tiers, the SPDX expression grammar (allowed, blocked variants,
 * malformed values), the fail-closed conflict rules between frontmatter and license
 * files, and the folder scan.
 */
class LicenseValidatorTest {

	@TempDir
	Path tmp;

	// ------------------------------------------------------------------
	// License texts (mirroring the Python module constants)
	// ------------------------------------------------------------------

	private static final String GPL_V3_LICENSE_TEXT = """

			            GNU GENERAL PUBLIC LICENSE
			               Version 3, 29 June 2007

			 Copyright (C) 2007 Free Software Foundation, Inc. <https://fsf.org/>
			 Everyone is permitted to copy and distribute verbatim copies
			 of this license document, but changing it is not allowed.
			""";

	private static final String APACHE_2_LICENSE_TEXT = """

			                     Apache License
			               Version 2.0, January 2004
			            http://www.apache.org/licenses/

			 TERMS AND CONDITIONS FOR USE, REPRODUCTION, AND DISTRIBUTION

			 1. Definitions.

			    "License" shall mean the terms and conditions for use, reproduction,
			    and distribution as defined by Sections 1 through 9 of this document.
			""";

	private static final String MIT_LICENSE_TEXT = """
			MIT License

			Permission is hereby granted, free of charge, to any person obtaining a copy
			of this software and associated documentation files (the "Software"), to deal
			in the Software without restriction, including without limitation the rights
			to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
			copies of the Software, and to permit persons to whom the Software is
			furnished to do so, subject to the following conditions:

			The above copyright notice and this permission notice shall be included in all
			copies or substantial portions of the Software.
			""";

	// ------------------------------------------------------------------
	// Fixtures
	// ------------------------------------------------------------------

	/**
	 * A skill whose SKILL.md carries the given frontmatter block plus optional extra
	 * files.
	 */
	private Path skill(String name, String frontmatter, Map<String, String> files) throws IOException {
		Path dir = tmp.resolve(name);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"), "---\n" + frontmatter + "---\n\n# " + name + "\n");
		for (Map.Entry<String, String> file : files.entrySet()) {
			Files.writeString(dir.resolve(file.getKey()), file.getValue());
		}
		return dir;
	}

	private Path skillWithLicenseField(String name, String declaredLicense) throws IOException {
		return skill(name, "name: " + name + "\ndescription: Skill with a declared license expression\n" + "license: \""
				+ declaredLicense + "\"\n", Map.of());
	}

	private Path skillWithLicenseFieldAndFile(String name, String declaredLicense, String licenseText)
			throws IOException {
		return skill(name, "name: " + name + "\ndescription: Skill with both declarations\n" + "license: \""
				+ declaredLicense + "\"\n", Map.of("LICENSE", licenseText));
	}

	private static List<Finding> checks(ValidationResult result, String checkName) {
		return result.getFindings().stream().filter(finding -> finding.getCheckName().equals(checkName)).toList();
	}

	private static String joinedErrors(ValidationResult result) {
		return String.join(" ", result.getErrors()).toLowerCase();
	}

	private static boolean mentionsAny(List<String> messages, String needle) {
		return messages.stream().anyMatch(message -> message.contains(needle));
	}

	private static SuccessDetail detail(ValidationResult result, String checkName) {
		return result.getSuccessDetails()
			.stream()
			.filter(entry -> entry.getCheckName().equals(checkName))
			.findFirst()
			.orElseThrow();
	}

	private static String meta(ValidationResult result, String key) {
		Object value = result.getMetadata().get(key);
		return value == null ? null : String.valueOf(value);
	}

	// ------------------------------------------------------------------
	// Basic properties
	// ------------------------------------------------------------------

	@Test
	void validatorProperties() {
		LicenseValidator validator = new LicenseValidator();
		assertEquals("License Compliance", validator.getName());
		assertTrue(validator.getDescription().toLowerCase().contains("license"));
	}

	@Test
	void configLoading() {
		Map<String, Object> config = new LicenseValidator().getConfig();
		assertTrue(config.containsKey("allowed_licenses"));
		assertTrue(config.containsKey("blocked_licenses"));
		assertTrue(config.containsKey("license_patterns"));
		assertTrue(String.valueOf(config.get("allowed_licenses")).contains("Apache-2.0"));
		assertTrue(String.valueOf(config.get("blocked_licenses")).contains("GPL-3.0"));
	}

	@Test
	void strictModeInitialization() {
		assertFalse(new LicenseValidator().isStrictMode());
		assertTrue(new LicenseValidator(true).isStrictMode());
	}

	// ------------------------------------------------------------------
	// Tier 1: frontmatter
	// ------------------------------------------------------------------

	@Test
	void detectsDirectLicenseInFrontmatter() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("mit-skill", "MIT", MIT_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertEquals("MIT", meta(result, "license"));
		assertEquals("allowed", meta(result, "license_status"));
		assertTrue(checks(result, "frontmatter_license_missing").isEmpty());
		assertNotNull(detail(result, "license_compliance"));
	}

	@Test
	void detectsFileReferenceInFrontmatter() throws IOException {
		Path skillDir = skill("apache-skill",
				"name: apache-skill\ndescription: A skill with Apache 2.0 license\nlicense: See LICENSE.txt\n",
				Map.of("LICENSE.txt", APACHE_2_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("Apache-2.0"));
		assertTrue(mentionsAny(result.getMessages(), "LICENSE"));
		assertTrue(checks(result, "frontmatter_license_mismatch").isEmpty());
	}

	@Test
	void directFrontmatterLicenseMatchesLicenseFile() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skillWithLicenseFieldAndFile("mit-match", "MIT", MIT_LICENSE_TEXT));

		assertTrue(result.isPassed());
		assertEquals("allowed", meta(result, "license_status"));
		assertTrue(checks(result, "frontmatter_license_mismatch").isEmpty());
	}

	@Test
	void mitFrontmatterDoesNotHideGplLicenseFile() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("mit-claims-gpl-body", "MIT", GPL_V3_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		String errorText = joinedErrors(result);
		assertTrue(errorText.contains("gpl") || errorText.contains("copyleft") || errorText.contains("blocked")
				|| errorText.contains("conflict") || errorText.contains("mismatch"));
		assertFalse(mentionsAny(result.getMessages(), "License: MIT (ALLOWED"));
		assertFalse("allowed".equals(meta(result, "license_status")));
	}

	@Test
	void mitFrontmatterWithoutLicenseFileIsAllowed() throws IOException {
		Path skillDir = skill("mit-frontmatter-only",
				"name: mit-frontmatter-only\ndescription: MIT in frontmatter with no LICENSE file\nlicense: MIT\n",
				Map.of());

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertEquals("MIT", meta(result, "license"));
		assertEquals("allowed", meta(result, "license_status"));
	}

	@Test
	void gplLicenseFileWithoutFrontmatterIsBlocked() throws IOException {
		Path skillDir = skill("gpl-skill", "name: gpl-skill\ndescription: A skill with GPL license\n",
				Map.of("LICENSE", GPL_V3_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertFalse(checks(result, "blocked_license").isEmpty());
		String errorText = joinedErrors(result);
		assertTrue(errorText.contains("gpl") || errorText.contains("copyleft"));
	}

	@Test
	void gplFrontmatterIsNotHiddenByMitLicenseFile() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("gpl-claims-mit-body", "GPL-3.0", MIT_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertFalse(checks(result, "blocked_license").isEmpty());
		assertFalse("allowed".equals(meta(result, "license_status")));
	}

	@Test
	void conflictingAllowedLicensesFailClosed() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("mit-vs-apache-body", "MIT", APACHE_2_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		String errorText = joinedErrors(result);
		assertTrue(errorText.contains("conflict") || errorText.contains("mismatch") || errorText.contains("differ"));
		assertFalse(checks(result, "frontmatter_license_mismatch").isEmpty());
		assertEquals("conflict", meta(result, "license_status"));
		assertFalse(mentionsAny(result.getMessages(), "ALLOWED - permissive"));
	}

	@Test
	void copyingGplIsNotHiddenByMitLicenseFile() throws IOException {
		Path skillDir = skill("mit-license-gpl-copying",
				"name: mit-license-gpl-copying\ndescription: Skill with MIT LICENSE and GPL COPYING\nlicense: MIT\n",
				Map.of("LICENSE", MIT_LICENSE_TEXT, "COPYING", GPL_V3_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		assertFalse(checks(result, "blocked_license").isEmpty());
		assertEquals("blocked", meta(result, "license_status"));
		assertFalse(mentionsAny(result.getMessages(), "License: MIT (ALLOWED"));
	}

	@Test
	void singleFileMitAndGplSignaturesFailClosed() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("dual-signature-license", "MIT",
				MIT_LICENSE_TEXT + "\n" + GPL_V3_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		assertFalse(checks(result, "blocked_license").isEmpty());
		assertEquals("blocked", meta(result, "license_status"));
	}

	@Test
	void unrecognizedLgpl21LicenseFileFailsClosed() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("mit-frontmatter-lgpl21-file", "MIT", """
				GNU LESSER GENERAL PUBLIC LICENSE
				Version 2.1, February 1999

				Copyright (C) 1991, 1999 Free Software Foundation, Inc.
				""");

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		List<Finding> findings = checks(result, "unrecognized_license_file");
		assertFalse(findings.isEmpty());
		assertEquals("License file 'LICENSE' is present but could not be identified. Frontmatter declares 'MIT'."
				+ " Manual review is required.", findings.get(0).getMessage());
		assertEquals("unrecognized", meta(result, "license_status"));
	}

	@Test
	void noticeApacheAttributionDoesNotConflictWithMit() throws IOException {
		Path skillDir = skill("mit-with-apache-notice",
				"name: mit-with-apache-notice\ndescription: MIT with an Apache NOTICE\nlicense: MIT\n",
				Map.of("LICENSE", MIT_LICENSE_TEXT, "NOTICE", APACHE_2_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(checks(result, "frontmatter_license_mismatch").isEmpty());
		assertEquals("allowed", meta(result, "license_status"));
	}

	@Test
	void strictDualLicenseExpressionWithUnknownComponentFails() throws IOException {
		Path skillDir = skill("dual-license-unknown",
				"name: dual-license-unknown\ndescription: Dual license with an unknown component\n"
						+ "license: \"MIT OR FOO-1.0\"\n",
				Map.of("LICENSE", MIT_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("unknown", meta(result, "license_status"));
		assertFalse(checks(result, "unknown_license").isEmpty());
		assertTrue(checks(result, "frontmatter_license_mismatch").isEmpty());
	}

	@Test
	void workflowFrontmatterDetection() throws IOException {
		Path workflowDir = tmp.resolve("licensed-workflow");
		Files.createDirectories(workflowDir);
		Files.writeString(workflowDir.resolve("workflow-rules.mdc"), """
				---
				alwaysApply: false
				title: Licensed Workflow
				description: A workflow with explicit license
				license: BSD-3-Clause
				metadata:
				  author: Test <test@example.com>
				---

				# Licensed Workflow
				""");

		ValidationResult result = new LicenseValidator().validate(workflowDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("BSD"));
	}

	// ------------------------------------------------------------------
	// SPDX expression policy
	// ------------------------------------------------------------------

	private static Stream<Arguments> validExpressions() {
		return Stream.of(Arguments.of("CC-BY-4.0 AND Apache-2.0"), Arguments.of("CC-BY-4.0 OR Apache-2.0"),
				Arguments.of("(MIT OR Apache-2.0)"), Arguments.of("(MIT OR Apache-2.0) AND BSD-3-Clause"),
				Arguments.of("MIT OR CC-BY-4.0 AND Apache-2.0"), Arguments.of("(MIT)"),
				Arguments.of("Apache-2.0 WITH LLVM-exception"));
	}

	@ParameterizedTest
	@MethodSource("validExpressions")
	void validSpdxExpressionOfAllowedLicensesPasses(String declaredLicense) throws IOException {
		Path skillDir = skillWithLicenseField("spdx-allowed", declaredLicense);

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertTrue(result.isPassed(), declaredLicense + " should pass");
		assertEquals(declaredLicense, meta(result, "license"));
		assertEquals("allowed", meta(result, "license_status"));
	}

	private static Stream<Arguments> blockedVersionForms() {
		return Stream.of(Arguments.of("GPL-2.0"), Arguments.of("LGPL-2.1"), Arguments.of("GPL-3.0+"),
				Arguments.of("GPL-2.0+"), Arguments.of("AGPL-3.0+"), Arguments.of("LGPL-2.1+"),
				Arguments.of("LGPL-2.1-only"), Arguments.of("LGPL-3.0-or-later"), Arguments.of("MPL-2.0+"),
				Arguments.of("MIT OR GPL-3.0+"), Arguments.of("GPL-2.0-only WITH Classpath-exception-2.0"),
				Arguments.of("GPL-2.0-with-classpath-exception"), Arguments.of("GPL-2.0-with-GCC-exception"),
				Arguments.of("GPL-3.0-with-autoconf-exception"));
	}

	@ParameterizedTest
	@MethodSource("blockedVersionForms")
	void versionFormsOfBlockedLicensesAreBlocked(String declaredLicense) throws IOException {
		Path skillDir = skillWithLicenseField("spdx-blocked", declaredLicense);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed(), declaredLicense + " should block");
		assertEquals("blocked", meta(result, "license_status"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	private static Stream<Arguments> malformedButBlocked() {
		return Stream.of(Arguments.of("GPL-3.0 (or later)", "GPL-3.0", null),
				Arguments.of("LGPL-2.1 (or any later version)", "LGPL-2.1", null),
				Arguments.of("(gpl-3.0 or mit", "gpl-3.0", null), Arguments.of("mit or (gpl-3.0", "gpl-3.0", null),
				Arguments.of("mit or gpl-3.0)", "gpl-3.0", null),
				Arguments.of("MIT AND (GPL-3.0", "GPL-3.0", "unbalanced parenthesis"),
				Arguments.of("(GPL-3.0 OR MIT", "GPL-3.0", "unbalanced parenthesis"));
	}

	@ParameterizedTest
	@MethodSource("malformedButBlocked")
	void blockedLicenseInMalformedValueIsBlocked(String declaredLicense, String blocked, String syntaxError)
			throws IOException {
		Path skillDir = skillWithLicenseField("malformed-blocked", declaredLicense);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		List<Finding> findings = checks(result, "blocked_license");
		assertFalse(findings.isEmpty());
		assertEquals("License '" + blocked + "' is not allowed (restrictive/copyleft)", findings.get(0).getMessage());
		assertTrue(result.getWarnings().isEmpty());
		List<String> syntaxMessages = result.getMessages()
			.stream()
			.filter(message -> message.contains("is not valid SPDX syntax"))
			.toList();
		if (syntaxError != null) {
			assertEquals(
					List.of("License expression '" + declaredLicense + "' is not valid SPDX syntax: " + syntaxError),
					syntaxMessages);
		}
		else {
			assertTrue(syntaxMessages.isEmpty());
		}
	}

	@Test
	void orLaterFrontmatterIsNotHiddenByApacheLicenseFile() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("gpl-or-later-with-apache-file", "GPL-3.0+",
				APACHE_2_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	@Test
	void unlistedWithExceptionNeedsReview() throws IOException {
		Path lenientDir = skillWithLicenseField("spdx-unlisted-exception-lenient", "Apache-2.0 WITH Commons-Clause");
		Path strictDir = skillWithLicenseField("spdx-unlisted-exception-strict", "Apache-2.0 WITH Commons-Clause");

		ValidationResult lenient = new LicenseValidator().validate(lenientDir);
		ValidationResult strict = new LicenseValidator(true).validate(strictDir);

		assertEquals("unknown", meta(lenient, "license_status"));
		assertTrue(lenient.getWarnings()
			.stream()
			.anyMatch(warning -> warning.contains("'Apache-2.0 WITH Commons-Clause' not in allowlist")));
		assertFalse(strict.isPassed());
		assertFalse(checks(strict, "unknown_license").isEmpty());
	}

	private static Stream<Arguments> malformedExpressions() {
		return Stream.of(Arguments.of("(MIT OR Apache-2.0", "unbalanced parenthesis"),
				Arguments.of("MIT OR Apache-2.0)", "unbalanced parenthesis"),
				Arguments.of("MIT AND", "missing license identifier after 'AND'"),
				Arguments.of("OR MIT", "missing license identifier before 'OR'"),
				Arguments.of("MIT OR OR Apache-2.0", "missing license identifier before 'OR'"),
				Arguments.of("(MIT OR Apache-2.0) BSD-3-Clause", "missing operator before 'BSD-3-Clause'"),
				Arguments.of("MIT WITH", "missing exception identifier after 'WITH'"), Arguments
					.of("(MIT OR Apache-2.0) WITH LLVM-exception", "WITH must follow a single license identifier"));
	}

	@ParameterizedTest
	@MethodSource("malformedExpressions")
	void malformedSpdxExpressionIsRejected(String declaredLicense, String reason) throws IOException {
		Path skillDir = skillWithLicenseField("spdx-malformed", declaredLicense);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("unsupported", meta(result, "license_status"));
		List<Finding> findings = checks(result, "unsupported_license_expression");
		assertFalse(findings.isEmpty());
		assertEquals("License expression '" + declaredLicense + "' is not valid SPDX syntax: " + reason,
				findings.get(0).getMessage());
	}

	private static Stream<Arguments> freeTextNames() {
		return Stream.of(Arguments.of("MIT License", "allowed"), Arguments.of("The MIT License", "allowed"),
				Arguments.of("MIT XOR Apache-2.0", "unknown"),
				Arguments.of("LicenseRef-Example-with-attribution", "unknown"),
				Arguments.of("MIT License (MIT)", "unknown"), Arguments.of("Apache 2.0 (with exceptions)", "unknown"),
				Arguments.of("(MIT", "unknown"), Arguments.of("mit or (apache-2.0", "unknown"));
	}

	@ParameterizedTest
	@MethodSource("freeTextNames")
	void licenseNamesWithoutOperatorsAreNotSyntaxErrors(String declaredLicense, String status) throws IOException {
		Path skillDir = skillWithLicenseField("free-text-license", declaredLicense);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed(), declaredLicense + " should only warn");
		assertEquals(status, meta(result, "license_status"));
		assertTrue(checks(result, "unsupported_license_expression").isEmpty());
	}

	@Test
	void expressionIncludingLicenseFileIdIsConsistent() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("dual-license-apache-file", "MIT OR Apache-2.0",
				APACHE_2_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertTrue(result.isPassed());
		assertEquals("allowed", meta(result, "license_status"));
		assertTrue(checks(result, "frontmatter_license_mismatch").isEmpty());
	}

	@Test
	void expressionExcludingLicenseFileIdFailsClosed() throws IOException {
		Path skillDir = skillWithLicenseFieldAndFile("mit-and-cc-vs-apache-body", "MIT AND CC-BY-4.0",
				APACHE_2_LICENSE_TEXT);

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("conflict", meta(result, "license_status"));
		assertFalse(checks(result, "frontmatter_license_mismatch").isEmpty());
	}

	// ------------------------------------------------------------------
	// Tier 2: license files
	// ------------------------------------------------------------------

	@Test
	void detectsApacheLicenseFile() throws IOException {
		Path skillDir = skill("apache-file-only",
				"name: apache-file-only\ndescription: Skill with an Apache LICENSE file\n",
				Map.of("LICENSE.txt", APACHE_2_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("Apache-2.0"));
	}

	@Test
	void detectsMitLicenseFile() throws IOException {
		Path skillDir = skill("mit-only",
				"name: mit-only\ndescription: Skill with MIT LICENSE file but no frontmatter declaration\n",
				Map.of("LICENSE", MIT_LICENSE_TEXT));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("MIT"));
		assertEquals("license_file", meta(result, "license_source"));
	}

	@Test
	void detectsBsdLicenseFile() throws IOException {
		Path skillDir = skill("bsd-skill", "name: bsd-skill\ndescription: BSD licensed skill\n",
				Map.of("LICENSE.txt", """
						BSD 3-Clause License

						Redistribution and use in source and binary forms, with or without
						modification, are permitted provided that the following conditions are met:

						Neither the name of the copyright holder nor the names of its
						contributors may be used to endorse or promote products derived from
						this software without specific prior written permission.
						"""));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("BSD-3-Clause"));
	}

	// ------------------------------------------------------------------
	// Tier 3: SPDX headers
	// ------------------------------------------------------------------

	@Test
	void detectsSpdxHeaderInPython() throws IOException {
		Path skillDir = skill("spdx-skill",
				"name: spdx-skill\ndescription: A skill with SPDX headers in source files\n",
				Map.of("script.py", "# SPDX-License-Identifier: Apache-2.0\n# Copyright (c) 2024 Example\n", "utils.py",
						"# SPDX-License-Identifier: Apache-2.0\n# Utility functions\n"));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("Apache-2.0"));
		assertTrue(mentionsAny(result.getMessages(), "SPDX"));
	}

	@Test
	void multipleFilesSameLicense() throws IOException {
		Path dir = tmp.resolve("multi-spdx");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: multi-spdx\ndescription: Multiple files with SPDX\n---\n\n# Multi\n");
		for (int i = 0; i < 3; i++) {
			Files.writeString(dir.resolve("file" + i + ".py"), "# SPDX-License-Identifier: MIT\n# File " + i + "\n");
		}

		ValidationResult result = new LicenseValidator().validate(dir);

		assertTrue(result.isPassed());
		assertTrue(meta(result, "license").contains("MIT"));
	}

	private Path skillWithSource(String name, String filename, String source) throws IOException {
		return skill(name, "name: " + name + "\ndescription: Skill whose only license signal is a source header\n",
				Map.of(filename, source));
	}

	@Test
	void spdxOrExpressionWithGplIsBlocked() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skillWithSource("spdx-or-gpl", "script.py", "# SPDX-License-Identifier: MIT OR GPL-3.0\n"));

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertTrue(meta(result, "license").contains("GPL-3.0"));
		assertFalse(checks(result, "blocked_license").isEmpty());
		assertFalse(mentionsAny(result.getMessages(), "License: MIT (ALLOWED"));
	}

	@Test
	void spdxOrExpressionInBlockCommentIsBlocked() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skillWithSource("spdx-js-block", "script.js", "/* SPDX-License-Identifier: MIT OR GPL-3.0 */\n"));

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertTrue(meta(result, "license").contains("GPL-3.0"));
		assertFalse(meta(result, "license").contains("*/"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	@Test
	void spdxInlineBlockCommentWithCodeSuffixIsBlocked() throws IOException {
		ValidationResult result = new LicenseValidator().validate(skillWithSource("spdx-js-inline", "script.js",
				"/* SPDX-License-Identifier: MIT OR GPL-3.0 */ const x = 1;\n"));

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertTrue(meta(result, "license").contains("GPL-3.0"));
		assertFalse(meta(result, "license").contains("*/"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	@Test
	void spdxOrExpressionInHtmlCommentIsBlocked() throws IOException {
		ValidationResult result = new LicenseValidator().validate(
				skillWithSource("spdx-html-comment", "notes.md", "<!-- SPDX-License-Identifier: MIT OR GPL-3.0 -->\n"));

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertTrue(meta(result, "license").contains("GPL-3.0"));
		assertFalse(meta(result, "license").contains("-->"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	@Test
	void spdxAndExpressionWithGplIsBlocked() throws IOException {
		ValidationResult result = new LicenseValidator().validate(
				skillWithSource("spdx-and-gpl", "script.py", "# SPDX-License-Identifier: Apache-2.0 AND GPL-3.0\n"));

		assertFalse(result.isPassed());
		assertFalse(checks(result, "blocked_license").isEmpty());
		assertFalse("allowed".equals(meta(result, "license_status")));
	}

	@Test
	void spdxOrExpressionOfAllowedLicensesPasses() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skillWithSource("spdx-or-mit", "script.py", "# SPDX-License-Identifier: MIT OR MIT-0\n"));

		assertTrue(result.isPassed());
		assertEquals("allowed", meta(result, "license_status"));
		assertTrue(meta(result, "license").contains("MIT OR MIT-0"));
	}

	@Test
	void spdxHeaderInMarkdownTableIsBlocked() throws IOException {
		Path skillDir = skillWithSource("spdx-md-table", "reference.md",
				"| File | License |\n| --- | --- |\n| tool.py | `SPDX-License-Identifier: MIT OR GPL-3.0` |\n");

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.isPassed());
		assertEquals("blocked", meta(result, "license_status"));
		assertEquals("MIT OR GPL-3.0", meta(result, "license"));
		assertFalse(checks(result, "blocked_license").isEmpty());
	}

	@Test
	void malformedSpdxHeaderIsRejected() throws IOException {
		ValidationResult result = new LicenseValidator().validate(skillWithSource("spdx-malformed-header", "script.py",
				"# SPDX-License-Identifier: (MIT OR Apache-2.0\n"));

		assertFalse(result.isPassed());
		assertEquals("unsupported", meta(result, "license_status"));
		assertFalse(checks(result, "unsupported_license_expression").isEmpty());
	}

	static Stream<Arguments> headerCaptures() {
		return Stream.of(Arguments.of("reference.md",
				"| Check | Header |\n| --- | --- |\n| License | `// SPDX-License-Identifier: GPL-3.0` |\n", "GPL-3.0"),
				Arguments.of("notes.md", "Add `SPDX-License-Identifier: MIT OR GPL-3.0` to each file.\n",
						"MIT OR GPL-3.0"),
				Arguments.of("script.py", "# SPDX-License-Identifier: MIT # trailing comment\n", "MIT"),
				Arguments.of("script.js", "// SPDX-License-Identifier: MIT;\n", "MIT"),
				Arguments.of("script.js", "/* SPDX-License-Identifier: GPL-2.0-only WITH Classpath-exception-2.0 */\n",
						"GPL-2.0-only WITH Classpath-exception-2.0"),
				Arguments.of("script.sh", "# SPDX-License-Identifier: MIT | GPL-3.0\n", "MIT | GPL-3.0"));
	}

	@ParameterizedTest
	@MethodSource("headerCaptures")
	void spdxHeaderValueStopsAtEnclosingDelimiters(String filename, String content, String expected)
			throws IOException {
		Path source = tmp.resolve(filename);
		Files.writeString(source, content);

		assertEquals(expected, LicenseValidator.extractSpdxFromFile(source));
	}

	// ------------------------------------------------------------------
	// No license / unknown license
	// ------------------------------------------------------------------

	@Test
	void noLicenseStrictMode() throws IOException {
		Path skillDir = skill("no-license-skill",
				"name: no-license-skill\ndescription: A skill with no license information\n", Map.of());

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		assertTrue(joinedErrors(result).contains("no license"));
	}

	@Test
	void noLicenseLenientModeWarns() throws IOException {
		Path skillDir = skill("no-license-lenient",
				"name: no-license-lenient\ndescription: A skill with no license information\n", Map.of());

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertTrue(result.isPassed());
		assertTrue(result.getWarnings()
			.stream()
			.anyMatch(warning -> warning.contains("No license information found - manual review required")));
	}

	@Test
	void unknownLicenseStrictMode() throws IOException {
		Path skillDir = skill("unknown-strict",
				"name: unknown-strict\ndescription: Skill with unknown license\nlicense: Custom-Weird-License-1.0\n",
				Map.of());

		ValidationResult result = new LicenseValidator(true).validate(skillDir);

		assertFalse(result.isPassed());
		String errorText = joinedErrors(result);
		assertTrue(errorText.contains("not in") && errorText.contains("allowlist"));
	}

	// ------------------------------------------------------------------
	// Folder validation
	// ------------------------------------------------------------------

	@Test
	void validatesMultipleSkills() throws IOException {
		Path skillsFolder = tmp.resolve("skills");
		Files.createDirectories(skillsFolder.resolve("skill-one"));
		Files.createDirectories(skillsFolder.resolve("skill-two"));
		Files.writeString(skillsFolder.resolve("skill-one/SKILL.md"),
				"---\nname: skill-one\ndescription: First skill\nlicense: MIT\n---\n\n# Skill One\n");
		Files.writeString(skillsFolder.resolve("skill-two/SKILL.md"),
				"---\nname: skill-two\ndescription: Second skill\nlicense: Apache-2.0\n---\n\n# Skill Two\n");

		ValidationResult result = new LicenseValidator().validate(skillsFolder);

		assertTrue(result.isPassed());
		assertTrue(mentionsAny(result.getMessages(), "2 skill(s)"));
	}

	@Test
	void folderStopsOnBlockedSkill() throws IOException {
		Path skillsFolder = tmp.resolve("mixed-skills");
		Files.createDirectories(skillsFolder.resolve("good-skill"));
		Files.createDirectories(skillsFolder.resolve("bad-skill"));
		Files.writeString(skillsFolder.resolve("good-skill/SKILL.md"),
				"---\nname: good-skill\ndescription: Good\nlicense: MIT\n---\n\n# Good\n");
		Files.writeString(skillsFolder.resolve("bad-skill/SKILL.md"),
				"---\nname: bad-skill\ndescription: Bad\nlicense: GPL-3.0\n---\n\n# Bad\n");

		ValidationResult result = new LicenseValidator().validate(skillsFolder);

		assertFalse(result.isPassed());
		assertTrue(result.getErrors().stream().anyMatch(error -> error.contains("bad-skill")));
	}

	// ------------------------------------------------------------------
	// Normalization and helpers
	// ------------------------------------------------------------------

	@Test
	void caseInsensitiveMatching() throws IOException {
		Path skillDir = skill("case-test", "name: case-test\ndescription: Test case sensitivity\nlicense: mit\n",
				Map.of());

		assertTrue(new LicenseValidator().validate(skillDir).isPassed());
	}

	@Test
	void handlesLicenseSuffix() throws IOException {
		Path skillDir = skill("suffix-test",
				"name: suffix-test\ndescription: Test license suffix\nlicense: MIT License\n", Map.of());

		ValidationResult result = new LicenseValidator().validate(skillDir);
		assertTrue(result.isPassed() || meta(result, "license").contains("MIT"));
		assertEquals("allowed", meta(result, "license_status"));
	}

	@Test
	void normalizationVariants() {
		assertEquals("mit", LicenseValidator.normalizeLicenseId("  The MIT License "));
		assertEquals("mit", LicenseValidator.normalizeLicenseId("mit_licence"));
		assertEquals("gpl-3.0-or-later", LicenseValidator.normalizeLicenseId("GPL-3.0+"));
		assertEquals("apache-2.0", LicenseValidator.normalizeLicenseId("apache 2.0"));
	}

	@Test
	void licenseDetectionValueObject() {
		LicenseValidator.LicenseDetection detection = new LicenseValidator.LicenseDetection("MIT", "frontmatter",
				"high", "SKILL.md", "Found in license field");

		assertEquals("MIT", detection.getLicenseId());
		assertEquals("frontmatter", detection.getSource());
		assertEquals("high", detection.getConfidence());
		assertEquals("SKILL.md", detection.getFilePath());
		assertEquals("Found in license field", detection.getDetails());
	}

	@Test
	void isFileReferenceDetectsFilePointers() {
		assertTrue(LicenseValidator.isFileReference("See LICENSE.txt"));
		assertTrue(LicenseValidator.isFileReference("Refer to COPYING"));
		assertTrue(LicenseValidator.isFileReference("see license.md"));
		assertFalse(LicenseValidator.isFileReference("MIT"));
		assertFalse(LicenseValidator.isFileReference("Apache-2.0"));
	}

	// ------------------------------------------------------------------
	// Edge cases
	// ------------------------------------------------------------------

	@Test
	void emptyLicenseFileIsReported() throws IOException {
		Path skillDir = skill("empty-license", "name: empty-license\ndescription: Skill with empty license file\n",
				Map.of("LICENSE", ""));

		ValidationResult result = new LicenseValidator().validate(skillDir);

		assertFalse(result.getWarnings().isEmpty() && result.getErrors().isEmpty());
	}

	@Test
	void binaryFilesDoNotCrashHeaderScanning() throws IOException {
		Path dir = tmp.resolve("binary-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: binary-skill\ndescription: Skill with binary files\n---\n\n# Binary\n");
		Files.write(dir.resolve("image.png"), new byte[] { (byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 1 });

		assertNotNull(new LicenseValidator().validate(dir));
	}

	@Test
	void nonexistentPathIsHandled() {
		ValidationResult result = new LicenseValidator().validate(tmp.resolve("does-not-exist"));

		assertFalse(result.isPassed() && result.getWarnings().isEmpty());
	}

	@Test
	void malformedFrontmatterIsHandled() throws IOException {
		Path dir = tmp.resolve("malformed");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: malformed\ndescription: [invalid: yaml: here\nlicense: MIT\n---\n\n# Malformed\n");

		assertNotNull(new LicenseValidator().validate(dir));
	}

	@Test
	void metadataPopulation() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skillWithLicenseFieldAndFile("meta-skill", "MIT", MIT_LICENSE_TEXT));

		assertTrue(result.getMetadata().containsKey("license"));
		assertTrue(result.getMetadata().containsKey("license_status"));
		assertTrue(result.getMetadata().containsKey("license_source"));
	}

	@Test
	void successDetailCarriesLicenseStatus() throws IOException {
		ValidationResult result = new LicenseValidator()
			.validate(skill("ok-skill", "name: ok-skill\ndescription: Skill\nlicense: MIT\n", Map.of()));

		SuccessDetail detail = detail(result, "license_compliance");
		assertEquals("License compliance scan completed (allowed)", detail.getMessage());
		assertEquals("allowed", detail.getMetadata().get("license_status"));
	}

}

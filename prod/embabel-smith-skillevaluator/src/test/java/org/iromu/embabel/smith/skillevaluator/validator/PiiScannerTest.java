/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;

/**
 * Ports of the deterministic PII cases from {@code tests/validators/}
 * {@code test_pii_grouping.py}, {@code test_pii_ownership.py} and the PII half of
 * {@code test_security.py} (home paths, email and card false-positive guards).
 *
 * <p>
 * Two seams replace the Python suite's monkeypatching:
 * {@link PiiScanner#PiiScanner(java.util.Collection)} stands in for
 * {@code SecurityValidator(submitter_usernames=…)} so a result never depends on who runs
 * the suite, and {@link PiiScanner#PiiScanner(java.util.function.Function)} stands in for
 * {@code monkeypatch.setenv("SKILLEVALUATOR_SUBMITTER", …)} because the JVM cannot
 * rewrite its own environment.
 *
 * <p>
 * Waiver and policy expectations ({@code test_pii_waivers.py}, the
 * {@code apply_policy(result, PII.ownership_emails=info)} half of
 * {@code test_pii_ownership.py}) are not ported: this build has no waiver matcher or
 * severity-override layer, so the assertions below are the pre-policy ones — which
 * finding is reported, under which check name, at what severity, with what value. The
 * Tier 1 CLI cases in that file belong to the runner.
 */
class PiiScannerTest {

	@TempDir
	Path tmp;

	/**
	 * The address the ownership suite uses; a real domain so the pattern's placeholder
	 * guards pass.
	 */
	private static final String ADDRESS = "contact@acme-mail.com";

	// ------------------------------------------------------------- fixtures

	/** A skill directory whose SKILL.md body is {@code body}. */
	private Path skill(String name, String body) throws IOException {
		Path dir = tmp.resolve(name);
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: " + name + "\ndescription: A skill for the PII scan tests\n---\n\n" + body,
				StandardCharsets.UTF_8);
		return dir;
	}

	/** A directory that is scanned as-is (no manifest, so no skill discovery). */
	private Path loose(String relative, String content) throws IOException {
		Path file = tmp.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, content, StandardCharsets.UTF_8);
		return tmp;
	}

	/**
	 * Python {@code SecurityValidator(submitter_usernames=[])} — the deterministic
	 * default.
	 */
	private static PiiScanner scanner() {
		return new PiiScanner(List.of());
	}

	private static List<Finding> findings(ValidationResult result, String checkName) {
		return result.getFindings().stream().filter(f -> checkName.equals(f.getCheckName())).toList();
	}

	private static Set<String> checkNames(ValidationResult result) {
		return new LinkedHashSet<>(result.getFindings().stream().map(Finding::getCheckName).toList());
	}

	private static Object meta(Finding finding, String key) {
		return finding.getMetadata() == null ? null : finding.getMetadata().get(key);
	}

	/**
	 * The raw card candidates a file yields, mirroring the Python {@code _card_values}
	 * helper.
	 */
	private List<String> cardValues(String relative, String text) throws IOException {
		Path file = tmp.resolve(relative);
		Files.createDirectories(file.getParent());
		Files.writeString(file, text, StandardCharsets.UTF_8);
		List<String> values = new ArrayList<>();
		for (PiiScanner.Candidate candidate : scanner().scanFileForPii(file, Set.of(), file.getParent())) {
			if ("credit_cards".equals(candidate.category())) {
				values.add(candidate.matchedValue());
			}
		}
		return values;
	}

	private static boolean mentionsHome(List<String> findings) {
		return findings.stream().anyMatch(f -> f.toLowerCase().contains("home directory"));
	}

	// ------------------------------------------------------------- validator shape

	@Test
	void nameAndDescriptionComeFromTier1Commands() {
		PiiScanner scanner = new PiiScanner();
		assertEquals("PII Scan", scanner.getName());
		assertEquals("Detect PII and local identifiers", scanner.getDescription());
	}

	@Test
	void validateStampsValidatorIdentityOnTheResult() throws IOException {
		ValidationResult result = scanner().validate(skill("clean-skill", "# Clean\n\nNothing personal here.\n"));

		assertEquals("PII Scan", result.getValidatorName());
		assertEquals("Detect PII and local identifiers", result.getValidatorDescription());
		assertTrue(result.isPassed());
		assertTrue(result.getSuccessDetails().stream().anyMatch(d -> "pii_detection".equals(d.getCheckName())));
	}

	/** {@code test_pattern_loading}: the classpath YAML carries every PII family. */
	@Test
	void patternsLoadFromTheClasspathResource() {
		Map<String, Object> patterns = scanner().piiPatterns();
		for (String category : List.of("personal_paths", "home_paths", "emails", "phone_numbers", "ssn", "ip_addresses",
				"database_credentials", "jwt_tokens", "hardcoded_secrets", "webhook_urls", "aws_identifiers",
				"github_tokens", "private_keys", "credit_cards", "crypto_addresses", "mac_addresses",
				"gps_coordinates")) {
			assertTrue(patterns.containsKey(category), "missing PII category " + category);
		}
	}

	// ---------------------------------------------------------------- grouping

	/** {@code test_repeated_value_reports_one_finding_with_occurrence_list}. */
	@Test
	void repeatedValueFoldsIntoOneFindingWithOccurrenceSuffix() throws IOException {
		ValidationResult result = scanner().validate(loose("a.md", """
				# Notes
				Contact one person: contact@acme-mail.com
				Contact again: contact@acme-mail.com
				And once more: contact@acme-mail.com
				"""));

		List<Finding> emails = findings(result, "emails");
		assertEquals(1, emails.size());
		Finding finding = emails.get(0);
		assertEquals("Non-placeholder email address: " + ADDRESS + " \u2014 3 occurrences (a.md lines 2, 3, 4)",
				finding.getMessage());
		assertEquals(3, meta(finding, "occurrence_count"));
		assertEquals(List.of(Map.of("file", "a.md", "line", 2), Map.of("file", "a.md", "line", 3),
				Map.of("file", "a.md", "line", 4)), meta(finding, "occurrences"));
		assertEquals("a.md", finding.getFilePath());
		assertEquals(2, finding.getLineNumber().intValue());
		assertEquals("high", meta(finding, "confidence"));
	}

	/** {@code test_occurrence_list_formats_files_and_lines}. */
	@Test
	void occurrenceListGroupsFilesAndLineRuns() {
		List<PiiScanner.Occurrence> occurrences = List.of(new PiiScanner.Occurrence("a.md", 3),
				new PiiScanner.Occurrence("a.md", 9), new PiiScanner.Occurrence("b.md", 12));

		assertEquals("a.md lines 3, 9; b.md line 12", PiiScanner.formatOccurrences(occurrences));
	}

	/** {@code test_occurrence_list_clamps_files_and_lines}. */
	@Test
	void occurrenceListClampsFileAndLineCounts() {
		List<PiiScanner.Occurrence> manyFiles = new ArrayList<>();
		for (int file = 1; file <= 5; file++) {
			manyFiles.add(new PiiScanner.Occurrence("file" + file + ".md", 1));
		}
		assertEquals("file1.md line 1; file2.md line 1; file3.md line 1; +2 more file(s)",
				PiiScanner.formatOccurrences(manyFiles, 3, 10));

		List<PiiScanner.Occurrence> manyLines = new ArrayList<>();
		for (int line = 1; line <= 12; line++) {
			manyLines.add(new PiiScanner.Occurrence("a.md", line));
		}
		StringBuilder shown = new StringBuilder();
		for (int line = 1; line <= 10; line++) {
			shown.append(line > 1 ? ", " : "").append(line);
		}
		assertEquals("a.md lines " + shown + ", \u2026", PiiScanner.formatOccurrences(manyLines, 3, 10));
	}

	/** {@code test_different_values_are_not_folded_together}. */
	@Test
	void differentValuesAreNotFoldedTogether() throws IOException {
		ValidationResult result = scanner().validate(loose("a.md", """
				first: one@acme-mail.com
				second: two@acme-mail.com
				"""));

		assertEquals(2, findings(result, "emails").size());
		assertTrue(result.getFindings()
			.stream()
			.noneMatch(f -> meta(f, "occurrence_count") != null && ((Integer) meta(f, "occurrence_count")) > 1));
	}

	/**
	 * {@code test_matches_without_a_value_are_not_folded_across_lines}: a candidate that
	 * reports no value cannot be folded by value, so each line stays a finding.
	 */
	@Test
	void valuelessCandidatesAreNotFoldedTogether() throws IOException {
		Path folder = loose("a.md", "# placeholder\n");
		PiiScanner scanner = new PiiScanner(List.of()) {
			@Override
			List<PiiScanner.Candidate> scanFileForPii(Path filePath, Set<String> usernames, Path skillRoot) {
				return List.of(candidate("a.md", 1, null), candidate("a.md", 2, null),
						candidate("a.md", 2, "same@acme-mail.com"), candidate("a.md", 3, "same@acme-mail.com"));
			}
		};

		ValidationResult result = scanner.validate(folder);

		assertEquals(3, result.getFindings().size());
		Finding folded = result.getFindings()
			.stream()
			.filter(f -> meta(f, "occurrence_count") != null)
			.findFirst()
			.orElseThrow();
		assertEquals(2, meta(folded, "occurrence_count"));
	}

	private static PiiScanner.Candidate candidate(String file, int line, String value) {
		return new PiiScanner.Candidate(file, line, "line content", "high", "Non-placeholder email address",
				"suggestion", "emails", "high", value);
	}

	// ------------------------------------------------------------ home paths

	/**
	 * {@code test_author_home_directory_flagged}: the author's own home leaks identity.
	 */
	@Test
	void authorHomePathIsFlaggedFromFrontmatter() throws IOException {
		Path dir = tmp.resolve("author-home-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"), """
				---
				name: author-home-skill
				description: A skill whose author's own home directory leaks into the body
				metadata:
				  author: Example Author <author@example.com>
				---

				# Author Home Path Test

				Install from: /home/author/projects/secret/
				""", StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertTrue(mentionsHome(result.getErrors()), "author home path should be flagged: " + result.getErrors());
	}

	/** {@code test_submitter_home_directory_flagged}. */
	@Test
	void submitterHomePathIsFlagged() throws IOException {
		Path dir = skill("submitter-home-skill",
				"# Submitter Home Path Test\n\nWorkspace: /home/releasebot/work/mount-list/\n");

		ValidationResult result = new PiiScanner(List.of("releasebot")).validate(dir);

		assertTrue(mentionsHome(result.getErrors()), "submitter home path should be flagged");
	}

	/** {@code test_submitter_detected_from_environment} through the environment seam. */
	@Test
	void submitterIsResolvedFromTheEnvironmentSeam() throws IOException {
		Path dir = skill("env-home-skill", "# Env\n\nWorkspace: /home/ciuser/work/\n");
		String submitterVar = Constants.HOME_PATH_SUBMITTER_ENV_VARS.get(0);

		ValidationResult result = new PiiScanner(name -> submitterVar.equals(name) ? "ciuser" : null).validate(dir);

		assertTrue(mentionsHome(result.getErrors()), "submitter from the environment should be flagged");
	}

	/** {@code test_unrelated_home_roots_not_flagged}. */
	@Test
	void unrelatedHomeRootsAreNotFlagged() throws IOException {
		Path dir = skill("shared-home-skill", """
				# Shared Home Path Test

				Tool cache: /home/tool-cache/python/bin/python
				Shared data: /home/shared-data/captures/
				Team volume: /home/team-volume/releases/
				""");

		ValidationResult result = scanner().validate(dir);

		assertFalse(mentionsHome(concat(result)), "unrelated /home roots should not be flagged");
	}

	/**
	 * {@code test_other_persons_home_directory_not_flagged}: only own identities are
	 * protected.
	 */
	@Test
	void thirdPartyHomePathIsNotFlagged() throws IOException {
		Path dir = skill("third-party-home-skill", "# Third party\n\nRead /home/otheruser/notes.md\n");

		ValidationResult result = new PiiScanner(List.of("releasebot")).validate(dir);

		assertFalse(mentionsHome(concat(result)));
	}

	/**
	 * {@code test_unrelated_home_path_does_not_hide_a_personal_home_path_on_the_line}.
	 */
	@Test
	void unrelatedHomePathDoesNotHidePersonalHomePath() throws IOException {
		Path folder = loose("notes.md", "cp /home/runner/out.txt /home/alice/backup/\n");

		ValidationResult result = new PiiScanner(List.of("alice")).validate(folder);

		List<Object> values = findings(result, "home_paths").stream().map(f -> meta(f, "matched_value")).toList();
		assertEquals(List.of("/home/alice/"), values);
	}

	/** Without an author or submitter the home-path check is inert. */
	@Test
	void homeCheckIsInertWithoutAnIdentity() throws IOException {
		Path dir = skill("anonymous-home-skill", "# Anonymous\n\nRead /home/anyone/notes.md\n");

		ValidationResult result = scanner().validate(dir);

		assertFalse(mentionsHome(concat(result)));
	}

	/** A Proton virtual profile is not a personal home path. */
	@Test
	void protonVirtualProfileIsNotAPersonalPath() throws IOException {
		Path folder = loose("notes.md", "prefix pfx/drive_c/users/steamuser/docs/readme.txt\n");

		ValidationResult result = new PiiScanner(List.of("steamuser")).validate(folder);

		assertTrue(findings(result, "personal_paths").isEmpty(), "the Proton profile must not be PII");
	}

	/** A dot-dot segment after the Proton prefix is traversal, not a virtual profile. */
	@Test
	void traversalAfterProtonPrefixIsStillFlagged() throws IOException {
		Path folder = loose("notes.md", "prefix pfx/drive_c/users/steamuser/../../../etc/passwd\n");

		ValidationResult result = new PiiScanner(List.of("steamuser")).validate(folder);

		assertFalse(findings(result, "personal_paths").isEmpty(), "dot-dot after the profile is traversal");
	}

	/** The encoded form of the same traversal is flagged too. */
	@Test
	void encodedTraversalAfterProtonPrefixIsStillFlagged() throws IOException {
		Path folder = loose("notes.md", "prefix pfx/drive_c/users/steamuser/%2e%2e%2fetc%2fpasswd\n");

		ValidationResult result = new PiiScanner(List.of("steamuser")).validate(folder);

		assertFalse(findings(result, "personal_paths").isEmpty(), "percent-encoded dot-dot is traversal");
	}

	// ---------------------------------------------------------------- emails

	/** {@code test_detects_non_placeholder_email}. */
	@Test
	void nonPlaceholderEmailIsDetected() throws IOException {
		Path dir = skill("email-skill", "# Email Test\n\nContact john.doe@personal-email.com for support.\n");

		ValidationResult result = scanner().validate(dir);

		List<Finding> emails = findings(result, "emails");
		assertEquals(1, emails.size());
		assertEquals(Severity.HIGH, emails.get(0).getSeverity());
		assertEquals("john.doe@personal-email.com", meta(emails.get(0), "matched_value"));
	}

	/** {@code test_reserved_domain_emails_are_not_pii}. */
	@ParameterizedTest
	@ValueSource(strings = { "example.com", "sub.example.org", "test.com", "unit.invalid", "unit.localhost" })
	void reservedDomainEmailsAreNotPii(String domain) throws IOException {
		Path folder = loose("notes.md", "Write to docs@" + domain + " for the details.\n");

		ValidationResult result = scanner().validate(folder);

		assertTrue(findings(result, "emails").isEmpty(), "reserved domain " + domain + " is not PII");
	}

	/** An email inside a code comment is not contact data. */
	@Test
	void emailInsideCommentIsSkippedButBodyIsNot() throws IOException {
		Path folder = tmp.resolve("comments");
		Files.createDirectories(folder);
		Files.writeString(folder.resolve("tool.py"), "# reach the maintainer at commented@acme-mail.com\n"
				+ "# or also@acme-mail.com directly\n" + "value = 1\n", StandardCharsets.UTF_8);
		Files.writeString(folder.resolve("notes.md"), "Reach us at body@acme-mail.com\n", StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(folder);

		List<Object> values = findings(result, "emails").stream().map(f -> meta(f, "matched_value")).toList();
		assertEquals(List.of("body@acme-mail.com"), values);
	}

	// ------------------------------------------------------------ ownership

	/** {@code test_ownership_requires_explicit_policy_and_remains_visible}. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\u0000', textBlock = """
			pic     | contact@acme-mail.com
			email   | contact@acme-mail.com
			pic     | 'contact@acme-mail.com'
			pic     | "Maintainer <contact@acme-mail.com>"
			""")
	void structuredContactIsReportedAsOwnershipEmail(String field, String value) throws IOException {
		Path dir = tmp.resolve("owner-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: owner-skill\ndescription: A skill with a declared contact\nmetadata:\n  " + field + ": "
						+ value + "\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(List.of("ownership_emails"), List.copyOf(checkNames(result)));
		Finding finding = result.getFindings().get(0);
		assertEquals(Severity.HIGH, finding.getSeverity());
		assertEquals("Ownership contact email in skill metadata: " + ADDRESS, finding.getMessage());
		assertEquals(ADDRESS, meta(finding, "matched_value"));
	}

	/**
	 * {@code test_ambiguous_or_unrecognized_metadata_retains_ordinary_email_policy}:
	 * anything but a single-line literal {@code metadata.pic}/{@code metadata.email}
	 * stays an ordinary email finding.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "pic: contact@acme-mail.com", "metadata:\n  description: contact@acme-mail.com",
			"metadata:\n  nested:\n    pic: contact@acme-mail.com",
			"metadata:\n  pic: Contact contact@acme-mail.com for support", "metadata:\n  pic: 'contact@acme-mail.com>'",
			"metadata:\n  pic: |\n    contact@acme-mail.com",
			"metadata:\n  pic: contact@acme-mail.com\n  pic: someone@acme-mail.com",
			"metadata: [\n  pic: contact@acme-mail.com",
			"original: &owner contact@acme-mail.com\nmetadata:\n  pic: *owner",
			"original: &owner {pic: contact@acme-mail.com}\nmetadata:\n  <<: *owner", })
	void ambiguousMetadataKeepsOrdinaryEmailPolicy(String frontmatter) throws IOException {
		Path dir = tmp.resolve("ambiguous-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: ambiguous-skill\ndescription: A skill with awkward metadata\n" + frontmatter + "\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(Set.of("emails"), checkNames(result), "unrecognized contact shape stays ordinary email PII");
	}

	/** {@code test_same_address_in_body_and_metadata_stays_independently_blocking}. */
	@Test
	void sameAddressInBodyAndMetadataStaysIndependent() throws IOException {
		Path dir = tmp.resolve("both-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: both-skill\ndescription: A skill that repeats its contact\nmetadata:\n  pic: " + ADDRESS
						+ "\n---\n\nContact " + ADDRESS + " for access.\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(Set.of("emails", "ownership_emails"), checkNames(result));
		assertTrue(result.getFindings().stream().allMatch(f -> (Integer) meta(f, "occurrence_count") == 1));
	}

	/** {@code test_flow_metadata_does_not_hide_another_address_on_the_same_line}. */
	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void flowMetadataDoesNotHideAnotherAddress(boolean ownerFirst) throws IOException {
		List<String> fields = new ArrayList<>(List.of("pic: " + ADDRESS, "description: " + ADDRESS));
		if (!ownerFirst) {
			fields.add(0, fields.remove(fields.size() - 1));
		}
		Path dir = tmp.resolve("flow-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: flow-skill\ndescription: A skill with flow metadata\nmetadata: {"
						+ String.join(", ", fields) + "}\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(Set.of("emails", "ownership_emails"), checkNames(result));
	}

	/** {@code test_author_exemption_preserves_adjacent_flow_contact_offsets}. */
	@ParameterizedTest
	@CsvSource({ "a@company.com,false", "a@company.com,true", "long-author-address@company.com,false",
			"long-author-address@company.com,true" })
	void authorExemptionPreservesAdjacentFlowContact(String author, boolean ordinary) throws IOException {
		String extra = ordinary ? ", description: " + ADDRESS : "";
		Path dir = tmp.resolve("author-flow-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: author-flow-skill\ndescription: A skill with an author and a contact\nmetadata: {\n"
						+ "  author: \"Author <" + author + ">\", pic: " + ADDRESS + extra + "}\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		Set<String> expected = ordinary ? Set.of("ownership_emails", "emails") : Set.of("ownership_emails");
		assertEquals(expected, checkNames(result));
		assertTrue(result.getFindings().stream().allMatch(f -> ADDRESS.equals(meta(f, "matched_value"))));
	}

	/** {@code test_other_markdown_files_cannot_claim_skill_ownership_metadata}. */
	@Test
	void proseDocumentCannotClaimOwnership() throws IOException {
		Path dir = tmp.resolve("contacts-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: contacts-skill\ndescription: A skill with a side document\n---\n", StandardCharsets.UTF_8);
		Files.writeString(dir.resolve("contacts.md"), "---\nname: contacts\nmetadata:\n  pic: " + ADDRESS + "\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(Set.of("emails"), checkNames(result));
	}

	/** {@code test_nested_skill_manifest_cannot_claim_ownership_metadata}. */
	@ParameterizedTest
	@CsvSource({ "references,SKILL.md", "references/vendored/deep,skill.md", "scripts,SKILL.md" })
	void nestedManifestCannotClaimOwnership(String nested, String name) throws IOException {
		Path dir = skill("nested-skill", "# Nested\n");
		Path folder = dir.resolve(nested);
		Files.createDirectories(folder);
		Files.writeString(folder.resolve(name),
				"---\nname: vendored\nmetadata:\n  pic: " + ADDRESS + "\n---\nVendored notes.\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertEquals(List.of("emails"), List.copyOf(checkNames(result)));
		assertEquals(Severity.HIGH, result.getFindings().get(0).getSeverity());
		assertEquals(List.of(Map.of("file", nested + "/" + name, "line", 4)),
				meta(result.getFindings().get(0), "occurrences"));
	}

	/** {@code test_root_ownership_does_not_cover_nested_manifest_contacts}. */
	@Test
	void rootOwnershipDoesNotCoverNestedManifestContacts() throws IOException {
		Path dir = tmp.resolve("mixed-ownership-skill");
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("SKILL.md"),
				"---\nname: mixed-ownership-skill\ndescription: A skill with a nested copy\nmetadata:\n  pic: "
						+ ADDRESS + "\n---\n",
				StandardCharsets.UTF_8);
		Files.createDirectories(dir.resolve("references"));
		Files.writeString(dir.resolve("references").resolve("SKILL.md"),
				"---\nname: vendored\nmetadata:\n  pic: " + ADDRESS + "\n  email: other@acme-mail.com\n---\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		Set<String> pairs = result.getFindings()
			.stream()
			.map(f -> f.getCheckName() + "=" + meta(f, "matched_value"))
			.collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
		assertEquals(Set.of("ownership_emails=" + ADDRESS, "emails=" + ADDRESS, "emails=other@acme-mail.com"), pairs);
	}

	// ------------------------------------------------------ numeric safeguards

	/** An SSN is reported with the value shown in full (it is not a credential). */
	@Test
	void ssnIsDetectedAndNotRedacted() throws IOException {
		Path folder = loose("notes.md", "Employee record 123-45-6789 on file.\n");

		ValidationResult result = scanner().validate(folder);

		List<Finding> ssn = findings(result, "ssn");
		assertEquals(1, ssn.size());
		assertEquals(Severity.CRITICAL, ssn.get(0).getSeverity());
		assertEquals("Social Security Number: 123-45-6789", ssn.get(0).getMessage());
	}

	/** A secret-family value is masked, and its line is dropped from the report. */
	@Test
	void sensitiveCategoryIsRedacted() throws IOException {
		Path folder = loose("notes.md",
				"-----BEGIN RSA PRIVATE KEY-----\nMIIEowIBAAKCAQEAy0HwE5Vp0kK3nZ7dLmZq0X8vYb2cD4eF6gH8iJ0kL2mN4oP6qR8sT0uV\n");

		ValidationResult result = scanner().validate(folder);

		List<Finding> keys = findings(result, "private_keys");
		assertEquals(1, keys.size());
		assertEquals("Private key in PEM format: [REDACTED]", keys.get(0).getMessage());
		assertEquals("[REDACTED]", meta(keys.get(0), "matched_value"));
		assertNull(keys.get(0).getLineContent());
		assertFalse(keys.get(0).getMetadata().containsKey("evidence_sha256"),
				"a digest of a masked credential is offline-testable");
	}

	/** A non-sensitive finding carries the waiver-identity digest upstream computes. */
	@Test
	void nonSensitiveFindingCarriesEvidenceDigest() throws IOException {
		Path folder = loose("notes.md", "Write to " + ADDRESS + " for the details.\n");

		ValidationResult result = scanner().validate(folder);

		Finding email = findings(result, "emails").get(0);
		assertEquals("f80bfa21471308bd0127fa2c0a2f6572a4c215dbcd8a2877eb9b3f9b74191297",
				meta(email, "evidence_sha256"));
	}

	/** {@code test_credit_card_shape_in_timestamp_or_hash_context_not_flagged}. */
	@ParameterizedTest
	@ValueSource(strings = { "timestamp_microseconds: 1707938736269577", "epoch nanoseconds = 1707938736269577",
			"{\"unix_time\": 1707938736269577}", "sha256: 1707938736269577", "checksum=1707938736269577",
			"build id_1707938736269577", "1707938736269577_suffix", "event_timestamp: 1707938736269577",
			"epoch_us: 1707938736269577", })
	void cardShapeInTechnicalContextIsNotFlagged(String line) throws IOException {
		assertTrue(cardValues("data.txt", line + "\n").isEmpty(), "technical value read as a card: " + line);
	}

	/** {@code test_credit_card_next_to_technical_or_test_value_still_flagged}. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\u0000', textBlock = """
			timestamp_microseconds: 1707938736269577 card_number: 4532015112830366|4532015112830366
			Use 4111-1111-1111-1111 or 4532 0151 1283 0366|4532 0151 1283 0366
			checksum verified; card: 4532015112830366|4532015112830366
			Use _4532015112830366_ at checkout|4532015112830366
			card_4532015112830366|4532015112830366
			creditCard_4532015112830366|4532015112830366
			https://pay.acme.io/charge?q=card%3D4532015112830366|4532015112830366
			""")
	void cardNextToTechnicalValueStillFlagged(String line, String card) throws IOException {
		assertEquals(List.of(card), cardValues("data.txt", line + "\n"));
	}

	/** {@code test_known_test_card_in_any_grouping_not_flagged}. */
	@ParameterizedTest
	@ValueSource(strings = { "4111 1111 1111 1111", "5500 0000 0000 0004", "4111111111111111" })
	void knownTestCardIsExcepted(String card) throws IOException {
		assertTrue(cardValues("data.txt", "Sandbox card: " + card + "\n").isEmpty());
	}

	/** {@code test_credit_card_shape_inside_guid_not_flagged}. */
	@ParameterizedTest
	@CsvSource(delimiter = '|', quoteCharacter = '\u0000', textBlock = """
			00000000-0000-0000-c000-000000000046
			{00000000-0000-0000-C000-000000000046}
			00000000-0000-0000-0000-000000000000
			45320151-1283-0366-c000-000000000046
			ABCDEFAB-CDEF-ABCD-4532-015112830366
			""")
	void guidShapeIsNotACard(String identifier) throws IOException {
		assertTrue(cardValues("com.py", "IID_IUNKNOWN_TEXT = \"" + identifier + "\"\n").isEmpty(),
				"GUID digits read as a card: " + identifier);
	}

	/** {@code test_guid_does_not_hide_adjacent_payment_card}. */
	@ParameterizedTest
	@CsvSource({ "4532015112830366,false", "4532015112830366,true", "4532-0151-1283-0366,false",
			"4532 0151 1283 0366,true" })
	void guidDoesNotHideAdjacentPaymentCard(String cardNumber, boolean cardFirst) throws IOException {
		List<String> fields = new ArrayList<>(
				List.of("identifier=\"45320151-1283-0366-c000-000000000046\"", "card_number=\"" + cardNumber + "\""));
		if (cardFirst) {
			fields.add(0, fields.remove(fields.size() - 1));
		}

		assertEquals(List.of(cardNumber), cardValues("data.txt", String.join("; ", fields) + "\n"));
	}

	/** {@code test_non_luhn_number_does_not_hide_later_card}. */
	@Test
	void nonLuhnNumberDoesNotHideLaterCard() throws IOException {
		assertEquals(List.of("4532 0151 1283 0366"),
				cardValues("data.txt", "ref 1234-5678-9012-3456 card 4532 0151 1283 0366\n"));
	}

	/** {@code test_credit_card_non_luhn_number_not_flagged}. */
	@Test
	void nonLuhnNumberIsNotACard() throws IOException {
		assertTrue(cardValues("data.txt", "ID: 1234567890123456\n").isEmpty());
	}

	/** {@code test_zero_only_number_not_flagged_as_credit_card}. */
	@ParameterizedTest
	@ValueSource(strings = { "0000000000000000", "0000-0000-0000-0000", "0000 0000 0000 0000" })
	void zeroOnlyNumberIsNotACard(String number) throws IOException {
		assertTrue(cardValues("data.txt", "card_number=" + number + "\n").isEmpty());
	}

	/**
	 * {@code test_phone_bare_ten_digit_run_not_flagged} and its formatted counterpart.
	 */
	@Test
	void bareDigitRunIsNotAPhoneNumberButFormattedIs() throws IOException {
		ValidationResult bare = scanner()
			.validate(loose("plugins.md", "duration: 1000000000 ns\noffset 2147483647\nid 5551234567\n"));
		assertTrue(findings(bare, "phone_numbers").isEmpty(), "bare digit runs read as phone numbers");

		ValidationResult formatted = scanner().validate(loose("phone.md", "Call 555-123-4567 for support.\n"));
		assertFalse(findings(formatted, "phone_numbers").isEmpty(), "a formatted number must still be flagged");
	}

	/** {@code test_phone_scientific_notation_matrix_not_flagged}. */
	@Test
	void scientificNotationExponentIsNotAPhonePrefix() throws IOException {
		Path folder = loose("matrix.txt",
				"1.000000e+00 0.000000e+00 0.000000e+00 1.234567e-01\n+1.000000e+00 -2.500000E+03\n");

		ValidationResult result = scanner().validate(folder);

		assertTrue(findings(result, "phone_numbers").isEmpty());
	}

	/** {@code test_phone_after_scientific_notation_still_flagged}. */
	@Test
	void phoneAfterScientificNotationStillFlagged() throws IOException {
		Path folder = loose("notes.txt", "scale 1.000000e+00 0.000000e+00, support +44 20 7946 0958\n");

		ValidationResult result = scanner().validate(folder);

		assertEquals(List.of("+44 20 7946 0958"),
				findings(result, "phone_numbers").stream().map(f -> meta(f, "matched_value")).toList());
	}

	// ------------------------------------------------------------- scan scope

	/** {@code test_pii_skips_evals_directory}: agent transcripts are not user PII. */
	@Test
	void evalsDirectoryIsSkipped() throws IOException {
		Path dir = skill("eval-skill", "# Eval skill\n");
		Path trial = dir.resolve("evals/results/run/trials/0");
		Files.createDirectories(trial);
		Files.writeString(trial.resolve("claude-code.txt"), "contact@acme-mail.com 123-45-6789\n",
				StandardCharsets.UTF_8);

		ValidationResult result = scanner().validate(dir);

		assertTrue(result.isPassed(), "PII under evals/ must be ignored: " + result.getFindings());
	}

	// ---------------------------------------------------------------- helpers

	private static List<String> concat(ValidationResult result) {
		List<String> all = new ArrayList<>(result.getErrors());
		all.addAll(result.getWarnings());
		return all;
	}

	/**
	 * Guards the digest recipe the waiver layer keys on: sha256(category + NUL +
	 * casefolded value).
	 */
	@Test
	void evidenceDigestFollowsTheUpstreamRecipe() throws Exception {
		assertEquals(sha256("home_paths\u0000/home/alice/"), PiiScanner.evidenceSha256("home_paths", "/home/Alice/"));
	}

	private static String sha256(String value) throws NoSuchAlgorithmException {
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
	}

}

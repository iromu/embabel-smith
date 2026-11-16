/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/** Ports of tests/validators/test_unicode_smuggle.py. */
class UnicodeSmuggleValidatorTest {

	@TempDir
	Path tmp;

	/** Create a single file inside a skill-like directory (SKILL.md included). */
	private Path makeSkill(String filename, String content) throws IOException {
		Path skillDir = tmp.resolve("skills/test-skill-" + filename.replace('.', '_'));
		Files.createDirectories(skillDir);
		Path manifest = skillDir.resolve("SKILL.md");
		if (!Files.exists(manifest)) {
			Files.writeString(manifest, "---\nname: test-skill\ndescription: test\n---\n# Test\n");
		}
		Files.writeString(skillDir.resolve(filename), content);
		return skillDir;
	}

	private static String tagEncode(String ascii) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < ascii.length(); i++) {
			sb.appendCodePoint(0xE0000 + ascii.charAt(i));
		}
		return sb.toString();
	}

	private static List<Finding> byCheckName(ValidationResult result, String checkName) {
		return result.getFindings().stream().filter(f -> f.getCheckName().equals(checkName)).toList();
	}

	private static List<Finding> bySeverity(ValidationResult result, Severity severity) {
		return result.getFindings().stream().filter(f -> f.getSeverity() == severity).toList();
	}

	// ------------------------------------------------------------------

	@Test
	void cleanFilePasses() throws IOException {
		Path skill = makeSkill("readme.md", "# Hello World\nNormal text.\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
	}

	@Test
	void emptyFilePasses() throws IOException {
		Path skill = makeSkill("empty.txt", "");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
	}

	@Test
	void bomAtFileStartIsInfo() throws IOException {
		Path skill = makeSkill("bom.md", "\uFEFF# Title\nBody text\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
		List<Finding> bom = byCheckName(result, "bom_marker");
		assertEquals(1, bom.size());
		assertEquals(Severity.INFO, bom.get(0).getSeverity());
	}

	@Test
	void bomNotAtStartIsNotInfo() throws IOException {
		Path skill = makeSkill("mid_bom.md", "Hello\uFEFFworld\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(byCheckName(result, "bom_marker").isEmpty());
		assertFalse(result.getFindings().isEmpty());
	}

	@Test
	void isolatedZwspIsLow() throws IOException {
		Path skill = makeSkill("zwsp.md", "hello\u200Bworld\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertEquals(1, result.getFindings().size());
		assertEquals(Severity.LOW, result.getFindings().get(0).getSeverity());
		assertEquals("isolated_invisible_char", result.getFindings().get(0).getCheckName());
	}

	@Test
	void multipleZwspPerLineIsMedium() throws IOException {
		Path skill = makeSkill("multi_zw.md", "a\u200B\u200C\u200Db\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		List<Finding> findings = byCheckName(result, "suspicious_invisible_chars");
		assertEquals(1, findings.size());
		assertEquals(Severity.MEDIUM, findings.get(0).getSeverity());
	}

	@Test
	void bidiOverrideIsHigh() throws IOException {
		Path skill = makeSkill("bidi.md", "normal\u202Etext\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertFalse(result.isPassed());
		List<Finding> bidi = byCheckName(result, "bidi_override");
		assertEquals(1, bidi.size());
		assertEquals(Severity.HIGH, bidi.get(0).getSeverity());
		assertTrue(bidi.get(0).getMessage().contains("CVE-2021-42574"));
	}

	@Test
	void ltrRtlMarksDetected() throws IOException {
		Path skill = makeSkill("ltr_rtl.md", "a\u200Eb\u200Fc\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		// Two separate marks (not consecutive), so each gets its own finding
		assertEquals(2, byCheckName(result, "bidi_override").size());
	}

	@Test
	void tagPayloadIsCritical() throws IOException {
		String hidden = tagEncode("secret");
		Path skill = makeSkill("smuggle.md", "visible" + hidden + "text\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertFalse(result.isPassed());
		List<Finding> crit = bySeverity(result, Severity.CRITICAL);
		assertEquals(1, crit.size());
		assertEquals("ascii_smuggling_payload", crit.get(0).getCheckName());
		assertEquals("secret", crit.get(0).getMetadata().get("decoded_payload"));
	}

	@Test
	void tagPayloadDecodedInMessage() throws IOException {
		String hidden = tagEncode("ignore instructions");
		Path skill = makeSkill("payload.md", "# Title\n" + hidden + "\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		List<Finding> crit = bySeverity(result, Severity.CRITICAL);
		assertEquals(1, crit.size());
		assertTrue(crit.get(0).getMessage().contains("ignore instructions"));
	}

	@Test
	void runOf10IsHigh() throws IOException {
		Path skill = makeSkill("run10.md", "before" + "\u200B".repeat(10) + "after\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertFalse(result.isPassed());
		List<Finding> high = bySeverity(result, Severity.HIGH);
		assertEquals(1, high.size());
		assertEquals(10, high.get(0).getMetadata().get("consecutive_run"));
	}

	@Test
	void runOf40IsCritical() throws IOException {
		Path skill = makeSkill("run40.md", "before" + "\u200B".repeat(40) + "after\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertFalse(result.isPassed());
		List<Finding> crit = bySeverity(result, Severity.CRITICAL);
		assertEquals(1, crit.size());
		assertEquals("long_invisible_run", crit.get(0).getCheckName());
	}

	@Test
	void deprecatedControlIsMedium() throws IOException {
		Path skill = makeSkill("deprecated.md", "text\u206Amore\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertEquals(1, result.getFindings().size());
		assertEquals(Severity.MEDIUM, result.getFindings().get(0).getSeverity());
	}

	@Test
	void basicVariationSelectorIsLow() throws IOException {
		Path skill = makeSkill("vs.md", "a\uFE0Fb\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertEquals(1, result.getFindings().size());
		assertEquals(Severity.LOW, result.getFindings().get(0).getSeverity());
	}

	@Test
	void nbspNotDetectedByDefault() throws IOException {
		Path skill = makeSkill("nbsp_default.md", "hello\u00A0world\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
	}

	@Test
	void nbspDetectedWithFlag() throws IOException {
		Path skill = makeSkill("nbsp_flag.md", "hello\u00A0world\n");
		ValidationResult result = new UnicodeSmuggleValidator(true).validate(skill);
		assertEquals(1, result.getFindings().size());
		assertEquals("confusable_spaces", result.getFindings().get(0).getMetadata().get("unicode_category"));
	}

	@Test
	void binaryFileSkipped() throws IOException {
		Path skill = makeSkill("clean.md", "# Clean\n");
		Files.write(skill.resolve("image.png"),
				new byte[] { (byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13 });
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
	}

	@Test
	void fileWithNullBytesSkipped() throws IOException {
		Path skill = makeSkill("clean2.md", "# Clean\n");
		Files.write(skill.resolve("data.txt"),
				"some text\u0000binary data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertTrue(result.isPassed());
	}

	@Test
	void multipleSkillsInFolder() throws IOException {
		Path skillsDir = tmp.resolve("skills-folder");
		Files.createDirectories(skillsDir);
		for (String skillName : List.of("skill-a", "skill-b")) {
			Path sd = skillsDir.resolve(skillName);
			Files.createDirectories(sd);
			Files.writeString(sd.resolve("SKILL.md"),
					"---\nname: " + skillName + "\ndescription: test\n---\n# " + skillName + "\n");
		}
		Files.writeString(skillsDir.resolve("skill-a/notes.md"), "text" + tagEncode("bad") + "\n",
				java.nio.charset.StandardCharsets.UTF_8);

		ValidationResult result = new UnicodeSmuggleValidator().validate(skillsDir);
		assertFalse(result.isPassed());
		assertTrue(bySeverity(result, Severity.CRITICAL).size() >= 1);
	}

	@Test
	void noScannableFilesWarns() throws IOException {
		Path emptyDir = Files.createDirectories(tmp.resolve("empty"));
		ValidationResult result = new UnicodeSmuggleValidator().validate(emptyDir);
		assertFalse(result.getWarnings().isEmpty());
	}

	@Test
	void invisibleOperatorsAreMedium() throws IOException {
		Path skill = makeSkill("math.md", "f\u2061(x)\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertEquals(1, result.getFindings().size());
		assertEquals(Severity.MEDIUM, result.getFindings().get(0).getSeverity());
	}

	@Test
	void mixedCategoriesInConsecutiveRun() throws IOException {
		String run = "\u200B\u200C\u200D\u200B\u200C\u200D\u200B\u200C\u200D\u200B";
		Path skill = makeSkill("mixed.md", "text" + run + "end\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		List<Finding> high = bySeverity(result, Severity.HIGH);
		assertEquals(1, high.size());
		assertEquals(10, high.get(0).getMetadata().get("consecutive_run"));
	}

	@Test
	void multipleLinesProduceMultipleFindings() throws IOException {
		Path skill = makeSkill("multi.md", "line1\u200Bx\nline2\u200By\n");
		ValidationResult result = new UnicodeSmuggleValidator().validate(skill);
		assertEquals(2, result.getFindings().size());
		assertEquals(1, result.getFindings().get(0).getLineNumber());
		assertEquals(2, result.getFindings().get(1).getLineNumber());
	}

	@Test
	void validatorNameAndDescriptionMatchPython() {
		UnicodeSmuggleValidator v = new UnicodeSmuggleValidator();
		assertEquals("Unicode Smuggling Detection", v.getName());
		assertEquals("Detect invisible Unicode characters and ASCII smuggling", v.getDescription());
	}

}

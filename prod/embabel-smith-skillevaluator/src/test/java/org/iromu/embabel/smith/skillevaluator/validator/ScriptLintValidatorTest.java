/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;

/**
 * Ports of {@code tests/validators/test_script_lint.py}, extended with the cases the Java
 * port decides differently from an {@code ast} walk.
 *
 * <p>
 * Every expectation here was checked against the upstream Python validator run as an
 * oracle over the same synthetic skills, so a test that asserts a value the Python code
 * does not produce is deliberate: it is marked with a <strong>Divergence</strong> javadoc
 * note explaining what {@code ast} would have done and why the line/regex model cannot.
 * The {@code deep_nesting} depth values in {@link #nestingDepthMatchesUpstream} are the
 * depths CPython's {@code ast} reports for the same snippets.
 */
class ScriptLintValidatorTest {

	@TempDir
	Path tmp;

	// ------------------------------------------------------------ fixtures and helpers

	/** Python conftest {@code skill_with_scripts}: SKILL.md plus an empty scripts/. */
	private Path skillWithScripts() throws IOException {
		Path skill = tmp.resolve("scripted-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: scripted-skill\ndescription: test\n---\n\n# Test\n");
		Files.createDirectories(skill.resolve("scripts"));
		return skill;
	}

	/** A skill directory with SKILL.md and no script directory at all. */
	private Path skillWithoutScripts(String name) throws IOException {
		Path skill = tmp.resolve(name);
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: " + name + "\ndescription: test\n---\n");
		return skill;
	}

	private static Path write(Path directory, String name, String body) throws IOException {
		Files.createDirectories(directory);
		Path file = directory.resolve(name);
		Files.writeString(file, body);
		return file;
	}

	private static List<String> checkNames(ValidationResult result) {
		return result.getFindings().stream().map(Finding::getCheckName).toList();
	}

	private static Finding only(ValidationResult result, String checkName) {
		List<Finding> matches = result.getFindings().stream().filter(f -> f.getCheckName().equals(checkName)).toList();
		assertEquals(1, matches.size(), "expected exactly one " + checkName + " in " + checkNames(result));
		return matches.get(0);
	}

	private static List<String> successMessages(ValidationResult result) {
		return result.getSuccessDetails().stream().map(d -> d.getMessage()).toList();
	}

	private static boolean anyContains(List<String> haystack, String needle) {
		return haystack.stream().anyMatch(item -> item.contains(needle));
	}

	/**
	 * Lint one script body placed in {@code scripts/} and return the modelled nesting
	 * depth.
	 */
	private static int nestingDepth(String source) {
		return ScriptLintValidator.nestingDepth(PythonSyntax.model(source));
	}

	// ------------------------------------------------------------------ identity

	@Test
	void validatorNameAndDescription() {
		ScriptLintValidator validator = new ScriptLintValidator();
		assertEquals("Script Lint (Advisory)", validator.getName());
		assertEquals("AST-based code quality checks for skill scripts", validator.getDescription());
	}

	// -------------------------------------------------------------- discovery shape

	@Test
	void noScriptsDirectory() throws IOException {
		ValidationResult result = new ScriptLintValidator().validate(skillWithoutScripts("no-scripts"));

		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
		assertEquals(List.of("lint"), result.getSuccessDetails().stream().map(d -> d.getCheckName()).toList());
		assertTrue(anyContains(successMessages(result), "No scripts/ or tools/ directory found"));
	}

	@Test
	void scriptsDirectoryWithoutPythonScripts() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "notes.md", "hi 42\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result), "No Python scripts found in scripts/ or tools/"));
	}

	@Test
	void scriptsDirectoryWithOnlyNodeScripts() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "a.js", "console.log(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result),
				"No Python scripts found in scripts/ or tools/; 1 Node script(s) not linted "
						+ "(script lint parses Python only)"));
	}

	/** Ports test_node_scripts_reported_as_not_linted. */
	@Test
	void nodeScriptsReportedAsNotLinted() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "a.mjs", "console.log('a')\n");
		write(skill.resolve("scripts"), "b.js", "console.log('b')\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result), "2 Node script(s) not linted"));
	}

	/** Ports test_node_scripts_reported_alongside_python_lint_findings. */
	@Test
	void nodeScriptsReportedAlongsidePythonLintFindings() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "flat.py", "#!/usr/bin/env python3\nimport sys\nprint(sys.argv)\n");
		write(skill.resolve("scripts"), "a.js", "console.log('a')\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("flat_script"));
		Finding note = only(result, "node_scripts_not_linted");
		assertEquals(Severity.INFO, note.getSeverity());
		assertEquals("SCRIPT_LINT", note.getCategory());
		assertEquals("1 Node script(s) not linted (script lint parses Python only)", note.getMessage());
		// Upstream names the containing directory, not the file.
		assertEquals("scripts", note.getFilePath());
	}

	/** Ports test_node_note_survives_folder_aggregation. */
	@Test
	void nodeNoteSurvivesFolderAggregation() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "flat.py", "#!/usr/bin/env python3\nimport sys\nprint(sys.argv)\n");
		write(skill.resolve("scripts"), "a.js", "console.log('a')\n");

		ValidationResult single = new ScriptLintValidator().validate(skill);
		ValidationResult folder = new ValidationResult();
		folder.mergeWithPrefix(single, "skill-a");

		assertTrue(folder.getFindings()
			.stream()
			.anyMatch(f -> f.getCheckName().equals("node_scripts_not_linted")
					&& f.getFilePath().startsWith("[skill-a]")));
	}

	/** Ports test_node_scripts_do_not_add_a_second_completed_check. */
	@Test
	void nodeScriptsDoNotAddASecondCompletedCheck() throws IOException {
		Path skill = skillWithScripts();
		Path node = write(skill.resolve("scripts"), "a.mjs", "console.log('a')\n");

		ValidationResult withNode = new ScriptLintValidator().validate(skill);
		Files.delete(node);
		ValidationResult withoutNode = new ScriptLintValidator().validate(skill);

		assertEquals(withoutNode.getSummary().getChecksPerformed(), withNode.getSummary().getChecksPerformed());
		assertEquals(withoutNode.getSuccessDetails().size(), withNode.getSuccessDetails().size());
	}

	/** Ports test_node_scripts_in_tools_dir_and_clean_python_are_both_reported. */
	@Test
	void nodeScriptsInToolsDirAndCleanPythonAreBothReported() throws IOException {
		Path skill = tmp.resolve("tools-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: tools-skill\ndescription: test\n---\n\n# Test\n");
		write(skill.resolve("tools"), "ok.py", CLEAN_SCRIPT);
		write(skill.resolve("tools"), "a.mjs", "export {}\n");
		write(skill.resolve("tools"), "b.cjs", "module.exports = {}\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(anyContains(successMessages(result), "passed lint checks"), successMessages(result).toString());
		assertTrue(anyContains(successMessages(result), "2 Node script(s) not linted"));
	}

	@Test
	void scriptsAndToolsDirectoriesAreBothLinted() throws IOException {
		Path skill = skillWithoutScripts("both-dirs");
		write(skill.resolve("scripts"), "a.py", "print(1)\n");
		write(skill.resolve("tools"), "b.py", "print(2)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(2, result.getFindings().stream().filter(f -> f.getCheckName().equals("flat_script")).count());
		assertTrue(result.getFindings()
			.stream()
			.allMatch(f -> f.getMessage().startsWith("a.py") || f.getMessage().startsWith("b.py")));
	}

	/**
	 * Upstream lists scripts with {@code Path.glob}, which — unlike the {@code glob}
	 * module — does match dotfiles, so {@code .hidden.py} is linted there too.
	 */
	@Test
	void hiddenDotFileScriptIsLinted() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), ".hidden.py", "print(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("flat_script"));
		assertTrue(result.getFindings().stream().anyMatch(f -> f.getMessage().startsWith(".hidden.py")));
	}

	@Test
	void nestedSubdirectoryIsNotLinted() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts").resolve("sub"), "a.py", "print(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result), "No Python scripts found"));
	}

	@Test
	void uppercaseExtensionIsNotLinted() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "A.PY", "print(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty());
	}

	/**
	 * {@code Path.glob} yields directories as well as files, so upstream picks up a
	 * directory named {@code weird.py} and its read then raises — reported as read_error
	 * rather than silently ignored.
	 */
	@Test
	void directoryNamedLikeAScriptReportsReadError() throws IOException {
		Path skill = skillWithScripts();
		Files.createDirectories(skill.resolve("scripts").resolve("weird.py"));

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "read_error");
		assertEquals(Severity.MEDIUM, finding.getSeverity());
		assertEquals("SCRIPT_LINT", finding.getCategory());
		assertTrue(finding.getMessage().startsWith("Cannot read file: "), finding.getMessage());
	}

	/**
	 * Upstream's {@code read_text(encoding="utf-8")} raises; the finding is advisory
	 * MEDIUM.
	 */
	@Test
	void undecodableBytesReportReadError() throws IOException {
		Path skill = skillWithScripts();
		Files.createDirectories(skill.resolve("scripts"));
		Files.write(skill.resolve("scripts").resolve("bad.py"), new byte[] { (byte) 0xff, (byte) 0xfe, (byte) 0xfd });

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "read_error");
		assertEquals(Severity.MEDIUM, finding.getSeverity());
		assertTrue(finding.getMessage().startsWith("Cannot read file: "), finding.getMessage());
		assertEquals("scripts/bad.py", relativeTo(skill, finding.getFilePath()));
		// Nothing else runs for a file that could not be read.
		assertEquals(1, result.getFindings().size());
	}

	@Test
	void findingsFollowSortedPathOrder() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "z.py", "#!/usr/bin/env python3\nprint(1)\n");
		write(skill.resolve("scripts"), "a.py", "print(2)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		// a.py sorts first: flat, missing shebang, no input validation; then z.py: flat,
		// no validation.
		assertEquals(
				List.of("flat_script", "missing_shebang", "no_input_validation", "flat_script", "no_input_validation"),
				checkNames(result));
	}

	private static String relativeTo(Path root, String path) {
		String prefix = root + "/";
		return path.startsWith(prefix) ? path.substring(prefix.length()) : path;
	}

	// ------------------------------------------------------------------ folder mode

	@Test
	void folderWithoutSkillsSucceeds() throws IOException {
		Path root = tmp.resolve("folder");
		Files.createDirectories(root.resolve("not-a-skill"));

		ValidationResult result = new ScriptLintValidator().validate(root);

		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result), "No skills found to lint"));
	}

	@Test
	void folderMergesSkillFindingsWithPrefix() throws IOException {
		Path root = tmp.resolve("folder");
		for (String name : List.of("alpha", "beta")) {
			Path skill = root.resolve(name);
			Files.createDirectories(skill);
			Files.writeString(skill.resolve("SKILL.md"), "---\nname: " + name + "\ndescription: test\n---\n");
			write(skill.resolve("scripts"), "f.py", "#!/usr/bin/env python3\nprint(1)\n");
		}

		ValidationResult result = new ScriptLintValidator().validate(root);

		List<String> locations = result.getFindings().stream().map(Finding::getFilePath).toList();
		assertEquals(4, locations.size());
		assertTrue(
				locations.stream()
					.allMatch(location -> location.startsWith("[alpha] ") || location.startsWith("[beta] ")),
				locations.toString());
		// Advisory findings never fail validation, and a skill with findings records no
		// check.
		assertTrue(result.isPassed());
		assertTrue(result.getSuccessDetails().isEmpty());
	}

	@Test
	void folderOfCleanSkillsReportsNoFindings() throws IOException {
		Path root = tmp.resolve("folder");
		Path skill = root.resolve("alpha");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: alpha\ndescription: test\n---\n");
		write(skill.resolve("scripts"), "ok.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    print(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(root);

		assertTrue(result.isPassed());
		assertTrue(result.getFindings().isEmpty());
		assertTrue(anyContains(successMessages(result), "All 1 script(s) passed lint checks"));
	}

	// ------------------------------------------------------------------ flat_script

	/**
	 * The upstream "clean script" fixture: shebang, argparse, a function, no magic
	 * numbers.
	 */
	private static final String CLEAN_SCRIPT = """
			#!/usr/bin/env python3
			import argparse

			def main():
			    parser = argparse.ArgumentParser()
			    parser.add_argument('input')
			    args = parser.parse_args()
			    print(args.input)

			if __name__ == '__main__':
			    main()
			""";

	/** Ports test_clean_script_no_findings. */
	@Test
	void cleanScriptHasNoFindings() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "clean.py", CLEAN_SCRIPT);

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
		assertTrue(anyContains(successMessages(result), "All 1 script(s) passed lint checks"));
	}

	/** Ports test_flat_script_finding, asserting the finding's exact shape. */
	@Test
	void flatScriptFinding() throws IOException {
		Path skill = skillWithScripts();
		Path script = write(skill.resolve("scripts"), "flat.py",
				"#!/usr/bin/env python3\nimport sys\nprint(sys.argv)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "flat_script");
		assertEquals("SCRIPT_LINT", finding.getCategory());
		assertEquals(Severity.MEDIUM, finding.getSeverity());
		assertEquals("flat.py has no function definitions (flat script)", finding.getMessage());
		assertEquals(String.valueOf(script), finding.getFilePath());
		assertEquals("Wrap logic in functions for maintainability and testability", finding.getSuggestion());
		assertNull(finding.getLineNumber());
	}

	/** Ports test_flat_script_finding_in_tools_dir. */
	@Test
	void flatScriptFindingInToolsDir() throws IOException {
		Path skill = tmp.resolve("tools-skill");
		Files.createDirectories(skill);
		Files.writeString(skill.resolve("SKILL.md"), "---\nname: tools-skill\ndescription: test\n---\n\n# Test\n");
		write(skill.resolve("tools"), "flat.py", "#!/usr/bin/env python3\nimport sys\nprint(sys.argv)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("flat_script"));
	}

	@Test
	void emptyScriptIsFlatAndLacksShebangAndValidation() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "empty.py", "");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(List.of("flat_script", "missing_shebang", "no_input_validation"), checkNames(result));
	}

	@Test
	void shebangOnlyScriptStillCountsAsFlat() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "sheb.py", "#!/usr/bin/env python3\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(List.of("flat_script", "no_input_validation"), checkNames(result));
	}

	@Test
	void classMethodCountsAsAFunction() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "cls.py", """
				#!/usr/bin/env python3
				import argparse
				class A:
				    def m(self):
				        return 1
				    async def n(self):
				        return 2
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("flat_script"), checkNames(result).toString());
	}

	@Test
	void asyncDefCountsAsAFunction() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "async.py",
				"#!/usr/bin/env python3\nimport argparse\nasync def main():\n    await go()\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("flat_script"));
	}

	@Test
	void lambdaAloneIsStillAFlatScript() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "lam.py", "#!/usr/bin/env python3\nimport argparse\nf = lambda x: x\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(List.of("flat_script"), checkNames(result));
	}

	/**
	 * A {@code def} in a comment is stripped before the scan, matching the AST, which
	 * never sees it.
	 */
	@Test
	void defMentionedInACommentIsNotAFunction() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "c.py", "#!/usr/bin/env python3\nimport argparse\n# def helper():\nprint(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("flat_script"));
	}

	/**
	 * The same for a docstring: the def inside it is invisible, the real one is counted.
	 */
	@Test
	void defMentionedInADocstringIsNotAFunction() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "d.py", """
				#!/usr/bin/env python3
				import argparse
				'''
				Docstring mentioning def fake(): and 42
				'''
				def main():
				    pass
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
	}

	// ------------------------------------------------------------------ deep_nesting

	/** Ports test_deep_nesting_finding, including the depth reported in the message. */
	@Test
	void deepNestingFinding() throws IOException {
		Path skill = skillWithScripts();
		Path script = write(skill.resolve("scripts"), "nested.py", """
				#!/usr/bin/env python3
				import argparse
				def main():
				    for i in range(10):
				        for j in range(10):
				            for k in range(10):
				                if i > 0:
				                    if j > 0:
				                        if k > 0:
				                            while True:
				                                print(i, j, k)
				                                break
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "deep_nesting");
		assertEquals(Severity.MEDIUM, finding.getSeverity());
		assertEquals("nested.py has deeply nested code (depth 7, max 6)", finding.getMessage());
		assertEquals(String.valueOf(script), finding.getFilePath());
		assertEquals("Refactor into smaller functions to reduce complexity", finding.getSuggestion());
	}

	@Test
	void nestingAtTheLimitIsNotFlagged() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "ok.py", """
				#!/usr/bin/env python3
				import argparse
				def main():
				    if a:
				        for i in x:
				            while b:
				                with c:
				                    for j in y:
				                        if d:
				                            print(j)
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("deep_nesting"), checkNames(result).toString());
	}

	/**
	 * The counted depths CPython's {@code ast} reports for these snippets, asserted
	 * against the indentation-stack model. Each row was measured with
	 * {@code _calculate_nesting_depth} over the same source text.
	 */
	@ParameterizedTest(name = "{0}")
	@CsvSource(delimiter = '|', quoteCharacter = '\0',
			value = { "chain-six|6", "chain-seven|7", "chain-seven-one-liner|6", "chain-seven-simple-suite|6",
					"try-except-finally|1", "try-except-else|1", "if-elif-elif-else|3", "if-else|1", "for-else|1",
					"while-else|1", "match-case-nested|5", "comprehension|6", "async-for-with|7", "bracket-chain|6",
					"nested-if-deep-with-else|8", "one-line-if-shallow|1", "dict-colon|0", "lambda-dict|0",
					"class-methods|2", "with-two-items|1", "backslash-if|1", })
	void nestingDepthMatchesUpstream(String snippet, int expected) {
		assertEquals(expected, nestingDepth(SNIPPETS.get(snippet)));
	}

	/**
	 * Snippets whose nesting depth is asserted above; keys use dashes, not underscores.
	 */
	private static final Map<String, String> SNIPPETS = Map.ofEntries(Map.entry("chain-six", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    for j in y:
			                        if d:
			                            print(j)
			"""), Map.entry("chain-seven", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    for j in y:
			                        if d:
			                            if e:
			                                print(j)
			"""), Map.entry("chain-seven-one-liner", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    for j in y:
			                        if d: return
			"""), Map.entry("chain-seven-simple-suite", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    for j in y:
			                        if d: k = 1; print(k)
			"""), Map.entry("try-except-finally", """
			def main():
			    try:
			        pass
			    except ValueError:
			        pass
			    finally:
			        pass
			"""), Map.entry("try-except-else", """
			def main():
			    try:
			        pass
			    except ValueError:
			        pass
			    else:
			        pass
			"""), Map.entry("if-elif-elif-else", """
			def main():
			    if a:
			        pass
			    elif b:
			        pass
			    elif c:
			        pass
			    else:
			        pass
			"""), Map.entry("if-else", """
			def main():
			    if a:
			        pass
			    else:
			        pass
			"""), Map.entry("for-else", """
			def main():
			    for i in x:
			        pass
			    else:
			        pass
			"""), Map.entry("while-else", """
			def main():
			    while b:
			        break
			    else:
			        pass
			"""), Map.entry("match-case-nested", """
			def main():
			    match p:
			        case [a, b]:
			            if a:
			                for i in x:
			                    while b:
			                        with c:
			                            try:
			                                pass
			                            except ValueError:
			                                raise
			"""), Map.entry("comprehension", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    if d:
			                        for j in y:
			                            total = [k for k in z if k > 2]
			"""), Map.entry("async-for-with", """
			async def main():
			    if a:
			        async for i in x:
			            async with c:
			                while b:
			                    with d:
			                        for j in y:
			                            if e:
			                                pass
			"""), Map.entry("bracket-chain", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    for j in y:
			                        if d:
			                            print(
			                                j,
			                            )
			"""), Map.entry("nested-if-deep-with-else", """
			def main():
			    if a:
			        for i in x:
			            while b:
			                with c:
			                    try:
			                        for j in y:
			                            if d: pass
			                            elif e: pass
			                            else: pass
			                    except ValueError:
			                        raise
			"""), Map.entry("one-line-if-shallow", "def main():\n    if a: pass\n"), Map.entry("dict-colon", """
			def main():
			    d = {
			        'k':
			            42,
			    }
			"""), Map.entry("lambda-dict", """
			H = {
			    'a': lambda x: x,
			}
			"""), Map.entry("class-methods", """
			class A:
			    def m(self):
			        if a:
			            for i in x:
			                pass
			"""), Map.entry("with-two-items", """
			def main():
			    with open(a) as f, open(b) as g:
			        print(f, g)
			"""), Map.entry("backslash-if", """
			def main():
			    if a or \\
			        b:
			        pass
			"""));

	/**
	 * A compound statement whose suite sits on the same line is still an {@code If} node
	 * upstream, so it costs a nesting level; the keyword test counts it too.
	 */
	@Test
	void oneLineCompoundStatementCountsAsNesting() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "dbg.py", """
				#!/usr/bin/env python3
				import argparse
				def main():
				    if a:
				        for i in x:
				            while b:
				                with c:
				                    try:
				                        for j in y:
				                            if d: return
				    except ValueError:
				        raise
				    """);

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "deep_nesting");
		assertEquals("dbg.py has deeply nested code (depth 7, max 6)", finding.getMessage());
	}

	/**
	 * <strong>Divergence from upstream.</strong> {@code try}/{@code except*} builds a
	 * {@code TryStar} node, which is absent from {@code _NESTING_NODES}, so the AST walk
	 * counts nothing for that block; the keyword model reads {@code try} as one level. A
	 * {@code try}/{@code except} pair does agree (both count one).
	 */
	@Test
	void tryStarCountsOneLevelWhereTheAstCountsNone() {
		String source = """
				def main():
				    if a:
				        for i in x:
				            while b:
				                with c:
				                    if d:
				                        for j in y:
				                            try:
				                                pass
				                            except* ValueError:
				                                raise
				""";
		// CPython's _calculate_nesting_depth reports 6 here; the stack model reports 7.
		assertEquals(7, nestingDepth(source));
	}

	/**
	 * <strong>Divergence from upstream.</strong> A {@code try} with no handler is a
	 * {@code SyntaxError} for {@code ast.parse}, so upstream returns from the file
	 * without any finding. The tokenizer-level model has no grammar to notice, so the
	 * file is linted and the chain is reported.
	 */
	@Test
	void grammarInvalidFileIsLintedRatherThanSkipped() {
		String source = """
				def main():
				    if a:
				        for i in x:
				            while b:
				                with c:
				                    try:
				                        for j in y:
				                            print(j)
				""";
		assertFalse(PythonSyntax.model(source).syntaxError);
		assertEquals(6, nestingDepth(source));
	}

	// ------------------------------------------------------------------ magic_numbers

	/** Ports test_magic_numbers_finding. */
	@Test
	void magicNumbersFinding() throws IOException {
		Path skill = skillWithScripts();
		Path script = write(skill.resolve("scripts"), "magic.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    x = 42\n    y = 3.14159\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "magic_numbers");
		assertEquals(Severity.LOW, finding.getSeverity());
		assertEquals("magic.py contains magic numbers", finding.getMessage());
		assertEquals(String.valueOf(script), finding.getFilePath());
		assertEquals("Extract magic numbers to named constants", finding.getSuggestion());
	}

	/**
	 * {@code SCRIPT_LINT_SAFE_CONSTANTS} is {@code {0, 1, -1, 2, -1.0}}; membership is
	 * numeric equality, so {@code 1.0}/{@code 2.0} are safe too, and a unary minus is a
	 * {@code UnaryOp} that never reaches the test.
	 */
	@Test
	void safeConstantsAreNotMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "safe.py", """
				#!/usr/bin/env python3
				import argparse
				def main():
				    a = 0
				    b = 1
				    c = 2
				    d = -1
				    e = 1.0
				    f = 2.0
				    g = 0.0
				    h = -2
				    print(a, b, c, d, e, f, g, h)
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"), checkNames(result).toString());
	}

	@Test
	void numbersInCommentsAndStringsAreInvisible() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "nums.py", """
				#!/usr/bin/env python3
				import argparse
				# offset by 42 degrees
				def main():
				    s = 'value 42 and 3.14'
				    print(s)
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"), checkNames(result).toString());
	}

	@Test
	void versionStringAndBooleansAreNotMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "ver.py", """
				#!/usr/bin/env python3
				import argparse
				VERSION = '1.2.3'
				def main():
				    print(VERSION, True, False)
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
	}

	@Test
	void digitsInsideIdentifiersAreNotNumbers() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "idents.py", """
				#!/usr/bin/env python3
				import argparse
				def main_2():
				    x1 = v2 + a3
				    print(x1)
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "0xff", "0b101", "0o17", "1e-9", "1_000_000", ".5", "2.5", "1_0", "0x1F" })
	void numericLiteralSpellingsAreMagic(String literal) throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "lit.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    x = " + literal + "\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("magic_numbers"), literal + " -> " + checkNames(result));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "0x0", "0b1", "0o2", "0.0", "-1.0" })
	void numericLiteralsEqualToSafeConstantsAreNotMagic(String literal) throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "lit.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    x = " + literal + "\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"), literal + " -> " + checkNames(result));
	}

	/** A subscript bound is a real constant upstream; so is a keyword default. */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "def main():\n    print(x[3])\n", "def main(limit=7):\n    print(limit)\n", })
	void numbersInArgumentPositionsAreMagic(String body) throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "arg.py", "#!/usr/bin/env python3\nimport argparse\n" + body);

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("magic_numbers"));
	}

	/**
	 * Slice bounds {@code 1} and {@code 2} are both in the safe set, so nothing is magic.
	 */
	@Test
	void sliceBoundsInSafeConstantsAreNotMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "sl.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    print(x[1:2])\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"));
	}

	/** A number written across a wrapped call is still a constant upstream. */
	@Test
	void numberOnAContinuationLineIsMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "wrap.py", """
				#!/usr/bin/env python3
				import argparse
				def main():
				    total = sum([
				        1,
				        2,
				        7,
				    ])
				""");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("magic_numbers"));
	}

	/** The same through an explicit backslash join. */
	@Test
	void numberAfterABackslashJoinIsMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "bs.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    x = 1 + \\\n        42\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("magic_numbers"));
	}

	/**
	 * A complex literal is not an {@code int} or {@code float} constant upstream, so it
	 * is not magic; the literal pattern refuses to match the digits of {@code 3j} for the
	 * same reason.
	 */
	@Test
	void complexLiteralIsNotMagic() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "cx.py", "#!/usr/bin/env python3\nimport argparse\ndef main():\n    z = 3j\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"));
	}

	/**
	 * <strong>Divergence from upstream.</strong> A number interpolated into an f-string
	 * is a {@code Constant} inside the {@code JoinedStr}, so upstream flags it. Here the
	 * whole string body is stripped before the scan, so it is invisible — the same
	 * stripping that correctly hides {@code 'value 42'} in a plain string.
	 */
	@Test
	void numberInsideAnFStringIsMissed() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "fstr.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    print(f'total {x + 42}')\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("magic_numbers"), checkNames(result).toString());
	}

	// ---------------------------------------------------------- shebang and validation

	/** Ports test_missing_shebang_finding. */
	@Test
	void missingShebangFinding() throws IOException {
		Path skill = skillWithScripts();
		Path script = write(skill.resolve("scripts"), "noshebang.py", "import argparse\ndef main():\n    pass\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "missing_shebang");
		assertEquals(Severity.LOW, finding.getSeverity());
		assertEquals("noshebang.py missing shebang line", finding.getMessage());
		assertEquals(String.valueOf(script), finding.getFilePath());
		assertEquals("Add: #!/usr/bin/env python3", finding.getSuggestion());
	}

	@Test
	void shebangMustBeTheVeryFirstCharacters() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "blank.py",
				"\n#!/usr/bin/env python3\nimport argparse\ndef main():\n    pass\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(checkNames(result).contains("missing_shebang"));
	}

	/**
	 * A UTF-8 byte-order mark is not stripped by {@code read_text}, and CPython's
	 * tokenizer rejects {@code U+FEFF} inside the string handed to {@code ast.parse}, so
	 * upstream skips the file silently rather than reporting a missing shebang.
	 */
	@Test
	void byteOrderMarkMakesTheFileSkippedNotFlagged() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "bom.py",
				"\uFEFF#!/usr/bin/env python3\nimport argparse\ndef main():\n    pass\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
		assertTrue(anyContains(successMessages(result), "All 1 script(s) passed lint checks"));
	}

	/** Ports test_no_input_validation_finding. */
	@Test
	void noInputValidationFinding() throws IOException {
		Path skill = skillWithScripts();
		Path script = write(skill.resolve("scripts"), "noval.py",
				"#!/usr/bin/env python3\ndef main():\n    print('hi')\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "no_input_validation");
		assertEquals(Severity.LOW, finding.getSeverity());
		assertEquals("noval.py may lack input validation", finding.getMessage());
		assertEquals(String.valueOf(script), finding.getFilePath());
		assertEquals("Add argument checks and raise descriptive errors", finding.getSuggestion());
	}

	/** Each substring upstream probes for, in a script that is otherwise clean. */
	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "if len(sys.argv) < 2:\n        sys.exit(1)", "argparse", "click", "typer", "ValueError",
			"raise", })
	void inputValidationProbeSatisfiesTheCheck(String evidence) throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "v.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n    " + evidence + "\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("no_input_validation"), evidence + " -> " + checkNames(result));
	}

	/**
	 * The probe is a plain substring test over the raw file, so the word inside a string
	 * literal counts too — upstream does the same.
	 */
	@Test
	void validationWordInsideAStringStillCounts() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "s.py", "#!/usr/bin/env python3\ndef main():\n    print('raise ValueError')\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("no_input_validation"));
	}

	// ------------------------------------------------------------------ syntax errors

	/** Ports test_syntax_error_skipped: an unparseable file yields no finding at all. */
	@Test
	void syntaxErrorIsSkippedSilently() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "broken.py", "def broken(:\n    pass\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertFalse(checkNames(result).contains("read_error"));
		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
		assertTrue(anyContains(successMessages(result), "All 1 script(s) passed lint checks"));
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "import argparse\ndef main():\n    s = 'abc\n    print(s)\n",
			"import argparse\ndef main():\n    s = '''abc\n    print(s)\n",
			"import argparse\ndef main():\n    print(x))\n", })
	void tokenizerLevelSyntaxErrorsAreSkipped(String source) throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "broken.py", source);

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertTrue(result.getFindings().isEmpty(), checkNames(result).toString());
	}

	/** One bad file does not stop the others in the same directory. */
	@Test
	void brokenScriptDoesNotStopItsNeighbours() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "a_broken.py", "def broken(:\n    pass\n");
		write(skill.resolve("scripts"), "b_flat.py", "print(1)\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(List.of("flat_script", "missing_shebang", "no_input_validation"), checkNames(result));
	}

	// ------------------------------------------------------------------ line endings

	@Test
	void crlfScriptIsLintedLikeAnyOther() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "crlf.py",
				"#!/usr/bin/env python3\r\nimport argparse\r\n\r\ndef main():\r\n    x = 42\r\n    print(x)\r\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		assertEquals(List.of("magic_numbers"), checkNames(result));
	}

	@Test
	void tabIndentedChainIsFlagged() throws IOException {
		Path skill = skillWithScripts();
		write(skill.resolve("scripts"), "tabs.py",
				"#!/usr/bin/env python3\nimport argparse\ndef main():\n"
						+ "\tif a:\n\t\tfor i in x:\n\t\t\twhile b:\n\t\t\t\twith c:\n\t\t\t\t\ttry:\n"
						+ "\t\t\t\t\t\tfor j in y:\n\t\t\t\t\t\t\tif d:\n\t\t\t\t\t\t\t\tprint(j)\n"
						+ "\t\t\t\t\texcept ValueError:\n\t\t\t\t\t\traise\n");

		ValidationResult result = new ScriptLintValidator().validate(skill);

		Finding finding = only(result, "deep_nesting");
		assertEquals("tabs.py has deeply nested code (depth 7, max 6)", finding.getMessage());
	}

	@Test
	void lintScriptIsUsableDirectlyOnOneFile() throws IOException {
		ValidationResult result = new ValidationResult();
		Path script = write(tmp.resolve("loose"), "one.py", "print(1)\n");

		new ScriptLintValidator().lintScript(script, result);

		assertNotNull(result.getFindings());
		assertEquals(List.of("flat_script", "missing_shebang", "no_input_validation"), checkNames(result));
		assertTrue(result.getFindings().stream().allMatch(f -> String.valueOf(script).equals(f.getFilePath())));
	}

}

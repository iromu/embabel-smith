/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.iromu.embabel.smith.skillevaluator.model.Finding;
import org.iromu.embabel.smith.skillevaluator.model.Severity;
import org.iromu.embabel.smith.skillevaluator.model.ValidationResult;
import org.iromu.embabel.smith.skillevaluator.util.Constants;

/**
 * Advisory lint for a skill's {@code scripts/} and {@code tools/} directories, ported
 * from {@code validators/script_lint.py}.
 * <p>
 * Every finding is advisory: MEDIUM/LOW severities are reported as warnings and never
 * fail validation, keeping lint separate from the quality scores.
 * <h2>AST rules re-implemented as line/regex heuristics</h2> Upstream parses each script
 * with {@code ast} and walks the tree. This port may neither embed a Python parser nor
 * shell out to one, so the three tree-derived rules run over {@link PythonSyntax} instead
 * — the same rules, decided from indentation and token text:
 * <ul>
 * <li>{@code flat_script} — upstream counts {@code FunctionDef}/{@code AsyncFunctionDef}
 * nodes. Here a {@code def} is a code line (comments and strings stripped) whose first
 * word is {@code def}, or {@code async} followed by {@code def}. A {@code def} written in
 * a comment or string is therefore not counted, but one produced by {@code exec()} or a
 * decorator-generated function is.</li>
 * <li>{@code deep_nesting} — upstream counts the deepest chain of
 * {@code If/For/While/Try/With/AsyncFor/AsyncWith} nodes. Here each such statement is a
 * line starting with its keyword, and depth is the count of enclosing ones on an
 * indentation stack. {@code else}/{@code except}/ {@code finally} reuse their sibling's
 * depth and {@code elif} adds one, matching the {@code Try}-is-one-node and
 * nested-{@code If}-in-{@code orelse} shapes of the AST. A statement whose suite sits on
 * the same line ({@code if d: return}) counts too, since valid Python cannot put a second
 * compound statement there. Consequences: a {@code match}/{@code case} block is not
 * counted (upstream does not count it either); a {@code try} paired with {@code except*}
 * counts as one level even though upstream builds a {@code TryStar}, which is absent from
 * its counted node set; and a file whose indentation does not reflect its block nesting
 * (statements joined with semicolons) can read shallower or deeper than the tree.</li>
 * <li>{@code magic_numbers} — upstream flags any {@code ast.Constant} int or float
 * outside {@code SCRIPT_LINT_SAFE_CONSTANTS}. Here numeric literals are matched on
 * stripped code lines and compared by value, so the safe set is the same
 * {@code 0}/{@code 1}/{@code 2} (Python's membership test is numeric equality, which is
 * why {@code 1.0}, {@code 2.0} and the negative spellings in that frozenset are already
 * covered), and a unary minus is ignored the way {@code UnaryOp} makes it ignored
 * upstream. Numbers in comments and plain strings are correctly invisible; numbers
 * interpolated inside an f-string are missed, because the string body is stripped.
 * Complex literals such as {@code 3j} are missed.</li>
 * </ul>
 * <p>
 * Upstream also skips a file silently when {@code ast.parse} raises {@code SyntaxError}.
 * The comparable verdict here comes from the tokenizer-level checks in
 * {@link PythonSyntax} (byte-order mark, unterminated string, unterminated triple quote,
 * unbalanced brackets), so code that tokenizes cleanly but is grammatically invalid — a
 * {@code try} with no {@code except}, a missing colon — is linted rather than skipped,
 * and can produce findings upstream never reaches.
 * <p>
 * The remaining checks — shebang, input validation, unreadable file, Node scripts not
 * linted — are text checks upstream and port 1:1.
 *
 * @author Ivan Rodriguez
 */
public class ScriptLintValidator extends ValidatorBase {

	/**
	 * Numeric literals that are not magic, compared by value as Python's {@code in} does.
	 */
	private static final Set<Double> SAFE_CONSTANTS = Set.of(0.0, 1.0, -1.0, 2.0);

	/** Substrings upstream accepts as evidence of input validation. */
	private static final List<String> INPUT_VALIDATION_PATTERNS = List.of("if len(sys.argv)", "argparse", "click",
			"typer", "ValueError", "raise");

	/**
	 * Words that open one level of control-flow nesting, mirroring
	 * {@code _NESTING_NODES}.
	 */
	private static final Set<String> NESTING_KEYWORDS = Set.of("if", "for", "while", "try", "with");

	/** Words that continue a sibling block at the same depth as their opener. */
	private static final Set<String> SIBLING_KEYWORDS = Set.of("else", "except", "finally");

	private static final Pattern NUMBER_LITERAL = Pattern
		.compile("(?<![\\w.])(?:0[xX][0-9a-fA-F_]+|0[bB][01_]+|0[oO][0-7_]+"
				+ "|(?:\\d[\\d_]*\\.?[\\d_]*(?:[eE][+-]?\\d+)?|\\.\\d[\\d_]*)(?![\\w.]))");

	@Override
	public String getName() {
		return "Script Lint (Advisory)";
	}

	@Override
	public String getDescription() {
		return "AST-based code quality checks for skill scripts";
	}

	@Override
	public ValidationResult validate(Path skillPath) {
		if (isSkillDirectory(skillPath)) {
			return lintSkill(skillPath);
		}
		return lintFolder(skillPath);
	}

	ValidationResult lintFolder(Path root) {
		List<Path> skillDirs = findAllSkills(root);
		ValidationResult result = newResult();
		if (skillDirs.isEmpty()) {
			result.addSuccess("lint", "No skills found to lint");
			return result;
		}
		for (Path skillDir : skillDirs) {
			result.mergeWithPrefix(lintSkill(skillDir), skillDir.getFileName().toString());
		}
		return result;
	}

	ValidationResult lintSkill(Path skillPath) {
		ValidationResult result = newResult();
		List<Path> pyFiles = new ArrayList<>();
		List<Path> nodeFiles = new ArrayList<>();
		for (String dirname : Constants.EXECUTABLE_SKILL_DIRS) {
			Path directory = skillPath.resolve(dirname);
			if (Files.isDirectory(directory)) {
				pyFiles.addAll(glob(directory, Constants.PYTHON_SCRIPT_PATTERNS));
				nodeFiles.addAll(glob(directory, Constants.NODE_SCRIPT_PATTERNS));
			}
		}
		String notLinted = nodeFiles.size() + " Node script(s) not linted (script lint parses Python only)";
		String suffix = nodeFiles.isEmpty() ? "" : "; " + notLinted;

		if (pyFiles.isEmpty()) {
			boolean anyExecutableDir = Constants.EXECUTABLE_SKILL_DIRS.stream()
				.anyMatch((dirname) -> Files.isDirectory(skillPath.resolve(dirname)));
			if (!anyExecutableDir) {
				result.addSuccess("lint", "No scripts/ or tools/ directory found");
			}
			else {
				result.addSuccess("lint", "No Python scripts found in scripts/ or tools/" + suffix);
			}
			return result;
		}

		for (Path script : pyFiles) {
			lintScript(script, result);
		}

		if (result.getFindings().isEmpty()) {
			result.addSuccess("lint", "All " + pyFiles.size() + " script(s) passed lint checks" + suffix);
		}
		else if (!nodeFiles.isEmpty()) {
			// The findings carry the verdict. Keep the Node note as an informational
			// finding: unlike a message it survives folder aggregation and the JSON
			// report, and it does not count as another completed check.
			result.addFinding(new Finding("SCRIPT_LINT", Severity.INFO, "node_scripts_not_linted", notLinted,
					nodeFiles.get(0).getParent().getFileName().toString()));
		}
		return result;
	}

	/**
	 * Run every lint check on one Python script.
	 * @param script the script file to lint
	 * @param result the result findings are appended to
	 */
	void lintScript(Path script, ValidationResult result) {
		String content;
		try {
			// readString, not Frontmatter.readUtf8: upstream reads utf-8 without
			// stripping a BOM, so the byte-order mark stays in the text it parses.
			content = Files.readString(script);
		}
		catch (IOException | RuntimeException ex) {
			result.addFinding(new Finding("SCRIPT_LINT", Severity.MEDIUM, "read_error",
					"Cannot read file: " + ((ex.getMessage() == null) ? ex.toString() : ex.getMessage()),
					String.valueOf(script)));
			return;
		}

		PythonSyntax.Model model = PythonSyntax.model(content);
		if (model.syntaxError) {
			// Upstream returns silently when ast.parse raises SyntaxError.
			return;
		}

		String fname = script.getFileName().toString();

		if (countFunctions(model) == 0) {
			result.addFinding(new Finding("SCRIPT_LINT", Severity.MEDIUM, "flat_script",
					fname + " has no function definitions (flat script)", String.valueOf(script), null, null,
					"Wrap logic in functions for maintainability and testability", null));
		}

		int depth = nestingDepth(model);
		if (depth > Constants.SCRIPT_LINT_MAX_NESTING) {
			result.addFinding(new Finding("SCRIPT_LINT", Severity.MEDIUM, "deep_nesting",
					fname + " has deeply nested code (depth " + depth + ", max " + Constants.SCRIPT_LINT_MAX_NESTING
							+ ")",
					String.valueOf(script), null, null, "Refactor into smaller functions to reduce complexity", null));
		}

		if (hasMagicNumbers(model)) {
			result
				.addFinding(new Finding("SCRIPT_LINT", Severity.LOW, "magic_numbers", fname + " contains magic numbers",
						String.valueOf(script), null, null, "Extract magic numbers to named constants", null));
		}

		if (!content.startsWith("#!")) {
			result
				.addFinding(new Finding("SCRIPT_LINT", Severity.LOW, "missing_shebang", fname + " missing shebang line",
						String.valueOf(script), null, null, "Add: #!/usr/bin/env python3", null));
		}

		if (!hasInputValidation(content)) {
			result.addFinding(new Finding("SCRIPT_LINT", Severity.LOW, "no_input_validation",
					fname + " may lack input validation", String.valueOf(script), null, null,
					"Add argument checks and raise descriptive errors", null));
		}
	}

	/**
	 * Port of {@code _count_functions}: {@code def} / {@code async def} statements.
	 * @param model the parsed script
	 * @return how many function definitions the script declares
	 */
	static int countFunctions(PythonSyntax.Model model) {
		int count = 0;
		for (PythonSyntax.CodeLine line : model.lines) {
			String first = PythonSyntax.firstWord(line.code);
			if ("def".equals(first) || ("async".equals(first) && "def".equals(PythonSyntax.secondWord(line.code)))) {
				count++;
			}
		}
		return count;
	}

	/**
	 * Port of {@code _calculate_nesting_depth}: the deepest chain of control-flow
	 * statements, derived from the indentation stack instead of the AST.
	 * @param model the parsed script
	 * @return the deepest nesting level reached by any control-flow statement
	 */
	static int nestingDepth(PythonSyntax.Model model) {
		int maxDepth = 0;
		// Enclosing blocks: their indent column and the control depth inside them.
		List<Frame> stack = new ArrayList<>();
		for (PythonSyntax.CodeLine line : model.lines) {
			if (line.continuation) {
				continue;
			}
			Frame poppedSame = null;
			while (!stack.isEmpty() && stack.get(stack.size() - 1).indent >= line.indent) {
				Frame popped = stack.remove(stack.size() - 1);
				if (popped.indent == line.indent && popped.control) {
					poppedSame = popped;
				}
			}
			int enclosing = stack.isEmpty() ? 0 : stack.get(stack.size() - 1).depth;
			String first = PythonSyntax.firstWord(line.code);
			boolean control = isControlOpener(line, first);
			int depth;
			if (!control) {
				depth = enclosing;
			}
			else if (poppedSame != null && isSibling(first)) {
				// else/except/finally continue their sibling block; elif nests an
				// If in the orelse of the previous one, which costs a level upstream.
				depth = poppedSame.depth + ("elif".equals(first) ? 1 : 0);
			}
			else {
				depth = enclosing + 1;
			}
			if (control) {
				maxDepth = Math.max(maxDepth, depth);
			}
			stack.add(new Frame(line.indent, depth, control));
		}
		return maxDepth;
	}

	/**
	 * Whether this line opens one of the counted control-flow blocks.
	 * @param line the code line to classify
	 * @param first its first word
	 * @return {@code true} when the line adds a nesting level
	 */
	private static boolean isControlOpener(PythonSyntax.CodeLine line, String first) {
		if (NESTING_KEYWORDS.contains(first)) {
			return true;
		}
		if ("async".equals(first)) {
			String second = PythonSyntax.secondWord(line.code);
			return "for".equals(second) || "with".equals(second);
		}
		return isSibling(first);
	}

	/**
	 * Whether the keyword continues a sibling block rather than opening its own.
	 * @param first the line's first word
	 * @return {@code true} for {@code else}, {@code except}, {@code finally} or
	 * {@code elif}
	 */
	private static boolean isSibling(String first) {
		return SIBLING_KEYWORDS.contains(first) || "elif".equals(first);
	}

	/**
	 * Port of {@code _has_magic_numbers}.
	 * @param model the parsed script
	 * @return {@code true} when a literal outside {@link #SAFE_CONSTANTS} appears
	 */
	static boolean hasMagicNumbers(PythonSyntax.Model model) {
		for (PythonSyntax.CodeLine line : model.lines) {
			Matcher matcher = NUMBER_LITERAL.matcher(line.code);
			while (matcher.find()) {
				Double value = parseNumber(matcher.group());
				// Upstream reads the Constant, so a unary minus never reaches the
				// membership test: -1 is Constant(1) there, and safe constants match
				// by numeric equality (1.0 == 1, 2.0 == 2).
				if (value != null && !SAFE_CONSTANTS.contains(Math.abs(value))) {
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Parse an int/float literal the way Python's numeric constants compare.
	 * @param literal the matched numeric token, including any radix prefix or underscores
	 * @return the value as a {@code double}, or {@code null} when it does not parse
	 */
	private static Double parseNumber(String literal) {
		String text = literal.replace("_", "");
		try {
			String lower = text.toLowerCase(java.util.Locale.ROOT);
			if (lower.startsWith("0x")) {
				return (double) Long.parseLong(lower.substring(2), 16);
			}
			if (lower.startsWith("0b")) {
				return (double) Long.parseLong(lower.substring(2), 2);
			}
			if (lower.startsWith("0o")) {
				return (double) Long.parseLong(lower.substring(2), 8);
			}
			return Double.valueOf(text);
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	/**
	 * Port of {@code _has_input_validation}: substring probes on the raw content.
	 * @param content the unprocessed script text
	 * @return {@code true} when any input-validation idiom appears in the text
	 */
	static boolean hasInputValidation(String content) {
		return INPUT_VALIDATION_PATTERNS.stream().anyMatch(content::contains);
	}

	private ValidationResult newResult() {
		ValidationResult result = new ValidationResult();
		result.setValidatorName("SCRIPT_LINT");
		result.setValidatorDescription(getDescription());
		return result;
	}

	/**
	 * Files in {@code directory} whose name matches one of the {@code *.ext} patterns.
	 * @param directory the directory to list
	 * @param patterns the flat {@code *.ext} patterns to match
	 * @return the matching entries sorted by path, or empty when the listing fails
	 */
	private static List<Path> glob(Path directory, List<String> patterns) {
		List<Path> out = new ArrayList<>();
		try (Stream<Path> children = Files.list(directory)) {
			for (Path child : children.toList()) {
				String name = child.getFileName().toString();
				// Upstream uses Path.glob, which — unlike the glob module — does match
				// dotfiles, and which yields directories too; reading one then raises and
				// is reported as read_error. Neither entry is filtered out here to keep
				// that.
				if (patterns.stream().anyMatch((pattern) -> matchesGlob(name, pattern))) {
					out.add(child);
				}
			}
		}
		catch (IOException ignored) {
			return List.of();
		}
		out.sort(Comparator.comparing(Path::toString));
		return out;
	}

	/**
	 * Supports the flat {@code *.ext} patterns in {@link Constants}; no recursive
	 * {@code **}.
	 * @param name the file name to test
	 * @param pattern the glob pattern, matched like Python's {@code Path.glob}
	 * @return {@code true} when {@code name} is matched by {@code pattern}
	 */
	private static boolean matchesGlob(String name, String pattern) {
		if (!pattern.startsWith("*.")) {
			return name.equals(pattern);
		}
		return name.length() > pattern.length() - 1 && name.endsWith(pattern.substring(1));
	}

	/** One entry of the indentation stack used by {@link #nestingDepth}. */
	private record Frame(int indent, int depth, boolean control) {
	}

}

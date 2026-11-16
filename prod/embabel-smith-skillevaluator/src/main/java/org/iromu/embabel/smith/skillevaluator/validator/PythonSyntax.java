/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Line/regex model of a Python source file — the deterministic stand-in for the
 * {@code ast} tree that upstream {@code validators/script_lint.py} walks.
 *
 * <p>
 * The Java port may not embed a Python parser and may not shell out, so the three
 * AST-derived lint rules ({@code flat_script}, {@code deep_nesting},
 * {@code magic_numbers}) are re-implemented over the token-light representation built
 * here: per-line code text with comments and string literals removed, plus the
 * indentation and bracket state needed to reason about blocks.
 *
 * <p>
 * It also carries the syntax-error verdict that lets {@code _lint_script} skip
 * unparseable files silently. That verdict is a heuristic, not a grammar: a file is
 * reported as a syntax error when it starts with a UTF-8 byte-order mark (which
 * {@code ast.parse} rejects as {@code invalid non-printable character U+FEFF}), a string
 * literal is left unterminated, a triple-quoted block never closes, or brackets are
 * unbalanced at EOF. Code that tokenizes cleanly but is grammatically invalid (a
 * misindented block, a {@code try} without an {@code except}) is linted rather than
 * skipped.
 *
 * <p>
 * Only used by {@link ScriptLintValidator}; not a general Python front end.
 */
final class PythonSyntax {

	/** One physical line after comments and string literals were removed. */
	static final class CodeLine {

		/** Indentation in expanded-tab columns. */
		final int indent;

		/** Line text with comments and strings stripped. */
		final String code;

		/** One-based line number in the original file. */
		final int lineNumber;

		/**
		 * True when the line continues a logical line (open bracket, backslash or
		 * string).
		 */
		final boolean continuation;

		CodeLine(int indent, String code, int lineNumber, boolean continuation) {
			this.indent = indent;
			this.code = code;
			this.lineNumber = lineNumber;
			this.continuation = continuation;
		}

	}

	/** Result of modelling one file. */
	static final class Model {

		/**
		 * Every non-blank code line, in order. Block analysis filters out the entries
		 * flagged as continuations; the text scans keep them so a value written inside a
		 * wrapped call is still seen.
		 */
		final List<CodeLine> lines;

		/** Whether the file looks unparseable and should be skipped by the linter. */
		final boolean syntaxError;

		/** Why {@link #syntaxError} was set, for logs and tests. */
		final String syntaxErrorReason;

		private Model(List<CodeLine> lines, boolean syntaxError, String syntaxErrorReason) {
			this.lines = lines;
			this.syntaxError = syntaxError;
			this.syntaxErrorReason = syntaxErrorReason;
		}

	}

	private static final Pattern FIRST_WORD = Pattern.compile("^([A-Za-z_][A-Za-z0-9_]*)");

	/** Leading word of a code line, or empty when the line starts with punctuation. */
	static String firstWord(String code) {
		var matcher = FIRST_WORD.matcher(code);
		return matcher.find() ? matcher.group(1) : "";
	}

	/** Word after the first — distinguishes {@code async def} from {@code async for}. */
	static String secondWord(String code) {
		String rest = code.substring(firstWord(code).length()).stripLeading();
		var matcher = FIRST_WORD.matcher(rest);
		return matcher.find() ? matcher.group(1) : "";
	}

	private PythonSyntax() {
	}

	/**
	 * Model a Python source file.
	 * @param content raw file text, newlines intact
	 */
	static Model model(String content) {
		List<CodeLine> lines = new ArrayList<>();
		String openString = null;
		String openStringReason = null;
		int bracketDepth = 0;
		boolean pendingContinuation = false;
		// Upstream hands ast.parse an already-decoded string, and CPython's tokenizer
		// rejects U+FEFF there ("invalid non-printable character U+FEFF"), so a UTF-8
		// byte-order mark makes ast.parse raise and the file is skipped silently.
		// read_text does not strip it — only the utf-8-sig codec would.
		String syntaxError = content.startsWith("\uFEFF") ? "byte-order mark at start of file" : null;

		String[] raw = splitPhysical(content);
		for (int index = 0; index < raw.length; index++) {
			String physical = raw[index];
			int lineNumber = index + 1;
			boolean continuation = pendingContinuation;

			if (openString != null) {
				int close = findClose(physical, openString);
				if (close < 0) {
					// Pure string body: contributes neither code nor indentation.
					continue;
				}
				physical = " ".repeat(close + openString.length());
				openString = null;
				continuation = true;
			}

			Scan scan = scanLine(physical, bracketDepth);
			bracketDepth = scan.bracketDepth;
			pendingContinuation = scan.lineContinuation || scan.bracketDepth > 0;

			if (scan.openString != null) {
				openString = scan.openString;
				if (openString.length() == 1 && !scan.lineContinuation && syntaxError == null) {
					syntaxError = "unterminated string literal on line " + lineNumber;
				}
				if (openString.length() == 3 && openStringReason == null) {
					openStringReason = "unterminated triple-quoted string starting on line " + lineNumber;
				}
				pendingContinuation = true;
			}

			if (scan.bracketDepth < 0 && syntaxError == null) {
				syntaxError = "unbalanced closing bracket on line " + lineNumber;
			}

			String code = scan.code.strip();
			if (!code.isEmpty()) {
				lines.add(new CodeLine(indentOf(physical), code, lineNumber, continuation));
			}
		}

		if (syntaxError == null && openString != null) {
			syntaxError = openStringReason != null ? openStringReason : "unterminated string literal";
		}
		if (syntaxError == null && bracketDepth != 0) {
			syntaxError = "unbalanced bracket at end of file";
		}
		return new Model(lines, syntaxError != null, syntaxError);
	}

	/**
	 * Split on the newline conventions Python accepts, dropping the final empty fragment.
	 */
	private static String[] splitPhysical(String content) {
		String normalized = content.replace("\r\n", "\n").replace('\r', '\n');
		if (normalized.endsWith("\n")) {
			normalized = normalized.substring(0, normalized.length() - 1);
		}
		if (normalized.isEmpty()) {
			return new String[0];
		}
		return normalized.split("\n", -1);
	}

	/**
	 * Column of the first non-whitespace character, with tabs advanced to multiples of 8.
	 */
	private static int indentOf(String line) {
		int column = 0;
		for (int i = 0; i < line.length(); i++) {
			char c = line.charAt(i);
			if (c == ' ') {
				column++;
			}
			else if (c == '\t') {
				column += 8 - (column % 8);
			}
			else {
				return column;
			}
		}
		return column;
	}

	/** Index of an unescaped {@code delimiter} in {@code line}, or -1. */
	private static int findClose(String line, String delimiter) {
		return findCloseFrom(line, delimiter, 0);
	}

	private static int findCloseFrom(String line, String delimiter, int from) {
		for (int i = line.indexOf(delimiter, from); i >= 0; i = line.indexOf(delimiter, i + 1)) {
			if (countEscapes(line, i) % 2 == 0) {
				return i;
			}
		}
		return -1;
	}

	private static int countEscapes(String line, int position) {
		int count = 0;
		for (int i = position - 1; i >= 0 && line.charAt(i) == '\\'; i--) {
			count++;
		}
		return count;
	}

	/** One-line scan result. */
	private static final class Scan {

		String code = "";

		int bracketDepth;

		boolean lineContinuation;

		String openString;

	}

	/**
	 * Strip comments and string literals from one physical line, tracking bracket depth
	 * and the ways a logical line can spill onto the next one. String bodies collapse to
	 * a placeholder so their contents cannot register as code.
	 */
	private static Scan scanLine(String line, int bracketDepthIn) {
		Scan scan = new Scan();
		scan.bracketDepth = bracketDepthIn;
		StringBuilder code = new StringBuilder(line.length());

		int i = 0;
		while (i < line.length()) {
			char c = line.charAt(i);
			if (c == '#') {
				break;
			}
			if (c == '\\') {
				if (countEscapes(line, i) % 2 == 1) {
					code.append(c);
					i++;
					continue;
				}
				if (isTail(line, i)) {
					scan.lineContinuation = true;
					i++;
					continue;
				}
				// Explicit line join inside the line: drop the backslash, keep the next
				// char.
				code.append(' ');
				if (i + 1 < line.length()) {
					code.append(line.charAt(i + 1));
				}
				i += 2;
				continue;
			}
			if (c == '\'' || c == '"') {
				String delimiter = tripleAt(line, i) != null ? tripleAt(line, i) : String.valueOf(c);
				int close = findCloseFrom(line, delimiter, i + delimiter.length());
				if (close < 0) {
					scan.openString = delimiter;
					break;
				}
				i = close + delimiter.length();
				code.append("''");
				continue;
			}
			if (c == '(' || c == '[' || c == '{') {
				scan.bracketDepth++;
			}
			else if (c == ')' || c == ']' || c == '}') {
				scan.bracketDepth--;
			}
			code.append(c);
			i++;
		}
		scan.code = code.toString();
		return scan;
	}

	/**
	 * True when the backslash at {@code position} is the line's last meaningful
	 * character.
	 */
	private static boolean isTail(String line, int position) {
		return line.substring(position + 1).isBlank();
	}

	/** The triple-quote delimiter starting at {@code position}, or null. */
	private static String tripleAt(String line, int position) {
		char quote = line.charAt(position);
		if (position + 2 < line.length() && line.charAt(position + 1) == quote && line.charAt(position + 2) == quote
				&& countEscapes(line, position) % 2 == 0) {
			return String.valueOf(quote).repeat(3);
		}
		return null;
	}

}

/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package org.iromu.embabel.smith.skillevaluator.validator;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * {@code metadata.author} value classification, ported from
 * {@code validators/schema.py:author_value_problem}.
 */
final class SchemaAuthor {

	/** Upper bound for a {@code metadata.author} value; longer values are malformed. */
	static final int AUTHOR_MAX_LENGTH = 256;

	/**
	 * An email address written outside the {@code Name <email@host>} shape. The
	 * discriminator is {@code \w@\w}: an address has a local part, so a word character
	 * sits on each side of the {@code @}, while a social handle such as
	 * {@code Jane Doe (@jdoe)} does not. {@code UNICODE_CHARACTER_CLASS} keeps {@code \w}
	 * Unicode-aware the way Python 3 str patterns are, so {@code Jos\u00e9@example.com}
	 * is still caught.
	 */
	private static final Pattern BARE_EMAIL_PATTERN = Pattern.compile("[^\\s<>@]*\\w@\\w[^\\s<>@]*",
			Pattern.UNICODE_CHARACTER_CLASS);

	/**
	 * Invisible format characters (Unicode category {@code Cf}) that can make an author
	 * value render as something other than what it says: the bidirectional marks,
	 * embeddings, overrides and isolates, plus the byte-order mark. The rest of
	 * {@code Cf} stays allowed, because ZWNJ (U+200C) and ZWJ (U+200D) are required for
	 * Persian, Arabic and Devanagari names to render correctly.
	 */
	private static final Set<Integer> UNSAFE_AUTHOR_FORMAT_CHARS = Set.of((int) '\u061C', // ARABIC
																							// LETTER
																							// MARK
			(int) '\u200E', // LEFT-TO-RIGHT MARK
			(int) '\u200F', // RIGHT-TO-LEFT MARK
			(int) '\u202A', // LEFT-TO-RIGHT EMBEDDING
			(int) '\u202B', // RIGHT-TO-LEFT EMBEDDING
			(int) '\u202C', // POP DIRECTIONAL FORMATTING
			(int) '\u202D', // LEFT-TO-RIGHT OVERRIDE
			(int) '\u202E', // RIGHT-TO-LEFT OVERRIDE
			(int) '\u2066', // LEFT-TO-RIGHT ISOLATE
			(int) '\u2067', // RIGHT-TO-LEFT ISOLATE
			(int) '\u2068', // FIRST STRONG ISOLATE
			(int) '\u2069', // POP DIRECTIONAL ISOLATE
			(int) '\uFEFF' // ZERO WIDTH NO-BREAK SPACE (byte-order mark)
	);

	private SchemaAuthor() {
	}

	/**
	 * Return why {@code author} is malformed or unsafe, or {@code null} when it is
	 * usable. A usable value is either the {@code Name <email@host>} shape or plain
	 * attribution: a person, team, or organization name such as {@code NVIDIA cuOpt Team}
	 * or {@code Jane Doe (@jdoe)}. Plain attribution may not contain angle brackets or an
	 * email address, because those mean an attempted but broken {@code Name <email>}
	 * value.
	 *
	 * <p>
	 * The character guard rejects controls, surrogates, private-use code points and the
	 * bidirectional/BOM format characters. It deliberately does not reject all of Unicode
	 * category C: that also covers the joiners real names need (U+200C/U+200D) and every
	 * unassigned code point, which would make the verdict depend on the platform's
	 * Unicode data rather than on the value.
	 */
	static String authorValueProblem(String author) {
		int length = author.codePointCount(0, author.length());
		if (length > AUTHOR_MAX_LENGTH) {
			return "is longer than " + AUTHOR_MAX_LENGTH + " characters";
		}
		for (int i = 0; i < author.length();) {
			int cp = author.codePointAt(i);
			i += Character.charCount(cp);
			if (UNSAFE_AUTHOR_FORMAT_CHARS.contains(cp) || isUnsafeCategory(cp)) {
				return "contains control, private-use, or bidirectional formatting characters";
			}
		}
		if (SchemaPolicy.AUTHOR_SHAPE_PATTERN.matcher(author).matches()) {
			return null;
		}
		if (author.indexOf('<') >= 0 || author.indexOf('>') >= 0) {
			return "uses angle brackets but is not of the form 'Name <email@host>'";
		}
		if (BARE_EMAIL_PATTERN.matcher(author).find()) {
			return "contains an email address that is not in the form 'Name <email@host>'";
		}
		if (!hasLetterOrDigit(author)) {
			return "has no letters or digits";
		}
		return null;
	}

	/**
	 * Unicode general categories never usable in an author value: C0/C1 controls,
	 * surrogates, private use.
	 */
	private static boolean isUnsafeCategory(int codePoint) {
		int type = Character.getType(codePoint);
		return type == Character.CONTROL || type == Character.SURROGATE || type == Character.PRIVATE_USE;
	}

	/**
	 * Python's per-character {@code isalnum()}: any Unicode letter (L*) or number (N*).
	 */
	private static boolean hasLetterOrDigit(String value) {
		for (int i = 0; i < value.length();) {
			int cp = value.codePointAt(i);
			i += Character.charCount(cp);
			int type = Character.getType(cp);
			if (Character.isLetter(cp) || Character.isDigit(cp) || type == Character.LETTER_NUMBER
					|| type == Character.OTHER_NUMBER) {
				return true;
			}
		}
		return false;
	}

	/**
	 * First {@code maxCodePoints} code points, mirroring Python's {@code value[:n]}
	 * slicing.
	 */
	static String truncate(String value, int maxCodePoints) {
		if (value == null) {
			return null;
		}
		int length = value.codePointCount(0, value.length());
		if (length <= maxCodePoints) {
			return value;
		}
		return value.substring(0, value.offsetByCodePoints(0, maxCodePoints));
	}

}

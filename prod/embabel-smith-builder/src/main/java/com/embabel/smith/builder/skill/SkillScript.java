/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.skill;

import java.util.regex.Pattern;

/**
 * One JavaScript file a skill carries beside its {@code SKILL.md}. The Agent Skills
 * format puts a skill's scripts in a {@code scripts/} folder in the skill directory, so
 * the path of a script is not free text: it is always that folder plus a bare file name.
 * That is enforced here rather than at the write boundary, because the name arrives from
 * a model — {@link SkillGenerator} parses it out of the generated JSON — and a name
 * carrying a separator or a {@code ..} would be a request to write outside the published
 * skill folder.
 *
 * <p>
 * JavaScript only, because that is the language the builder's script engine implements
 * ({@code skill/script/GraalJsSkillScriptExecutionEngine} serves
 * {@code ScriptLanguage.JAVASCRIPT} and lets any other language fall through). A script
 * the reader cannot run is not a script the builder should be publishing.
 *
 * @param path the path inside the skill folder, always {@code scripts/<name>.js}.
 * @param content the JavaScript source, written verbatim.
 * @author Ivan Rodriguez
 */
public record SkillScript(String path, String content) {

	/** The folder a skill's scripts live in, per the Agent Skills format. */
	public static final String FOLDER = "scripts";

	/**
	 * A script file name: a bare name, no directory, no {@code ..}, ending in
	 * {@code .js}. The leading character is restricted so a name cannot be a hidden file
	 * or an operator-looking string; the rest allows the hyphen and underscore a real
	 * script name uses.
	 */
	private static final Pattern FILE_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]*\\.js");

	public SkillScript {
		if (!isInScriptFolder(path)) {
			throw new IllegalArgumentException("A skill script must live at scripts/<name>.js, not '" + path + "'");
		}
		content = (content != null) ? content : "";
	}

	/**
	 * The script the model asked for, at the folder's path. The declared path is reduced
	 * to its file name and placed under {@code scripts/}, so a model that writes
	 * {@code scripts/totals.js} and one that writes {@code totals.js} produce the same
	 * script; what cannot be reduced to a bare {@code .js} name is refused rather than
	 * silently relocated.
	 * @param declaredPath the path as the model named it, {@code scripts/} prefix or not.
	 * @param content the JavaScript source.
	 * @return the script, addressed inside the skill folder.
	 * @throws IllegalArgumentException when the name is not a bare {@code .js} file name.
	 */
	public static SkillScript of(String declaredPath, String content) {
		return new SkillScript(FOLDER + "/" + fileName(declaredPath), content);
	}

	/**
	 * The file name of a script path — the part after the last separator, which is what
	 * {@link #of} re-addresses under {@code scripts/}.
	 * @param declaredPath the path as declared.
	 * @return the bare file name.
	 */
	private static String fileName(String declaredPath) {
		if (declaredPath == null) {
			throw new IllegalArgumentException("A skill script must name a .js file");
		}
		String normalized = declaredPath.trim().replace('\\', '/');
		int lastSeparator = normalized.lastIndexOf('/');
		String name = (lastSeparator < 0) ? normalized : normalized.substring(lastSeparator + 1);
		if (!FILE_NAME.matcher(name).matches()) {
			throw new IllegalArgumentException("A skill script must be a .js file name, not '" + declaredPath + "'");
		}
		return name;
	}

	private static boolean isInScriptFolder(String path) {
		return path != null
				&& (path.startsWith(FOLDER + "/") && FILE_NAME.matcher(path.substring(FOLDER.length() + 1)).matches());
	}

}

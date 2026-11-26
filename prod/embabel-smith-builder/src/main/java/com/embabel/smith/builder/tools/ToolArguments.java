/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.builder.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Reading a tool's arguments the way the platform delivers them, shared by the builder's
 * tools.
 *
 * <p>
 * The platform hands a tool the argument string as the model wrote it, so a schema-
 * conforming call arrives as {@code {"name": "value"}}. Two deviations are tolerated
 * because both were seen on the validator before this class existed: a call that names
 * nothing but carries the payload anyway, and a bare quoted string. The first is read as
 * the payload itself; the second is unquoted. A brace-led string that is not JSON at all
 * carried no argument rather than one written with a brace, and the tool says so rather
 * than guessing.
 *
 * <p>
 * It is the same tolerance {@code eval.BuilderPackTools} grew for {@code specJson},
 * extracted because four more tools now need it and a fifth copy of the fallback would be
 * five places to keep in step.
 *
 * @author Ivan Rodriguez
 */
public final class ToolArguments {

	private static final ObjectMapper JSON = new ObjectMapper();

	private ToolArguments() {
	}

	/**
	 * The string value of a named argument.
	 * @param arguments the raw tool-call arguments, possibly null
	 * @param parameter the argument name the tool's schema declares
	 * @return the value, blank when the call carried none
	 */
	public static String string(String arguments, String parameter) {
		String raw = (arguments != null) ? arguments.trim() : "";
		if (raw.startsWith("{")) {
			try {
				JsonNode named = JSON.readTree(raw).get(parameter);
				return (named != null) ? named.asText("").trim() : "";
			}
			catch (Exception ignored) {
				return "";
			}
		}
		return raw.replaceAll("^\"|\"$", "");
	}

	/**
	 * The whole argument object when the call carried one and the tool takes no named
	 * argument, as opposed to a stray string that is not an object.
	 * @param arguments the raw tool-call arguments, possibly null
	 * @return the object text, or blank when the call was not an object
	 */
	public static String object(String arguments) {
		String raw = (arguments != null) ? arguments.trim() : "";
		if (!raw.startsWith("{")) {
			return "";
		}
		try {
			JSON.readTree(raw);
			return raw;
		}
		catch (Exception ignored) {
			return "";
		}
	}

}

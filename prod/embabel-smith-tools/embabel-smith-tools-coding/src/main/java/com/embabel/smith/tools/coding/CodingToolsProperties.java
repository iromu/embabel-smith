/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.tools.coding;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The coding addon's settings, all under the same prefix as its switches.
 *
 * <p>
 * The two {@code enabled} flags are deliberately absent. They are read by
 * {@code @ConditionalOnProperty}, which decides whether a group's bean exists at all, and
 * binding them here as well would put a second, mutable copy of a value that only matters
 * at start-up next to the one that decides. This class holds the settings that are values
 * rather than gates — the run root, the two bounds on a command, whether the group that
 * registers by default may write, and the roles an operator composes for itself.
 *
 * <p>
 * <b>{@code expose} is a closed vocabulary, not a class loader.</b> Its values are
 * capability names from {@link ExposedToolGroups#CAPABILITIES}, and an entry outside that
 * list refuses the boot naming the offender. That is the design choice the property
 * exists to make explicit: an operator can recompose what the platform's own factories
 * offer under a role of their choosing, but cannot get this addon to instantiate a class
 * their property named.
 *
 * @author Ivan Rodriguez
 */
@ConfigurationProperties(prefix = "smith.tools")
public class CodingToolsProperties {

	private final Coding coding = new Coding();

	/**
	 * {@code smith.tools.expose.<role>} — the roles this deployment composes for itself
	 * from the named capabilities. Empty by default, which is what leaves the registry
	 * exactly as the two switches describe it.
	 */
	private Map<String, List<String>> expose = new LinkedHashMap<>();

	/**
	 * The settings shared by the coding groups.
	 * @return the coding settings
	 */
	public Coding getCoding() {
		return this.coding;
	}

	/**
	 * The operator-composed roles, each naming the capabilities it wants.
	 * @return role name to capability names, in the order the configuration carries them
	 */
	public Map<String, List<String>> getExpose() {
		return this.expose;
	}

	/**
	 * Set the operator-composed roles.
	 * @param expose role name to the capability names it asks for
	 */
	public void setExpose(Map<String, List<String>> expose) {
		this.expose = expose;
	}

	/**
	 * {@code smith.tools.coding} — the run root, and what a command may do inside it.
	 */
	public static class Coding {

		/**
		 * The directory every tool is confined to. Empty means the process's own working
		 * directory, which is the documented default: a deployment that says nothing is
		 * confined to wherever it was started, not to the whole filesystem.
		 */
		private String root = "";

		/**
		 * Whether the default-on {@code file} group is allowed to hold a writer at all.
		 * Off by default, and off is the whole point: this is the setting that decides
		 * what an untouched deployment can be asked to do to a filesystem, so the answer
		 * is "read it, not change it" until an operator says otherwise. It is read once,
		 * at construction, so the read-only deployment never builds a writer rather than
		 * filtering one out afterwards.
		 */
		private boolean writes = false;

		/**
		 * How long a command may run before it is terminated and reported as timed out.
		 */
		private Duration commandTimeout = Duration.ofSeconds(60);

		/**
		 * How much of each of a command's streams the result keeps; the rest is dropped
		 * with the truncation stated in the result.
		 */
		private int maxOutputChars = 20_000;

		/**
		 * The run root.
		 * @return the configured root, blank for the working directory
		 */
		public String getRoot() {
			return this.root;
		}

		/**
		 * Set the run root.
		 * @param root the directory to confine every tool to
		 */
		public void setRoot(String root) {
			this.root = root;
		}

		/**
		 * Whether the default group holds a writer.
		 * @return {@code true} when {@code smith.tools.coding.writes} was set
		 */
		public boolean isWrites() {
			return this.writes;
		}

		/**
		 * Set whether the default group holds a writer.
		 * @param writes whether to build the platform's read-write file tools
		 */
		public void setWrites(boolean writes) {
			this.writes = writes;
		}

		/**
		 * The command's wall-clock limit.
		 * @return the timeout
		 */
		public Duration getCommandTimeout() {
			return this.commandTimeout;
		}

		/**
		 * Set the command's wall-clock limit.
		 * @param commandTimeout how long a command may run before it is killed
		 */
		public void setCommandTimeout(Duration commandTimeout) {
			this.commandTimeout = commandTimeout;
		}

		/**
		 * The per-stream output allowance.
		 * @return the maximum number of characters kept of each stream
		 */
		public int getMaxOutputChars() {
			return this.maxOutputChars;
		}

		/**
		 * Set the per-stream output allowance.
		 * @param maxOutputChars how many characters of each stream to keep
		 */
		public void setMaxOutputChars(int maxOutputChars) {
			this.maxOutputChars = maxOutputChars;
		}

	}

}

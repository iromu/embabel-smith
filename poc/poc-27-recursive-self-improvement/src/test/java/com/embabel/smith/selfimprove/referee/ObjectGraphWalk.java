/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.referee;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.embabel.agent.skills.script.SkillScriptExecutionEngine;

/**
 * Walks an object graph through instance fields and reports the classes it reached. This
 * is the enforcement behind "the referee opens no script context": rather than trusting
 * that the constructor looks right, the test asks what is actually reachable from the
 * object it built.
 *
 * <p>
 * Two limits are stated rather than hidden. Library internals under
 * {@link #LIBRARY_PREFIXES} are reported but not reflected into — a Graal context cannot
 * appear there on its own, only in a field of this project's own code or of the prod code
 * it wires, and both of those are traversed. Any field reflection cannot read is recorded
 * in {@link #blockedByReflection()} and asserted empty, so the walk cannot quietly
 * under-cover the graph it claims to have covered.
 *
 * @author Ivan Rodriguez
 */
final class ObjectGraphWalk {

	private static final int MAX_OBJECTS = 20_000;

	private static final List<String> LIBRARY_PREFIXES = List.of("java.", "javax.", "jdk.", "sun.", "kotlin.",
			"org.springframework.", "com.fasterxml.");

	/**
	 * Name fragments that mean a script engine or a script context is reachable: POC 26's
	 * engine and package, Embabel's script SPI package, and Graal's own polyglot and
	 * Truffle namespaces. These are a convenience — the type test in {@link #note} is
	 * what the check actually relies on, because a name is something a future refactor
	 * can change without changing what the object is.
	 */
	private static final List<String> SCRIPT_MARKERS = List.of("graal", "polyglot", "skillscripts", "skills.script",
			"scriptexecutionengine", "truffle");

	private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

	private final Set<String> classes = new LinkedHashSet<>();

	private final List<String> scriptClasses = new ArrayList<>();

	private final List<String> blockedByReflection = new ArrayList<>();

	private final Deque<Object> pending = new ArrayDeque<>();

	private int objects;

	private ObjectGraphWalk(Object root) {
		push(root);
		while (!this.pending.isEmpty()) {
			visit(this.pending.poll());
		}
	}

	/**
	 * Walks everything reachable from an object.
	 * @param root the object to start from
	 * @return the walk, with the classes it reached
	 */
	static ObjectGraphWalk from(Object root) {
		return new ObjectGraphWalk(root);
	}

	/**
	 * Every class name the walk reached, including the library types it declined to
	 * enter.
	 * @return the reached class names
	 */
	Set<String> classes() {
		return Set.copyOf(this.classes);
	}

	/**
	 * The reached classes that are script engines or script contexts, or live in a
	 * script-engine namespace.
	 * @return the offending class names; empty when the graph carries no script engine
	 */
	List<String> scriptClasses() {
		return List.copyOf(this.scriptClasses);
	}

	/**
	 * Fields the walk could not read.
	 * @return one entry per field reflection refused, in the form {@code Class#field}
	 */
	List<String> blockedByReflection() {
		return List.copyOf(this.blockedByReflection);
	}

	private void visit(Object object) {
		if (!this.visited.add(object)) {
			return;
		}
		if (++this.objects > MAX_OBJECTS) {
			throw new IllegalStateException("object graph walk exceeded " + MAX_OBJECTS + " objects; the graph is not"
					+ " small enough for this check to be meaningful");
		}
		Class<?> type = object.getClass();
		note(type, object);
		if (object instanceof Object[] elements) {
			for (Object element : elements) {
				push(element);
			}
			return;
		}
		if (object instanceof Collection<?> collection) {
			collection.forEach(this::push);
			return;
		}
		if (object instanceof Map<?, ?> map) {
			map.keySet().forEach(this::push);
			map.values().forEach(this::push);
			return;
		}
		if (isLibrary(type.getName())) {
			return;
		}
		readFields(type, object);
	}

	/**
	 * Queues an object for the walk, ignoring null references. A null field is the
	 * absence of a collaborator, which is the good case, and the walk's queue rejects
	 * nulls by design.
	 * @param object the object to queue, possibly null
	 */
	private void push(Object object) {
		if (object != null) {
			this.pending.add(object);
		}
	}

	private void readFields(Class<?> type, Object object) {
		for (Class<?> level = type; level != null && !isLibrary(level.getName()); level = level.getSuperclass()) {
			for (Field field : level.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				try {
					field.setAccessible(true);
					push(field.get(object));
				}
				catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
					this.blockedByReflection.add(level.getName() + "#" + field.getName());
				}
			}
		}
	}

	private void note(Class<?> type, Object object) {
		String name = type.getName();
		this.classes.add(name);
		String folded = name.toLowerCase(Locale.ROOT);
		if (object instanceof SkillScriptExecutionEngine || SCRIPT_MARKERS.stream().anyMatch(folded::contains)) {
			this.scriptClasses.add(name);
		}
	}

	private static boolean isLibrary(String className) {
		return LIBRARY_PREFIXES.stream().anyMatch(className::startsWith);
	}

}

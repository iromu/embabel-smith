/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.control;

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
import java.util.Map;
import java.util.Set;

import com.embabel.smith.builder.improve.LoopPublishPolicy;
import com.embabel.smith.builder.publish.Publishable;
import com.embabel.smith.builder.publish.PublishResult;
import com.embabel.smith.builder.publish.Publisher;

/**
 * Walks an object graph through instance fields and reports which reached objects could
 * write a pack to a source. This is the enforcement behind task 8.2's walk half: rather
 * than trusting that {@code HeldForApproval} looks publisher-free, the test asks what is
 * actually reachable from the object it built.
 *
 * <p>
 * Detection is by <em>type</em>, not by name, because a name is something a refactor can
 * change without changing what the object is: an object is reported when it is a
 * {@link Publisher} (the only SPI whose {@code publish} method takes the {@code push}
 * argument), a {@link Publishable}, a {@link PublishResult} (whose presence would mean a
 * publish already happened), or a {@link LoopPublishPolicy} (the one class in the
 * reachable code that stores the flag). The name fragments are a backstop for a
 * delegating wrapper, never the primary test, and every report is exercised by a control
 * case in the test so the walk cannot pass by being blind.
 *
 * <p>
 * Two limits are stated rather than hidden. A lambda is an object with fields, so a
 * captured value is walked, but what its <em>body</em> calls is not visible to reflection
 * at all — and a lambda a class holds in a static constant, which is where a stateless
 * one lives, is not in the instance graph either. Both cases are why the test also scans
 * the POC's own sources. And library internals under {@link #LIBRARY_PREFIXES} are noted
 * but not entered; fields reflection refuses to read are reported by
 * {@link #blockedByReflection()} and asserted empty, so the walk cannot quietly
 * under-cover the graph it claims to have covered.
 *
 * <p>
 * It does not reuse the referee's {@code ObjectGraphWalk}: that class is package-private
 * to {@code selfimprove.referee} and looks for script engines. The shape is deliberately
 * the same.
 *
 * @author Ivan Rodriguez
 */
final class PublishingPathWalk {

	private static final int MAX_OBJECTS = 20_000;

	private static final List<String> LIBRARY_PREFIXES = List.of("java.", "javax.", "jdk.", "sun.", "kotlin.",
			"org.springframework.", "com.fasterxml.");

	/**
	 * Name fragments that mean a publishing path is reachable even when the object does
	 * not implement any of the interfaces above. Convenience only — {@link #note} is the
	 * check the assertion relies on.
	 */
	private static final List<String> PUBLISHING_NAME_MARKERS = List.of("builder.publish.", "GitPublisher",
			"LoopPublishPolicy", "ImprovementLoop", "LoopService");

	private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

	private final List<String> publishingClasses = new ArrayList<>();

	private final List<String> blockedByReflection = new ArrayList<>();

	private final Deque<Object> pending = new ArrayDeque<>();

	private final Set<String> classes = new LinkedHashSet<>();

	private int objects;

	private PublishingPathWalk(Object root) {
		push(root);
		while (!this.pending.isEmpty()) {
			visit(this.pending.poll());
		}
	}

	/**
	 * Walks everything reachable from an object.
	 * @param root the object to start from
	 * @return the walk, with what it reached
	 */
	static PublishingPathWalk from(Object root) {
		return new PublishingPathWalk(root);
	}

	/**
	 * The reached objects that could write a pack to a source or carry the push flag.
	 * @return one entry per offending object; empty when the graph carries no publishing
	 * path
	 */
	List<String> publishingClasses() {
		return List.copyOf(this.publishingClasses);
	}

	/**
	 * Fields the walk could not read.
	 * @return one entry per field reflection refused, in the form {@code Class#field}
	 */
	List<String> blockedByReflection() {
		return List.copyOf(this.blockedByReflection);
	}

	/**
	 * Every class name the walk reached, including the library types it declined to
	 * enter.
	 * @return the reached class names
	 */
	Set<String> classes() {
		return Set.copyOf(this.classes);
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
		boolean publishing = object instanceof Publisher || object instanceof Publishable
				|| object instanceof PublishResult || object instanceof LoopPublishPolicy
				|| PUBLISHING_NAME_MARKERS.stream().anyMatch(name::contains);
		if (publishing) {
			this.publishingClasses.add(name);
		}
	}

	private static boolean isLibrary(String className) {
		return LIBRARY_PREFIXES.stream().anyMatch(className::startsWith);
	}

}

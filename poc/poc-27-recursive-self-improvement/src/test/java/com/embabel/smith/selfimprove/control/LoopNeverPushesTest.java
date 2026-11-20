/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.control;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.embabel.smith.builder.publish.FolderPublisher;
import com.embabel.smith.builder.publish.PublishTarget;
import com.embabel.smith.builder.publish.Publisher;
import com.embabel.smith.studio.improve.LoopPublishPolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Task 8.2: the loop never pushes. The loop's push flag lives in one field —
 * {@code LoopPublishPolicy.push} — and it reaches a remote only as the third argument of
 * {@code Publisher.publish(unit, overwrite, push)}. The claim is therefore checked three
 * ways, each catching what the others cannot see:
 *
 * <ol>
 * <li><b>The object graph.</b> Nothing reachable from a populated {@link HeldForApproval}
 * is a publisher or a publish policy, so there is no flag set to true anywhere in the
 * POC's held-candidate path.</li>
 * <li><b>The POC's own sources.</b> No production source of this POC names a publishing
 * type, which covers what a walk cannot see: a constructor no test ever builds, a static
 * call, and a lambda — whose body reflection cannot read, and which is not in the
 * instance graph at all when the class holds it in a static constant, as this one does.
 * This is a scan for <em>type references</em>, not for the word "push" — the word appears
 * in the POC's own frozen task set and in {@code MUTABILITY.md}, and a grep for it would
 * fail on true statements.</li>
 * <li><b>The configuration that supplies it.</b> The setting is bound in one place — the
 * studio's Spring configuration, which {@code docs/10-self-improvement.md} names as the
 * only route in — and this test reads that class' constant pool to check the configured
 * default is off. This module never sets the key itself.</li>
 * </ol>
 *
 * <p>
 * <b>Limits, stated rather than worked around.</b> (a) The walk sees objects that exist
 * at test time; it cannot see a publisher a future class constructs reflectively
 * ({@code Class.forName}), so the source scan is the backstop and neither claim is
 * complete without the other. (b) The builder CLI has its own {@code --push} option
 * ({@code BuildOptions.push}), which is a human decision made on a command line this POC
 * never invokes; it is excluded from the scan because the flag is only ever acted on by a
 * publisher, which no POC source names. (c) Writing a pack to a {@code folder:} source is
 * not a push and is covered by task 8.1, not here. (d) The scan strips comments before
 * matching, so a {@code //} inside a string literal can hide the rest of that line —
 * which is why it is the backstop and not the primary check. (e) The constant-pool check
 * reads the studio's configuration class only; some other jar binding the same key under
 * a different name would not be visible to it, which is why the walk and the scan carry
 * the weight of the claim.
 *
 * @author Ivan Rodriguez
 */
class LoopNeverPushesTest {

	/**
	 * The studio's Spring configuration, read as bytes rather than loaded: it is the only
	 * class on this classpath that supplies the push flag from configuration, and reading
	 * its constant pool avoids resolving a bean class whose method signatures reference
	 * the web and JPA jars this module excludes.
	 */
	private static final String STUDIO_CONFIG_CLASS = "com/embabel/smith/studio/config/StudioConfiguration.class";

	private static final String PUSH_SETTING = "smith.studio.loop.push";

	private static final Path RESOURCES = Path.of(System.getProperty("smith.self-improve.dir", "src/main/resources"));

	private static final Path POC_SOURCES = RESOURCES.resolveSibling("java").resolve("com/embabel/smith/selfimprove");

	private static final Path CONTROL_TESTS = RESOURCES.getParent()
		.getParent()
		.resolve("test/java/com/embabel/smith/selfimprove/control");

	/**
	 * Types that carry or act on the push flag. {@code (?<!Loop)Publisher} deliberately
	 * excludes {@code ApprovalQueue.LoopPublisher}, the hook this POC must supply and
	 * which cannot write anything on its own; it matches every publisher that can.
	 */
	private static final List<String> PUBLISHING_REFERENCES = List.of("(?<!Loop)Publisher", "Publishable",
			"PublishResult", "PublishTarget", "LoopPublishPolicy", "LoopPushDisabledException", "ImprovementLoop",
			"LoopService", Pattern.quote(PUSH_SETTING));

	@Test
	void theHeldCandidateGraphReachesNoPublisherAndNoPublishPolicy() {
		HeldForApproval held = new HeldForApproval();
		held.hold(ControlFixtures.candidate("gen1-variant-a"), ControlFixtures.gated("gen1-variant-a", 0.833, 1.0),
				0.333);

		PublishingPathWalk walk = PublishingPathWalk.from(held);

		assertThat(walk.publishingClasses()).isEmpty();
		assertThat(walk.blockedByReflection()).isEmpty();
		// Coverage, not just absence: the walk did enter the queue and read the record it
		// holds, so
		// the empty result above is about a populated graph rather than an empty one.
		assertThat(walk.classes()).contains("com.embabel.smith.studio.improve.ApprovalQueue",
				"com.embabel.smith.studio.improve.PendingCandidate", "com.embabel.smith.common.model.AgentSpec");
	}

	@Test
	void theWalkReportsAPublishingPathWhenOneIsPresent() {
		// Control case for the assertion above. A walk that reports nothing because it
		// cannot see what
		// it is looking for is the failure this module has already been caught making
		// once,
		// so the
		// detector is proven against both shapes: a publisher behind a field, and the
		// policy that
		// stores the flag.
		List<String> behindAField = PublishingPathWalk
			.from(new BearingOnAPublisher(new FolderPublisher(PublishTarget.parse("folder:/tmp/unused-poc27"))))
			.publishingClasses();
		assertThat(behindAField).contains(FolderPublisher.class.getName());

		LoopPublishPolicy pushing = new LoopPublishPolicy(true, "git:https://example.invalid/packs.git", null);
		assertThat(PublishingPathWalk.from(pushing).publishingClasses()).contains(LoopPublishPolicy.class.getName());
	}

	@Test
	void thePushFlagHasNoDefaultAndNoWayInButExplicitly() {
		// The flag cannot arrive by omission: the policy is final, has one constructor,
		// its
		// first
		// parameter is the boolean itself, and it declares no factory that could supply a
		// default.
		// So "the loop pushed" is always a line of code that said so.
		Constructor<?>[] constructors = LoopPublishPolicy.class.getDeclaredConstructors();
		assertThat(constructors).hasSize(1);
		assertThat(constructors[0].getParameterTypes()[0]).isEqualTo(boolean.class);
		assertThat(Modifier.isFinal(LoopPublishPolicy.class.getModifiers())).isTrue();
		assertThat(Stream.of(LoopPublishPolicy.class.getDeclaredMethods())
			.filter((method) -> Modifier.isStatic(method.getModifiers()))
			.map(Method::getName)
			.toList()).isEmpty();
	}

	@Test
	void theOnlyConfiguredValueOfThePushFlagOnThisClasspathDefaultsToOff() throws IOException {
		String studioConfig = classFileBytes(STUDIO_CONFIG_CLASS);

		assertThat(studioConfig).contains(PUSH_SETTING + ":false");
		assertThat(studioConfig).doesNotContain(PUSH_SETTING + ":true");
	}

	@Test
	void thisModuleNeverConfiguresThePushFlag() throws IOException {
		// The flag is only reachable from outside by setting the key. The POC's own
		// resources never
		// mention it, so there is no run of this module in which it is on.
		List<String> mentions = new ArrayList<>();
		for (Path file : filesUnder(RESOURCES)) {
			String text = new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1);
			if (text.contains(PUSH_SETTING)) {
				mentions.add(file.toString());
			}
		}
		assertThat(mentions).isEmpty();
	}

	@Test
	void noPocProductionSourceNamesAPublishingPath() throws IOException {
		List<Path> sources = javaSourcesUnder(POC_SOURCES);

		// The scan is only worth anything if it read the files it claims to have read,
		// and
		// the path
		// it resolves from is a convention rather than a guarantee.
		assertThat(sources).extracting((path) -> path.getFileName().toString()).contains("HeldForApproval.java");
		assertThat(references(sources)).isEmpty();
	}

	@Test
	void theSourceScanFindsAPublishingReferenceWhenOneIsThere() throws IOException {
		// Control case for the scan: these very tests name a publisher to build the
		// walk's
		// control
		// case, so the same matcher run over them must report it.
		assertThat(references(javaSourcesUnder(CONTROL_TESTS))).isNotEmpty();
	}

	private static List<String> references(List<Path> files) throws IOException {
		List<String> hits = new ArrayList<>();
		for (Path file : files) {
			String code = withoutComments(new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
			for (String reference : PUBLISHING_REFERENCES) {
				if (Pattern.compile(reference).matcher(code).find()) {
					hits.add(file.getFileName() + " names " + reference);
				}
			}
		}
		return List.copyOf(hits);
	}

	private static String withoutComments(String text) {
		return text.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("//[^\n]*", " ");
	}

	private static List<Path> javaSourcesUnder(Path root) throws IOException {
		try (Stream<Path> paths = filesUnder(root).stream().filter((path) -> path.toString().endsWith(".java"))) {
			return paths.sorted(Comparator.comparing(Path::toString)).toList();
		}
	}

	private static List<Path> filesUnder(Path root) throws IOException {
		if (!Files.isDirectory(root)) {
			throw new IllegalStateException(root + " is not a directory: this test locates the module through the"
					+ " smith.self-improve.dir convention and cannot check anything if that does not resolve");
		}
		try (Stream<Path> paths = Files.walk(root)) {
			return paths.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
		}
	}

	private static String classFileBytes(String resource) throws IOException {
		try (InputStream in = LoopPublishPolicy.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException(resource + " is not on this module's classpath, so the default it"
						+ " supplies cannot be checked here");
			}
			return new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
		}
	}

	/**
	 * A field-bearing holder, used only to prove the walk reaches a publisher behind a
	 * field rather than only one it is handed as the root.
	 */
	private record BearingOnAPublisher(Publisher publisher) {
	}

}

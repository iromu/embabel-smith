/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.runtime.remote;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The bridge configuration grammar. The case that matters is the entry with no {@code @}:
 * it is reported as malformed, never guessed into a bridge, because the alternative is
 * the product contacting a host nobody wrote down — or naming one it invented.
 */
class RemoteBridgesTest {

	@Test
	void aNameAtUrlPairBecomesOneBridge() {
		RemoteBridges.Parsed parsed = RemoteBridges.parse("digest@http://127.0.0.1:18090");

		assertThat(parsed.bridges()).containsExactly(new RemoteBridge("digest", "http://127.0.0.1:18090"));
		assertThat(parsed.malformed()).isEmpty();
	}

	@Test
	void severalEntriesParseInConfiguredOrderAndBlanksAreSkipped() {
		RemoteBridges.Parsed parsed = RemoteBridges.parse("a@http://one,  ,b@http://two,");

		assertThat(parsed.bridges()).containsExactly(new RemoteBridge("a", "http://one"),
				new RemoteBridge("b", "http://two"));
		assertThat(parsed.malformed()).as("a blank entry is an unset slot, not a broken one").isEmpty();
	}

	@Test
	void anEntryWithNoAtIsReportedMalformedRatherThanGuessed() {
		RemoteBridges.Parsed parsed = RemoteBridges.parse("http://localhost:18090");

		assertThat(parsed.bridges()).as("no name is invented for a bare URL").isEmpty();
		assertThat(parsed.malformed()).containsExactly("http://localhost:18090");
	}

	@Test
	void anEntryMissingEitherSideOfTheAtIsMalformed() {
		assertThat(RemoteBridges.parse("@http://one").malformed()).containsExactly("@http://one");
		assertThat(RemoteBridges.parse("a@").malformed()).containsExactly("a@");
		assertThat(RemoteBridges.parse("a@http://one,b@").bridges())
			.containsExactly(new RemoteBridge("a", "http://one"));
	}

	@Test
	void aGoodEntryBesideABadOneStillLoads() {
		RemoteBridges.Parsed parsed = RemoteBridges.parse("digest@http://one, typo");

		assertThat(parsed.bridges()).containsExactly(new RemoteBridge("digest", "http://one"));
		assertThat(parsed.malformed()).containsExactly("typo");
	}

	@Test
	void nothingConfiguredIsAnEmptyListNotAFailure() {
		assertThat(RemoteBridges.parse(null).bridges()).isEmpty();
		assertThat(RemoteBridges.parse("").bridges()).isEmpty();
		assertThat(RemoteBridges.parse("  ").malformed()).isEmpty();
	}

	@Test
	void aTrailingSlashIsNotTurnedIntoAnEmptyPathSegment() {
		assertThat(RemoteBridges.parse("a@http://one:18090/").bridges())
			.as("a running sidecar must not read as dead because of one slash")
			.containsExactly(new RemoteBridge("a", "http://one:18090"));
	}

	@Test
	void theAtSplitTakesTheNameFromTheFirstAtSoCredentialsInAUrlSurvive() {
		assertThat(RemoteBridges.parse("a@http://user:pw@one:18090").bridges())
			.containsExactly(new RemoteBridge("a", "http://user:pw@one:18090"));
	}

}

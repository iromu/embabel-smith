/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.selfimprove.innerloop;

/**
 * The counter every paid call in this package has to pass through.
 *
 * <p>
 * Design D5 makes the inner loop free, and a free loop is the one claim in this POC that
 * a stray model call would quietly falsify. So the count in {@link GenerationRecord} is
 * read off this object rather than asserted: {@link #counted} is the seam a call is made
 * on, the loop records the delta the counter moved during a generation, and a run that
 * spent nothing shows a zero it can point at. The inner loop never calls it; the paid
 * outer loop will.
 *
 * @author Ivan Rodriguez
 */
public final class ModelCallMeter {

	private int calls;

	/**
	 * Run something that spends a model call.
	 * @param call the work
	 * @param <T> what it returns
	 * @return what the work produced.
	 */
	public <T> T counted(Call<T> call) {
		this.calls++;
		return call.apply();
	}

	/**
	 * The calls spent so far.
	 * @return the count, which the inner loop's runs leave at zero.
	 */
	public int count() {
		return this.calls;
	}

	/**
	 * A unit of work that would cost a model call.
	 *
	 * @param <T> what it returns
	 * @author Ivan Rodriguez
	 */
	@FunctionalInterface
	public interface Call<T> {

		/**
		 * Do the work.
		 * @return whatever the work produced.
		 */
		T apply();

	}

}

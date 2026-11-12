/*
 * Copyright 2026 Ivan Rodriguez.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.embabel.smith.security;

/**
 * The overall install-time verdict for a scanned agent spec.
 *
 * @author Ivan Rodriguez
 */
public enum ScanVerdict {

	/** No findings — safe to load at the recommended tier. */
	SAFE,

	/**
	 * Warning-level findings — load only under scrutiny, at the recommended (higher)
	 * tier.
	 */
	REVIEW,

	/** A critical finding — do not load; quarantine the pack. */
	BLOCK

}

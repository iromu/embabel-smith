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

package com.embabel.smith.builder.publish;

/**
 * A build cannot be published because a pack of the same name already exists at the
 * destination. The build is blocked; the user may retry with {@code --overwrite} to
 * replace it.
 *
 * @author Ivan Rodriguez
 */
public class CollisionException extends RuntimeException {

	public CollisionException(String message) {
		super(message);
	}

}

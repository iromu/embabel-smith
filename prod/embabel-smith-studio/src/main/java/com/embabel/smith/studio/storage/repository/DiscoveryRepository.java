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

package com.embabel.smith.studio.storage.repository;

import com.embabel.smith.studio.storage.entity.DiscoveryEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for the discovery history (change:
 * smith-studio-postgres-storage). Created programmatically by the storage context, not as
 * a Spring-managed bean. Ordering is done in the store (not a derived query) to avoid the
 * ANTLR query parser, which is shadowed on this classpath by an older copy bundled in a
 * transitive Kotlin compiler jar.
 *
 * @author Ivan Rodriguez
 */
public interface DiscoveryRepository extends JpaRepository<DiscoveryEntity, Long> {

}

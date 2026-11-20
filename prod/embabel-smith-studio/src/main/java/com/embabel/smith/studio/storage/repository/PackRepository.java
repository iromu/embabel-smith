/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage.repository;

import com.embabel.smith.studio.storage.entity.PackEntity;
import com.embabel.smith.studio.storage.entity.PackEntityId;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for saved packs (change: smith-studio-postgres-storage). Not
 * a Spring-managed bean: the storage context creates the proxy programmatically per
 * binding so the endpoint can be hot-swapped.
 *
 * <p>
 * It still declares no finders, and the key is now the composite {@code (name, version)}
 * {@link PackEntityId} (change: publish-keeps-versions), so a read by bare name cannot go
 * through {@code findById}. Resolving a name to its highest version is done in the store
 * over fetched rows, not in a derived query, for the same reason as
 * {@link DiscoveryRepository}: the ANTLR query parser is shadowed on this classpath by an
 * older copy bundled in a transitive Kotlin compiler jar.
 *
 * @author Ivan Rodriguez
 */
public interface PackRepository extends JpaRepository<PackEntity, PackEntityId> {

}

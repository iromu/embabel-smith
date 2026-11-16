/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage.repository;

import com.embabel.smith.studio.storage.entity.TraceEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for run traces (change: smith-studio-postgres-storage).
 * Created programmatically by the storage context, not as a Spring-managed bean. Ordering
 * is done in the trace store (not a derived query) to avoid the ANTLR query parser, which
 * is shadowed on this classpath by an older copy bundled in a transitive Kotlin compiler
 * jar.
 *
 * @author Ivan Rodriguez
 */
public interface TraceRepository extends JpaRepository<TraceEntity, Long> {

}

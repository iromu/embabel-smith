/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.embabel.smith.studio.storage.repository;

import com.embabel.smith.studio.storage.entity.PackEntity;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for saved packs (change: smith-studio-postgres-storage). Not
 * a Spring-managed bean: the storage context creates the proxy programmatically per
 * binding so the endpoint can be hot-swapped.
 *
 * @author Ivan Rodriguez
 */
public interface PackRepository extends JpaRepository<PackEntity, String> {

}

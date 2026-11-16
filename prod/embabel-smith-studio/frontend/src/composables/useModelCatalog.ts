/*
 * SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * The platform's model catalog, fetched once and shared by every model selector in the studio. The
 * cache is module-level rather than per-call because the panes mount and unmount as the route
 * changes — each mount calling `useModelCatalog()` must not re-request the same list.
 */

import { ref } from 'vue'
import type { Ref } from 'vue'

import { listModels } from '../api/client'

export interface ModelCatalogState {
  models: Ref<string[]>
  defaultModel: Ref<string | null>
  loading: Ref<boolean>
}

const models = ref<string[]>([])
const defaultModel = ref<string | null>(null)
const loading = ref(false)
let inFlight: Promise<void> | null = null

function loadCatalog(): Promise<void> {
  if (!inFlight) {
    inFlight = (async () => {
      loading.value = true
      try {
        const catalog = await listModels()
        models.value = catalog.models
        defaultModel.value = catalog.defaultModel
      } catch {
        // No catalog reachable: the selector offers only the platform default.
        models.value = []
      } finally {
        loading.value = false
      }
    })()
  }
  return inFlight
}

export function useModelCatalog(): ModelCatalogState {
  void loadCatalog()
  return { models, defaultModel, loading }
}

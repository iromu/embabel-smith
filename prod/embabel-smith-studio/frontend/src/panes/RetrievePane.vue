<!--
  SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
  SPDX-License-Identifier: Apache-2.0
-->
<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="travel_explore"
        title="Retrieve"
        description="Rank a source's packs against a task; lexical scoring with lifecycle curation, offline."
      />

      <SectionCard title="Search the library" icon="search">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 240px"
              data-test="retrieve-source"
            />
          </div>
          <div class="col-auto">
            <q-input
              v-model="query"
              label="Task"
              outlined
              autogrow
              hint="What you want an agent to do."
              style="width: 320px"
              data-test="retrieve-query"
              @keyup.enter="doRetrieve"
            />
          </div>
          <div class="col-auto">
            <q-btn label="Search" color="primary" icon="search" @click="doRetrieve" data-test="retrieve-btn" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Ranked packs" icon="list_alt">
        <div v-if="error" class="text-negative" data-test="retrieve-error">{{ error }}</div>
        <div v-else-if="searched" data-test="retrieve-results">
          <div class="text-grey-7 q-mb-sm" data-test="retrieve-scorer">
            Scorer: {{ scorer ?? 'unknown' }} · {{ results.length }} match(es)
          </div>
          <q-table
            v-if="results.length > 0"
            :rows="results"
            :columns="columns"
            row-key="name"
            flat
            dense
            hide-bottom
            :pagination="{ rowsPerPage: 0, sortBy: 'score', descending: true }"
            data-test="retrieve-table"
          >
            <template #body-cell-lifecycleState="props">
              <q-td :props="props">
                <q-badge :color="lifecycleColor(props.value)" :label="props.value" />
              </q-td>
            </template>
          </q-table>
          <div v-else class="text-grey-7" data-test="retrieve-none">
            Nothing in this source matched that task.
          </div>
        </div>
        <EmptyState
          v-else
          icon="travel_explore"
          title="No search yet"
          hint="Pick a source, describe the task, then search to rank its packs here."
          data-test="retrieve-empty"
        />
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { QTableColumn } from 'quasar'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { retrieve } from '../api/client'
import type { RetrieveResult } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'
import { lifecycleColor } from '../improve/model'

const { source, sourceOptions } = useSourceSpec()
const query = ref('')
const results = ref<RetrieveResult[]>([])
const scorer = ref<string | null>(null)
const searched = ref(false)
const error = ref<string | null>(null)

const columns: QTableColumn<RetrieveResult>[] = [
  { name: 'name', label: 'Pack', field: 'name', align: 'left', sortable: true },
  {
    name: 'score',
    label: 'Score',
    field: 'score',
    align: 'right',
    sortable: true,
    format: (val) => Number(val).toFixed(4),
  },
  {
    name: 'outputType',
    label: 'Output',
    field: 'outputType',
    align: 'left',
    sortable: true,
    format: (val) => val ?? '—',
  },
  { name: 'lifecycleState', label: 'Lifecycle', field: 'lifecycleState', align: 'left', sortable: true },
]

async function doRetrieve() {
  if (source.value === '' || query.value.trim() === '') {
    return
  }
  error.value = null
  try {
    const res = await retrieve({ source: source.value, query: query.value })
    if (res.error) {
      error.value = res.error
    } else {
      results.value = res.results
      scorer.value = res.scorer
      searched.value = true
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

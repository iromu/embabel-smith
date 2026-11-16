<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Retrieve</div>
        <p class="text-grey-7">POC 12/18 — rank the library by a pluggable scorer, curation-aware.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input v-model="query" label="Task query" dense style="min-width: 320px" data-test="retrieve-query" />
          <q-input v-model.number="k" label="Top-k" type="number" dense style="width: 120px" data-test="retrieve-k" />
          <q-btn label="Retrieve" color="primary" icon="search" @click="doRetrieve" data-test="retrieve-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="loading" class="text-grey-7">ranking…</div>
        <div v-else-if="error" class="text-negative" data-test="retrieve-error">{{ error }}</div>
        <div v-else-if="result" data-test="retrieve-result">
          <q-badge color="primary" :label="`scorer: ${result.scorer}`" />
          <q-list dense class="q-mt-sm">
            <q-item v-for="r in result.results" :key="r.name" class="q-py-xs">
              <q-item-section>{{ r.name }}</q-item-section>
              <q-item-section side class="text-grey-7">{{ r.score.toFixed(3) }}</q-item-section>
              <q-item-section side>
                <q-badge :label="r.state" :color="stateColor(r.state)" />
              </q-item-section>
            </q-item>
          </q-list>
        </div>
        <div v-else class="text-grey-6">Enter a query to rank the library.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { retrieve } from '../api/client'
import type { RetrieveResponse } from '../api/types'

const query = ref('')
const k = ref(5)
const result = ref<RetrieveResponse | null>(null)
const error = ref<string | null>(null)
const loading = ref(false)

function stateColor(state: string): string {
  switch (state) {
    case 'ACTIVE':
      return 'positive'
    case 'DEGRADED':
      return 'warning'
    case 'RETIRED':
      return 'negative'
    default:
      return 'grey'
  }
}

async function doRetrieve() {
  if (!query.value.trim()) {
    return
  }
  loading.value = true
  error.value = null
  result.value = null
  try {
    result.value = await retrieve({ query: query.value.trim(), k: k.value })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}
</script>

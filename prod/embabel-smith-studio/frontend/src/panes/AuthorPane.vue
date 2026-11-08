<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Build</div>
        <p class="text-grey-7">Describe a skill and publish it to a source through the builder (compile → eval → security → lifecycle).</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-start">
          <q-input
            v-model="description"
            label="Describe the skill"
            dense
            style="min-width: 360px"
            data-test="build-description"
          />
          <q-input
            v-model="outputSource"
            label="Publish to (source)"
            dense
            style="width: 280px"
            data-test="build-output"
            :hint="sourceHint"
          />
          <q-btn label="Build" color="primary" icon="edit" @click="doBuild" data-test="build-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="build-no-endpoint">
          <q-badge color="warning" label="model not configured" />
          <div class="q-mt-sm text-grey-7">{{ error }}</div>
        </div>
        <div v-else-if="error && !report" class="text-negative" data-test="build-error">{{ error }}</div>
        <div v-else-if="report" data-test="build-result">
          <div class="row q-gutter-sm items-center">
            <q-badge :color="report.status === 'BUILT' ? 'positive' : 'negative'" :label="report.status" />
            <q-badge v-if="report.packName" color="secondary" :label="`pack: ${report.packName}`" />
            <q-badge v-if="report.stage && report.status === 'BLOCKED'" color="warning" :label="`blocked at ${report.stage}`" />
            <q-badge v-if="report.score !== null" color="primary" :label="`score: ${report.score.toFixed(3)}`" />
          </div>
          <div v-if="report.issues.length > 0" class="text-negative q-mt-sm" data-test="build-issues">
            <div v-for="(issue, i) in report.issues" :key="i">{{ issue }}</div>
          </div>
          <div v-if="report.scan" class="q-mt-sm">
            <q-badge :color="verdictColor(report.scan.verdict)" :label="`security: ${report.scan.verdict}`" />
            <span class="text-grey-7 q-ml-sm">{{ report.scan.summary }}</span>
          </div>
          <div v-if="report.lifecycle" class="q-mt-sm">
            <q-badge :color="lifecycleColor(report.lifecycle.state)" :label="`lifecycle: ${report.lifecycle.state}`" />
            <q-badge color="secondary" :label="`baseline: ${report.lifecycle.baselinePassRate.toFixed(3)}`" />
          </div>
          <div v-if="report.published" class="text-positive q-mt-sm">
            published: {{ report.published.location }}{{ report.published.pushed ? ' (pushed)' : '' }}
          </div>
        </div>
        <div v-else class="text-grey-6">Describe a skill and pick where to publish it, then build.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { build, listSources } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { BuildResponse } from '../api/types'
import { lifecycleColor } from '../improve/model'

const sources = ref<string[]>([])
const description = ref('')
const outputSource = ref('')
const report = ref<BuildResponse | null>(null)
const error = ref<string | null>(null)
const last = ref<BuildResponse | null>(null)

const sourceHint = computed(() => sources.value.join(', '))
const noEndpoint = computed(() => isModelNotConfigured(last.value))

function verdictColor(v: string): string {
  return v === 'SAFE' ? 'positive' : v === 'REVIEW' ? 'warning' : 'negative'
}

async function loadSources() {
  sources.value = (await listSources()).map((s) => s.uri)
  if (sources.value.length > 0 && outputSource.value === '') {
    outputSource.value = sources.value[0]
  }
}

async function doBuild() {
  if (description.value.trim() === '' || outputSource.value === '') {
    return
  }
  error.value = null
  report.value = null
  try {
    const res = await build({ description: description.value.trim(), outputSource: outputSource.value })
    last.value = res
    report.value = res
    if (res.error) {
      error.value = res.error
    }
  } catch (e) {
    last.value = null
    error.value = e instanceof Error ? e.message : String(e)
  }
}

void loadSources()
</script>

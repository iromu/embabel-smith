<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <PageHeader
        icon="history"
        title="Traces"
        description="A spec's recent runs, most recent first, with per-step drill-down."
      />

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-select
            v-model="source"
            label="Source"
            dense
            :options="sourceOptions"
            style="width: 320px"
            data-test="traces-source"
          />
          <q-select
            v-model="spec"
            label="Spec"
            dense
            :options="specOptions"
            style="width: 260px"
            data-test="traces-spec"
          />
          <q-btn label="Load" color="primary" icon="history" @click="loadTraces" data-test="traces-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="traces-error">{{ error }}</div>
        <div v-else-if="traceRows.length === 0" class="text-grey-6" data-test="traces-empty">
          No traces for this spec yet.
        </div>
        <q-table
          v-else
          :rows="traceRows"
          :columns="columns"
          row-key="runId"
          flat
          dense
          :pagination="{ rowsPerPage: 10, sortBy: 'timestamp', descending: true }"
          data-test="traces-table"
        >
          <template #body-cell-runId="props">
            <q-td :props="props">
              <span class="text-caption">{{ props.value.slice(0, 8) }}</span>
            </q-td>
          </template>
          <template #body-cell-timestamp="props">
            <q-td :props="props">
              <span class="text-caption">{{ formatTimestamp(props.value) }}</span>
            </q-td>
          </template>
          <template #body-cell-success="props">
            <q-td :props="props">
              <q-badge
                :color="props.value ? 'positive' : 'negative'"
                :label="runStatusLabel(props.row)"
              />
            </q-td>
          </template>
          <template #body-cell-error="props">
            <q-td :props="props">
              <span class="text-negative text-caption">
                {{ failureReason(props.row) ?? '' }}
              </span>
            </q-td>
          </template>
          <template #body-cell-steps="props">
            <q-td :props="props">
              <q-btn
                v-if="hasSteps(props.row)"
                flat
                dense
                round
                icon="expand_more"
                :aria-label="`show steps for ${props.value}`"
                data-test="traces-drilldown"
                @click="openSteps(props.row)"
              />
              <span v-else class="text-grey-5 text-caption">—</span>
            </q-td>
          </template>
        </q-table>
      </q-card-section>
    </q-card>

    <q-dialog v-model="stepsOpen">
      <q-card style="min-width: 480px; max-width: 90vw">
        <q-card-section>
          <div class="text-h6">Steps</div>
          <div v-if="selectedTrace" class="text-grey-7 text-caption">
            run {{ selectedTrace.runId.slice(0, 8) }} · {{ selectedTrace.goal ?? 'no goal' }}
          </div>
        </q-card-section>
        <q-separator />
        <q-card-section>
          <q-list dense v-if="selectedTrace">
            <q-item v-for="(s, i) in selectedTrace.steps" :key="i" class="q-py-xs">
              <q-item-section side>
                <q-badge :color="stepStatusColor(s.status)" :label="s.status" />
              </q-item-section>
              <q-item-section>
                <div class="text-weight-medium">{{ s.name }}</div>
                <div class="text-grey-7 text-caption">
                  {{ formatDuration(s.durationMs) }}
                  <template v-if="s.outputExcerpt"> · {{ s.outputExcerpt }}</template>
                </div>
              </q-item-section>
            </q-item>
          </q-list>
          <div v-else class="text-grey-6">No steps recorded.</div>
        </q-card-section>
        <q-separator />
        <q-card-section class="text-right">
          <q-btn flat label="Close" @click="stepsOpen = false" />
        </q-card-section>
      </q-card>
    </q-dialog>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import type { QTableColumn } from 'quasar'

import PageHeader from '../components/PageHeader.vue'
import { traces } from '../api/client'
import type { TraceInfo } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'
import {
  failureReason,
  formatDuration,
  hasSteps,
  runStatusLabel,
  stepStatusColor,
} from '../traces/model'

const { source, spec, sourceOptions, specOptions } = useSourceSpec()
const traceRows = ref<TraceInfo[]>([])
const error = ref<string | null>(null)
const stepsOpen = ref(false)
const selectedTrace = ref<TraceInfo | null>(null)

const columns: QTableColumn<TraceInfo>[] = [
  { name: 'runId', label: 'Run', field: 'runId', align: 'left' },
  { name: 'timestamp', label: 'Time', field: 'timestamp', align: 'left' },
  { name: 'success', label: 'Status', field: 'success', align: 'left' },
  { name: 'error', label: 'Failure', field: 'error', align: 'left' },
  { name: 'steps', label: '', field: 'runId', align: 'left' },
]

function formatTimestamp(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleString()
}

function openSteps(trace: TraceInfo) {
  selectedTrace.value = trace
  stepsOpen.value = true
}

async function loadTraces() {
  if (source.value === '' || spec.value === '') {
    return
  }
  error.value = null
  traceRows.value = []
  try {
    traceRows.value = await traces(spec.value)
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

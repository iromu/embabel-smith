<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <PageHeader
        icon="play_arrow"
        title="Run"
        description="Pick a source and a goal it exports, submit an input, and run it through the runtime."
      />

      <q-separator />

      <q-card-section>
        <div class="col q-gutter-y-sm">
          <div class="row q-gutter-sm items-start">
            <q-select
              v-model="source"
              label="Source"
              dense
              :options="sourceOptions"
              style="width: 320px"
              data-test="run-source"
            />
            <q-select
              v-model="goal"
              label="Goal"
              dense
              :options="goalOptions"
              :loading="loadingGoals"
              style="width: 260px"
              data-test="run-goal"
            />
          </div>
          <div class="row q-gutter-sm items-start">
            <q-input v-model="input" label="Input" dense style="flex: 1; min-width: 220px" data-test="run-input" />
            <q-input v-model="planner" label="Planner (optional)" dense style="width: 180px" data-test="run-planner" />
            <q-select
              v-model="tier"
              label="Trust tier"
              dense
              :options="tiers"
              style="width: 150px"
              data-test="run-tier"
            />
            <q-btn label="Run" color="primary" icon="play_arrow" @click="doRun" data-test="run-btn" />
          </div>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="run-no-endpoint">
          <q-badge color="warning" label="model not configured" />
          <div class="q-mt-sm text-grey-7">{{ error }}</div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="run-error">{{ error }}</div>
        <div v-else-if="output !== null" data-test="run-output">
          <div class="text-subtitle2">Goal output</div>
          <q-card flat bordered class="q-pa-sm bg-grey-1">
            <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ output }}</pre>
          </q-card>
        </div>
        <div v-else class="text-grey-6">Pick a source and a goal, enter an input, then run it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import PageHeader from '../components/PageHeader.vue'
import { listGoals, run } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { RunResponse } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'

const { source, sourceOptions } = useSourceSpec()
const goal = ref('')
const goals = ref<string[]>([])
const goalOptions = computed(() => goals.value)
const loadingGoals = ref(false)
const input = ref('')
const planner = ref('')
const tier = ref('STANDARD')
const output = ref<string | null>(null)
const error = ref<string | null>(null)
const last = ref<RunResponse | null>(null)

const tiers = ['STANDARD', 'HIGH']
const noEndpoint = computed(() => isModelNotConfigured(last.value))

// The run is addressed by goal, not pack name. Load the selected source's exported goals and
// auto-select it when the source exports exactly one (mirroring the runtime's single-goal default).
watch(
  source,
  async (s) => {
    goal.value = ''
    goals.value = []
    if (!s) return
    loadingGoals.value = true
    try {
      goals.value = await listGoals(s)
      if (goals.value.length === 1) goal.value = goals.value[0]
    } catch {
      goals.value = []
    } finally {
      loadingGoals.value = false
    }
  },
)

async function doRun() {
  if (source.value === '' || goal.value === '') {
    return
  }
  error.value = null
  output.value = null
  try {
    const res = await run({
      source: source.value,
      goal: goal.value,
      input: input.value,
      planner: planner.value.trim() || undefined,
      trustTier: tier.value,
    })
    last.value = res
    if (res.error) {
      error.value = res.error
    } else {
      output.value = res.output
    }
  } catch (e) {
    last.value = null
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

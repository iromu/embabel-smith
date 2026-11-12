<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="play_arrow"
        title="Run"
        description="Pick a source and a goal it exports, submit an input, and run it through the runtime."
      />

      <SectionCard title="Run a goal" icon="play_arrow">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 240px"
              data-test="run-source"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="goal"
              label="Goal"
              outlined
              :options="goalOptions"
              :loading="loadingGoals"
              style="width: 240px"
              data-test="run-goal"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="tier"
              label="Trust tier"
              outlined
              :options="tiers"
              style="width: 150px"
              data-test="run-tier"
            />
          </div>
          <div class="col" style="min-width: 200px">
            <q-input v-model="input" label="Input" outlined data-test="run-input" />
          </div>
          <div class="col-auto">
            <q-btn label="Run" color="primary" icon="play_arrow" @click="doRun" data-test="run-btn" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Result" icon="terminal">
        <div v-if="noEndpoint" class="text-warning" data-test="run-no-endpoint">
          <q-badge color="warning" label="model not configured" />
          <div class="q-mt-sm text-grey-7">{{ error }}</div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="run-error">{{ error }}</div>
        <div v-else-if="output !== null" data-test="run-output">
          <div class="section-label">Goal output</div>
          <q-card flat bordered class="q-pa-sm bg-grey-1">
            <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ output }}</pre>
          </q-card>
        </div>
        <EmptyState
          v-else
          icon="play_circle_outline"
          title="No run yet"
          hint="Pick a source and a goal, enter an input, then run it to see the goal output here."
          data-test="run-empty"
        />
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
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

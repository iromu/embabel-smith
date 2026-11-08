<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Run</div>
        <p class="text-grey-7">Pick a source and a skill, submit an input, and run the goal through the runtime.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-start">
          <q-input
            v-model="source"
            label="Source"
            dense
            style="width: 280px"
            data-test="run-source"
            :hint="sourceHint"
          />
          <q-input
            v-model="goal"
            label="Skill / goal"
            dense
            style="width: 240px"
            data-test="run-goal"
            :hint="skillHint"
          />
          <q-input v-model="input" label="Input" dense style="min-width: 260px" data-test="run-input" />
          <q-input v-model="planner" label="Planner (optional)" dense style="width: 160px" data-test="run-planner" />
          <q-input
            v-model="tier"
            label="Trust tier"
            dense
            style="width: 140px"
            data-test="run-tier"
            hint="STANDARD / HIGH"
          />
          <q-btn label="Run" color="primary" icon="play_arrow" @click="doRun" data-test="run-btn" />
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
        <div v-else class="text-grey-6">Pick a source, a skill, and an input, then run it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { run } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { RunResponse } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skills, skill, sources, loading } = useSourceSkill()
const goal = skill
const input = ref('')
const planner = ref('')
const tier = ref('STANDARD')
const output = ref<string | null>(null)
const error = ref<string | null>(null)
const last = ref<RunResponse | null>(null)

const sourceHint = computed(() => sources.value.map((s) => s.uri).join(', '))
const skillHint = computed(() => (loading.value ? 'loading…' : skills.value.map((s) => s.name).join(', ')))
const noEndpoint = computed(() => isModelNotConfigured(last.value))

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

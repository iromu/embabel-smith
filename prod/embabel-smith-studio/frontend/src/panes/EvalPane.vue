<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Eval</div>
        <p class="text-grey-7">Score a skill over its tasks and refine the prompt from failures.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="source"
            label="Source"
            dense
            style="width: 280px"
            data-test="eval-source"
            :hint="sourceHint"
          />
          <q-input
            v-model="skill"
            label="Skill"
            dense
            style="width: 240px"
            data-test="eval-skill"
            :hint="skillHint"
          />
          <q-btn label="Evaluate" color="primary" icon="grade" @click="doEval" data-test="eval-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="eval-no-endpoint">
          <q-badge color="warning" label="model not configured" />
          <div class="q-mt-sm text-grey-7">{{ error }}</div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="eval-error">{{ error }}</div>
        <div v-else-if="result" data-test="eval-result">
          <div class="row q-gutter-sm items-center">
            <q-badge :color="result.passed ? 'positive' : 'negative'" :label="`score: ${result.score?.toFixed(3) ?? '?'}`" />
            <q-badge v-if="result.refinementCount > 0" color="primary" :label="`refined ×${result.refinementCount}`" />
          </div>
          <div v-if="result.failingTasks.length > 0" class="text-negative q-mt-sm">
            failing: {{ result.failingTasks.join(', ') }}
          </div>
          <div v-if="result.revisedPrompt" class="q-mt-sm">
            <div class="text-subtitle2">Revised prompt</div>
            <q-card flat bordered class="q-pa-sm bg-grey-1">
              <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ result.revisedPrompt }}</pre>
            </q-card>
          </div>
        </div>
        <div v-else class="text-grey-6">Pick a source and a skill, then evaluate it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { evaluate } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { EvalResponse } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skills, skill, sources, loading } = useSourceSkill()
const result = ref<EvalResponse | null>(null)
const error = ref<string | null>(null)
const last = ref<EvalResponse | null>(null)

const sourceHint = computed(() => sources.value.map((s) => s.uri).join(', '))
const skillHint = computed(() => (loading.value ? 'loading…' : skills.value.map((s) => s.name).join(', ')))
const noEndpoint = computed(() => isModelNotConfigured(last.value))

async function doEval() {
  if (source.value === '' || skill.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await evaluate({ source: source.value, skill: skill.value })
    last.value = res
    if (res.error) {
      error.value = res.error
    } else {
      result.value = res
    }
  } catch (e) {
    last.value = null
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

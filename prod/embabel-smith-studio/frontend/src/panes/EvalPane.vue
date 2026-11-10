<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <PageHeader
        icon="grade"
        title="Eval"
        description="Score a skill over its tasks and refine the prompt from failures."
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
            data-test="eval-source"
          />
          <q-select
            v-model="skill"
            label="Skill"
            dense
            :options="skillOptions"
            style="width: 260px"
            data-test="eval-skill"
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

import PageHeader from '../components/PageHeader.vue'
import { evaluate } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { EvalResponse } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skill, sourceOptions, skillOptions } = useSourceSkill()
const result = ref<EvalResponse | null>(null)
const error = ref<string | null>(null)
const last = ref<EvalResponse | null>(null)

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

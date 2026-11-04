<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Eval</div>
        <p class="text-grey-7">POC 9 — score a skill over tasks, and refine the prompt from failures.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="skillName"
            label="Skill to evaluate"
            dense
            style="min-width: 320px"
            data-test="eval-skill"
            :hint="skillNames.join(', ') || 'loading…'"
          />
          <q-btn label="Evaluate" color="primary" icon="grade" @click="doEval" data-test="eval-btn" />
        </div>
        <div class="text-grey-6 q-mt-sm">
          Uses a small default task set (override in the backend when needed).
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="eval-no-endpoint">
          <q-badge color="warning" label="endpoint not configured" />
          <div class="q-mt-sm text-grey-7">
            Set the model endpoint (OPENAI_CUSTOM_BASE_URL / _API_KEY / _MODELS) to evaluate a skill.
          </div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="eval-error">{{ error }}</div>
        <div v-else-if="result" data-test="eval-result">
          <div class="row q-gutter-sm items-center">
            <q-badge color="secondary" :label="`before: ${result.beforeScore.toFixed(2)}`" />
            <q-badge
              :color="result.improved ? 'positive' : 'grey'"
              :label="`after: ${result.afterScore.toFixed(2)}`"
            />
            <q-badge v-if="result.refined" color="primary" label="refined" />
          </div>
          <div v-if="result.stepName" class="text-grey-7 q-mt-sm">step refined: {{ result.stepName }}</div>
          <div v-if="result.revisedPrompt" class="q-mt-sm">
            <div class="text-subtitle2">Revised prompt</div>
            <q-card flat bordered class="q-pa-sm bg-grey-1">
              <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ result.revisedPrompt }}</pre>
            </q-card>
          </div>
          <div v-if="result.failingInputs.length > 0" class="text-negative q-mt-sm">
            failing: {{ result.failingInputs.join(', ') }}
          </div>
        </div>
        <div v-else class="text-grey-6">Pick a skill and evaluate it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { evaluate, isEndpointNotConfigured, listSkills } from '../api/client'
import type { EvalResponse } from '../api/types'

const skillName = ref('')
const skills = ref<string[]>([])
const result = ref<EvalResponse | null>(null)
const error = ref<string | null>(null)

const skillNames = computed(() => skills.value)
const noEndpoint = computed(() => isEndpointNotConfigured(error.value))

async function doEval() {
  if (!skillName.value.trim()) {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await evaluate({ skillName: skillName.value.trim() })
    if (res.error) {
      error.value = res.error
    } else {
      result.value = res
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(async () => {
  skills.value = (await listSkills()).map((s) => s.name)
})
</script>

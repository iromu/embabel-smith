<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Lifecycle</div>
        <p class="text-grey-7">POC 18 — re-evaluate a skill and move it through the lifecycle states.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="skillName"
            label="Skill to re-evaluate"
            dense
            style="min-width: 320px"
            data-test="lifecycle-skill"
            :hint="skillNames.join(', ') || 'loading…'"
          />
          <q-btn label="Re-evaluate" color="primary" icon="cycle" @click="doCurate" data-test="lifecycle-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="lifecycle-no-endpoint">
          <q-badge color="warning" label="endpoint not configured" />
          <div class="q-mt-sm text-grey-7">
            Set the model endpoint (OPENAI_CUSTOM_BASE_URL / _API_KEY / _MODELS) to re-evaluate a skill.
          </div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="lifecycle-error">{{ error }}</div>
        <div v-else-if="result" data-test="lifecycle-result">
          <div class="row q-gutter-sm items-center">
            <q-badge color="grey" :label="`before: ${result.before ?? '?'}`" />
            <q-icon name="arrow_forward" color="grey" />
            <q-badge
              :color="result.after === 'RETIRED' ? 'negative' : result.after === 'DEGRADED' ? 'warning' : 'positive'"
              :label="`after: ${result.after ?? '?'}`"
            />
            <q-badge v-if="result.contentDrift" color="warning" label="content drift" />
          </div>
          <div class="row q-gutter-sm items-center q-mt-sm">
            <q-badge color="secondary" :label="`baseline: ${result.baselineScore.toFixed(2)}`" />
            <q-badge color="primary" :label="`measured: ${result.currentScore.toFixed(2)}`" />
            <q-badge
              :color="result.surfaced ? 'positive' : 'negative'"
              :label="result.surfaced ? 'surfaced' : 'out of library'"
            />
          </div>
          <div v-if="result.verdict" class="text-grey-7 q-mt-sm">{{ result.verdict }}</div>
        </div>
        <div v-else class="text-grey-6">Pick a skill and re-evaluate it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { curate, isEndpointNotConfigured, listSkills } from '../api/client'
import type { CurateResponse } from '../api/types'

const skillName = ref('')
const skills = ref<string[]>([])
const result = ref<CurateResponse | null>(null)
const error = ref<string | null>(null)

const skillNames = computed(() => skills.value)
const noEndpoint = computed(() => isEndpointNotConfigured(error.value))

async function doCurate() {
  if (!skillName.value.trim()) {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await curate({ skillName: skillName.value.trim() })
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

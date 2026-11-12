<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="grade"
        title="Eval"
        description="Score a spec over its tasks and refine the prompt from failures."
      />

      <SectionCard title="Evaluate a spec" icon="grade">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 240px"
              data-test="eval-source"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="spec"
              label="Spec"
              outlined
              :options="specOptions"
              style="width: 240px"
              data-test="eval-spec"
            />
          </div>
          <div class="col-auto">
            <q-btn label="Evaluate" color="primary" icon="grade" @click="doEval" data-test="eval-btn" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Result" icon="terminal">
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
            <div class="section-label">Revised prompt</div>
            <q-card flat bordered class="q-pa-sm bg-grey-1">
              <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ result.revisedPrompt }}</pre>
            </q-card>
          </div>
        </div>
        <EmptyState
          v-else
          icon="query_stats"
          title="No evaluation yet"
          hint="Pick a source and a spec, then evaluate it to see the score and any refined prompt here."
          data-test="eval-empty"
        />
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { evaluate } from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type { EvalResponse } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'

const { source, spec, sourceOptions, specOptions } = useSourceSpec()
const result = ref<EvalResponse | null>(null)
const error = ref<string | null>(null)
const last = ref<EvalResponse | null>(null)

const noEndpoint = computed(() => isModelNotConfigured(last.value))

async function doEval() {
  if (source.value === '' || spec.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await evaluate({ source: source.value, spec: spec.value })
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

<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Run</div>
        <p class="text-grey-7">POC 1 — pick a spec, run the GOAP plan, and see the goal output.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-start">
          <q-input
            v-model="specName"
            label="Spec to run"
            dense
            style="min-width: 300px"
            data-test="run-spec"
            :hint="specNames.join(', ') || 'loading…'"
          />
          <q-input v-model="input" label="Input" dense style="min-width: 300px" data-test="run-input" />
          <q-btn label="Run" color="primary" icon="play_arrow" @click="doRun" data-test="run-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="run-no-endpoint">
          <q-badge color="warning" label="endpoint not configured" />
          <div class="q-mt-sm text-grey-7">
            Set the model endpoint (OPENAI_CUSTOM_BASE_URL / _API_KEY / _MODELS) to run a spec.
          </div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="run-error">{{ error }}</div>
        <div v-else-if="output !== null" data-test="run-output">
          <div class="text-subtitle2">Goal output</div>
          <q-card flat bordered class="q-pa-sm bg-grey-1">
            <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ output }}</pre>
          </q-card>
        </div>
        <div v-else class="text-grey-6">Pick a spec and an input, then run it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { isEndpointNotConfigured, listSpecs, run } from '../api/client'

const specName = ref('')
const input = ref('')
const specs = ref<string[]>([])
const output = ref<string | null>(null)
const error = ref<string | null>(null)

const specNames = computed(() => specs.value)
const noEndpoint = computed(() => isEndpointNotConfigured(error.value))

async function doRun() {
  if (!specName.value.trim()) {
    return
  }
  error.value = null
  output.value = null
  try {
    const res = await run({ specName: specName.value.trim(), input: input.value })
    if (res.error) {
      error.value = res.error
    } else {
      output.value = res.output
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(async () => {
  specs.value = (await listSpecs()).map((s) => s.name)
})
</script>

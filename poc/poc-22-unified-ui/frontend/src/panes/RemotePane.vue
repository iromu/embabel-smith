<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Remote</div>
        <p class="text-grey-7">POC 16/13 — invoke a remote action (RestAction sidecar) or MCP tool over HTTP.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input v-model="input" label="Input to the remote action" dense style="min-width: 360px" data-test="remote-input" />
          <q-btn label="Invoke" color="primary" icon="cloud" @click="doInvoke" data-test="remote-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="remote-error">{{ error }}</div>
        <div v-else-if="result && !result.running" class="text-warning" data-test="remote-not-running">
          <q-badge color="warning" label="backing process not running" />
          <div class="q-mt-sm text-grey-7">
            The sidecar is unreachable. Start it (POC 16) and invoke again.
          </div>
          <div v-if="result.mcpUrl" class="text-grey-7">MCP transport: {{ result.mcpUrl }}</div>
        </div>
        <div v-else-if="result && result.running" data-test="remote-result">
          <div class="row q-gutter-sm items-center">
            <q-badge color="positive" :label="`action: ${result.action ?? '?'}`" />
            <q-badge v-if="result.mcpUrl" color="secondary" :label="`mcp: ${result.mcpUrl}`" />
          </div>
          <div class="text-subtitle2 q-mt-md">Result</div>
          <q-card flat bordered class="q-pa-sm bg-grey-1 q-mt-sm">
            <pre class="text-body2" style="white-space: pre-wrap; margin: 0">{{ result.result ?? '' }}</pre>
          </q-card>
        </div>
        <div v-else class="text-grey-6">Invoke the remote action on the backing sidecar.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import { invokeRemote } from '../api/client'
import type { RemoteResponse } from '../api/types'

const input = ref('')
const result = ref<RemoteResponse | null>(null)
const error = ref<string | null>(null)

async function doInvoke() {
  error.value = null
  result.value = null
  try {
    result.value = await invokeRemote({ input: input.value })
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Scale</div>
        <p class="text-grey-7">
          POC 12 — precision@1 across a growing, same-typed library (shadowing pressure).
        </p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="loading" class="text-grey-7">measuring…</div>
        <div v-else-if="error" class="text-negative" data-test="scale-error">{{ error }}</div>
        <div v-else-if="result" data-test="scale-result">
          <div class="row q-gutter-sm items-center">
            <q-badge color="primary" :label="`scorer: ${result.scorer}`" />
            <q-badge color="secondary" :label="`core P@1: ${result.coreP1.toFixed(3)} (${result.coreSize})`" />
            <q-badge color="warning" :label="`full P@1: ${result.fullP1.toFixed(3)} (${result.fullSize})`" />
          </div>

          <div class="text-subtitle2 q-mt-md">P@1 as the library grows</div>
          <div class="q-mt-sm">
            <div v-for="(p, i) in result.curve" :key="i" class="row items-center q-py-xs">
              <div class="text-grey-7" style="width: 60px">{{ p.librarySize }}</div>
              <div class="scale-bar" style="height: 14px; background: #1976d2">
                <div
                  class="scale-bar-fill"
                  style="height: 100%; background: #42b983"
                  :style="{ width: `${p.precisionAt1 * 100}%` }"
                ></div>
              </div>
              <div class="text-grey-7" style="width: 56px">{{ p.precisionAt1.toFixed(3) }}</div>
            </div>
          </div>
        </div>
        <div v-else class="text-grey-6">Loading the measurement…</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import { scale } from '../api/client'
import type { ScaleResponse } from '../api/types'

const result = ref<ScaleResponse | null>(null)
const error = ref<string | null>(null)
const loading = ref(true)

onMounted(async () => {
  try {
    result.value = await scale()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.scale-bar {
  flex: 1;
  border-radius: 3px;
  overflow: hidden;
}
</style>

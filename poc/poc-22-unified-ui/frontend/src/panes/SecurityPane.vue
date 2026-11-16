<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Security</div>
        <p class="text-grey-7">POC 21 — scan an agent spec at install time; pure static analysis, offline.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="specName"
            label="Spec to scan"
            dense
            style="min-width: 320px"
            data-test="security-spec"
            :hint="specNames.join(', ') || 'loading…'"
          />
          <q-btn label="Scan" color="primary" icon="security" @click="doScan" data-test="security-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="security-error">{{ error }}</div>
        <div v-else-if="result" data-test="security-result">
          <div class="row q-gutter-sm items-center">
            <q-badge :color="verdictColor(result.verdict)" :label="result.verdict ?? ''" />
            <q-badge v-if="result.recommendedTier" color="secondary" :label="`min tier: ${result.recommendedTier}`" />
          </div>
          <div v-if="result.summary" class="text-grey-7 q-mt-sm">{{ result.summary }}</div>
          <q-list dense v-if="result.findings.length > 0" class="q-mt-sm">
            <q-item v-for="(f, i) in result.findings" :key="i" class="q-py-xs">
              <q-item-section side>
                <q-badge :color="severityColor(f.severity)" :label="f.severity" />
              </q-item-section>
              <q-item-section>
                <div class="text-weight-medium">{{ f.category }}</div>
                <div class="text-grey-7">{{ f.message }}</div>
              </q-item-section>
            </q-item>
          </q-list>
          <div v-else class="text-positive q-mt-sm">No findings.</div>
        </div>
        <div v-else class="text-grey-6">Pick a spec and scan it.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { listSpecs, scan } from '../api/client'
import type { SecurityResponse } from '../api/types'

const specName = ref('')
const specs = ref<string[]>([])
const result = ref<SecurityResponse | null>(null)
const error = ref<string | null>(null)

const specNames = computed(() => specs.value)

function verdictColor(v: string | null): string {
  switch (v) {
    case 'SAFE':
      return 'positive'
    case 'REVIEW':
      return 'warning'
    case 'BLOCK':
      return 'negative'
    default:
      return 'grey'
  }
}

function severityColor(s: string): string {
  return s === 'HIGH' || s === 'CRITICAL' ? 'negative' : s === 'MEDIUM' ? 'warning' : 'grey'
}

async function doScan() {
  if (!specName.value.trim()) {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await scan({ specName: specName.value.trim() })
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
  specs.value = (await listSpecs()).map((s) => s.name)
})
</script>

<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="security"
        title="Security"
        description="Scan a spec-pack at install time; pure static analysis, offline."
      />

      <SectionCard title="Scan a pack" icon="security">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 240px"
              data-test="security-source"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="spec"
              label="Spec"
              outlined
              :options="specOptions"
              style="width: 240px"
              data-test="security-spec"
            />
          </div>
          <div class="col-auto">
            <q-btn label="Scan" color="primary" icon="security" @click="doScan" data-test="security-btn" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Findings" icon="list_alt">
        <div v-if="error" class="text-negative" data-test="security-error">{{ error }}</div>
        <div v-else-if="result" data-test="security-result">
          <div class="row q-gutter-sm items-center">
            <q-badge :color="verdictColor(result.verdict)" :label="result.verdict" />
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
        <EmptyState
          v-else
          icon="shield_outlined"
          title="No scan yet"
          hint="Pick a source and a spec, then scan its pack to see any findings here."
          data-test="security-empty"
        />
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { scan } from '../api/client'
import type { ScanInfo } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'

const { source, spec, sourceOptions, specOptions } = useSourceSpec()
const result = ref<ScanInfo | null>(null)
const error = ref<string | null>(null)

function verdictColor(v: string): string {
  return v === 'SAFE' ? 'positive' : v === 'REVIEW' ? 'warning' : 'negative'
}

function severityColor(s: string): string {
  return s === 'CRITICAL' || s === 'HIGH' ? 'negative' : s === 'WARN' || s === 'MEDIUM' ? 'warning' : 'grey'
}

async function doScan() {
  if (source.value === '' || spec.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await scan({ source: source.value, spec: spec.value })
    if (res.error) {
      error.value = res.error
    } else {
      result.value = res.scan
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

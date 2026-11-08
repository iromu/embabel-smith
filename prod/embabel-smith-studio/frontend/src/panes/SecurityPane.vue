<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Security</div>
        <p class="text-grey-7">Scan a skill-pack at install time; pure static analysis, offline.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="source"
            label="Source"
            dense
            style="width: 280px"
            data-test="security-source"
            :hint="sourceHint"
          />
          <q-input
            v-model="skill"
            label="Skill"
            dense
            style="width: 240px"
            data-test="security-skill"
            :hint="skillHint"
          />
          <q-btn label="Scan" color="primary" icon="security" @click="doScan" data-test="security-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
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
        <div v-else class="text-grey-6">Pick a source and a skill, then scan its pack.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { scan } from '../api/client'
import type { ScanInfo } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skills, skill, sources, loading } = useSourceSkill()
const result = ref<ScanInfo | null>(null)
const error = ref<string | null>(null)

const sourceHint = computed(() => sources.value.map((s) => s.uri).join(', '))
const skillHint = computed(() => (loading.value ? 'loading…' : skills.value.map((s) => s.name).join(', ')))

function verdictColor(v: string): string {
  return v === 'SAFE' ? 'positive' : v === 'REVIEW' ? 'warning' : 'negative'
}

function severityColor(s: string): string {
  return s === 'CRITICAL' || s === 'HIGH' ? 'negative' : s === 'WARN' || s === 'MEDIUM' ? 'warning' : 'grey'
}

async function doScan() {
  if (source.value === '' || skill.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await scan({ source: source.value, skill: skill.value })
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

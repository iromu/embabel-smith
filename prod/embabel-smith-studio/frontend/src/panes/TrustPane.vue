<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Trust</div>
        <p class="text-grey-7">Check whether a skill's declared tool-groups are allowed under a trust tier.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="source"
            label="Source"
            dense
            style="width: 280px"
            data-test="trust-source"
            :hint="sourceHint"
          />
          <q-input
            v-model="skill"
            label="Skill"
            dense
            style="width: 240px"
            data-test="trust-skill"
            :hint="skillHint"
          />
          <q-input
            v-model="tier"
            label="Tier"
            dense
            style="width: 160px"
            data-test="trust-tier"
            hint="STANDARD / HIGH"
          />
          <q-btn label="Check" color="primary" icon="verified_user" @click="doCheck" data-test="trust-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="trust-error">{{ error }}</div>
        <div v-else-if="result" data-test="trust-result">
          <q-badge :color="result.allowed ? 'positive' : 'negative'" :label="result.allowed ? 'ALLOWED' : 'DENIED'" />
          <div v-if="result.tier" class="text-grey-7 q-mt-sm">tier: {{ result.tier }}</div>
          <div v-if="!result.allowed && result.disallowedGroups.length > 0" class="text-negative q-mt-sm">
            not permitted: {{ result.disallowedGroups.join(', ') }}
          </div>
          <div v-if="result.declaredToolGroups.length > 0" class="text-grey-7">
            declares: {{ result.declaredToolGroups.join(', ') }}
          </div>
        </div>
        <div v-else class="text-grey-6">Pick a source, a skill and a trust tier, then check.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { checkTrust } from '../api/client'
import type { TrustResponse } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skills, skill, sources, loading } = useSourceSkill()
const tier = ref('STANDARD')
const result = ref<TrustResponse | null>(null)
const error = ref<string | null>(null)

const sourceHint = computed(() => sources.value.map((s) => s.uri).join(', '))
const skillHint = computed(() => (loading.value ? 'loading…' : skills.value.map((s) => s.name).join(', ')))

async function doCheck() {
  if (source.value === '' || skill.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await checkTrust({ source: source.value, skill: skill.value, tier: tier.value })
    if (res.error) {
      error.value = res.error
    } else {
      result.value = res
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

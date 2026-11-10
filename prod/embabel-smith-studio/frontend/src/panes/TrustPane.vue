<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <PageHeader
        icon="verified_user"
        title="Trust"
        description="Check whether a skill's declared tool-groups are allowed under a trust tier."
      />

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-select
            v-model="source"
            label="Source"
            dense
            :options="sourceOptions"
            style="width: 320px"
            data-test="trust-source"
          />
          <q-select
            v-model="skill"
            label="Skill"
            dense
            :options="skillOptions"
            style="width: 260px"
            data-test="trust-skill"
          />
          <q-select
            v-model="tier"
            label="Tier"
            dense
            :options="tiers"
            style="width: 160px"
            data-test="trust-tier"
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
import { ref } from 'vue'

import PageHeader from '../components/PageHeader.vue'
import { checkTrust } from '../api/client'
import type { TrustResponse } from '../api/types'
import { useSourceSkill } from '../composables/useSourceSkill'

const { source, skill, sourceOptions, skillOptions } = useSourceSkill()
const tier = ref('STANDARD')
const tiers = ['STANDARD', 'HIGH']
const result = ref<TrustResponse | null>(null)
const error = ref<string | null>(null)

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

<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Trust</div>
        <p class="text-grey-7">POC 10 — check a spec's tool groups against a trust-tier allowlist.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="specName"
            label="Spec to check"
            dense
            style="min-width: 300px"
            data-test="trust-spec"
            :hint="specNames.join(', ') || 'loading…'"
          />
          <q-select
            v-model="tier"
            :options="tiers"
            emit-value
            map-options
            label="Trust tier"
            dense
            style="width: 200px"
            data-test="trust-tier"
          />
          <q-btn label="Check" color="primary" icon="verified_user" @click="doCheck" data-test="trust-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="trust-error">{{ error }}</div>
        <div v-else-if="result" data-test="trust-result">
          <q-badge :color="result.allowed ? 'positive' : 'negative'" :label="result.allowed ? 'allowed' : 'denied'">
          </q-badge>
          <div v-if="result.tier" class="text-grey-7 q-mt-sm">tier: {{ result.tier }}</div>
          <div v-if="result.disallowedGroups.length > 0" class="text-negative q-mt-sm">
            not permitted: {{ result.disallowedGroups.join(', ') }}
          </div>
          <div v-if="result.declaredToolGroups.length > 0" class="text-grey-7">
            declares: {{ result.declaredToolGroups.join(', ') }}
          </div>
        </div>
        <div v-else class="text-grey-6">Pick a spec and a tier, then check the allowlist.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { checkTrust, listSpecs } from '../api/client'
import type { TrustResponse } from '../api/types'

const TIERS = ['T1_COMMUNITY', 'T2_INTERNAL', 'T3_PARTNER', 'T4_PLATFORM']

const specName = ref('')
const tier = ref('T3_PARTNER')
const specs = ref<string[]>([])
const result = ref<TrustResponse | null>(null)
const error = ref<string | null>(null)

const specNames = computed(() => specs.value)
const tiers = TIERS

async function doCheck() {
  if (!specName.value.trim()) {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await checkTrust({ specName: specName.value.trim(), tier: tier.value })
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

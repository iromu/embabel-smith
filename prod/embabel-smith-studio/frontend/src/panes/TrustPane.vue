<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="verified_user"
        title="Trust"
        description="Check whether a spec's declared tool-groups are allowed under a trust tier."
      />

      <SectionCard title="Check a spec" icon="verified_user">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 220px"
              data-test="trust-source"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="spec"
              label="Spec"
              outlined
              :options="specOptions"
              style="width: 220px"
              data-test="trust-spec"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="tier"
              label="Tier"
              outlined
              :options="tiers"
              style="width: 150px"
              data-test="trust-tier"
            />
          </div>
          <div class="col-auto">
            <q-btn label="Check" color="primary" icon="verified_user" @click="doCheck" data-test="trust-btn" />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Verdict" icon="gavel">
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
        <EmptyState
          v-else
          icon="policy"
          title="No check yet"
          hint="Pick a source, a spec and a trust tier, then check to see the verdict here."
          data-test="trust-empty"
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
import { checkTrust } from '../api/client'
import type { TrustResponse } from '../api/types'
import { useSourceSpec } from '../composables/useSourceSpec'

const { source, spec, sourceOptions, specOptions } = useSourceSpec()
const tier = ref('STANDARD')
const tiers = ['STANDARD', 'HIGH']
const result = ref<TrustResponse | null>(null)
const error = ref<string | null>(null)

async function doCheck() {
  if (source.value === '' || spec.value === '') {
    return
  }
  error.value = null
  result.value = null
  try {
    const res = await checkTrust({ source: source.value, spec: spec.value, tier: tier.value })
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

<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="cycle"
        title="Lifecycle"
        description="Each pack's lifecycle state, baseline pass-rate, and content-hash drift status."
      />

      <SectionCard title="Packs" icon="inventory_2">
        <template #actions>
          <div class="row items-center q-gutter-sm">
            <q-select
              v-model="source"
              label="Source"
              outlined
              :options="sourceOptions"
              style="width: 240px"
              data-test="lifecycle-source"
            />
            <q-btn label="Refresh" outline icon="refresh" @click="loadSpecs" data-test="lifecycle-refresh" />
          </div>
        </template>

        <div v-if="error" class="text-negative" data-test="lifecycle-error">{{ error }}</div>
        <div v-else-if="loading" class="text-grey-6">loading…</div>
        <EmptyState
          v-else-if="specs.length === 0"
          icon="inventory_2"
          title="No packs in this source"
          hint="Pick a source that contains built packs to see their lifecycle state here."
          data-test="lifecycle-empty"
        />
        <q-list dense v-else data-test="lifecycle-list">
          <q-item v-for="pack in specs" :key="pack.name" class="q-py-sm">
            <q-item-section>
              <div class="text-weight-medium">{{ pack.name }}</div>
              <div class="text-grey-7 text-caption">hash {{ shortHash(pack.contentHash) }}</div>
            </q-item-section>
            <q-item-section side>
              <div class="row q-gutter-x-sm items-center">
                <q-badge
                  :color="lifecycleColor(pack.lifecycleState)"
                  :label="pack.lifecycleState ?? 'no baseline'"
                />
                <q-badge
                  v-if="pack.baselinePassRate !== null"
                  color="secondary"
                  :label="`baseline ${pack.baselinePassRate.toFixed(3)}`"
                />
                <q-badge v-if="pack.drift" color="warning" label="drift" />
              </div>
            </q-item-section>
          </q-item>
        </q-list>
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { useSourceSpec } from '../composables/useSourceSpec'
import { lifecycleColor } from '../improve/model'

const { source, specs, sourceOptions, loading, loadSpecs } = useSourceSpec()
const error = ref<string | null>(null)

function shortHash(hash: string): string {
  return hash.length <= 12 ? hash : hash.slice(0, 12)
}
</script>

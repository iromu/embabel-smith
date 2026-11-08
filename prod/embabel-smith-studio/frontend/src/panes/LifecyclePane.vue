<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Lifecycle</div>
        <p class="text-grey-7">Each pack's lifecycle state, baseline pass-rate, and content-hash drift status.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="source"
            label="Source"
            dense
            style="width: 320px"
            data-test="lifecycle-source"
            :hint="sourceHint"
          />
          <q-btn label="Refresh" color="primary" icon="refresh" @click="loadSkills" data-test="lifecycle-refresh" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="error" class="text-negative" data-test="lifecycle-error">{{ error }}</div>
        <div v-else-if="loading" class="text-grey-6">loading…</div>
        <div v-else-if="skills.length === 0" class="text-grey-6" data-test="lifecycle-empty">
          No packs in this source.
        </div>
        <q-list dense v-else data-test="lifecycle-list">
          <q-item v-for="pack in skills" :key="pack.name" class="q-py-sm">
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
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { useSourceSkill } from '../composables/useSourceSkill'
import { lifecycleColor } from '../improve/model'

const { source, skills, sources, loading, loadSkills } = useSourceSkill()
const error = ref<string | null>(null)

const sourceHint = computed(() => sources.value.map((s) => s.uri).join(', '))

function shortHash(hash: string): string {
  return hash.length <= 12 ? hash : hash.slice(0, 12)
}
</script>

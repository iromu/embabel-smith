<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader icon="lock" :title="title" :description="detail || ''" />

      <SectionCard>
        <EmptyState
          icon="science"
          title="POC-only — reference"
          hint="This pane is part of the POC suite and is not backed by the studio's production cores. It is kept here for reference only and is disabled in the product."
          data-test="poc-only-notice"
        >
          <template #action>
            <div class="row q-gutter-xs empty-state__badges">
              <q-badge color="grey" :label="poc" />
              <q-badge color="warning" label="POC-only" />
            </div>
          </template>
        </EmptyState>
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'

withDefaults(
  defineProps<{
    title: string
    poc: string
    detail?: string
  }>(),
  { detail: '' },
)
</script>

<style scoped>
.empty-state__badges {
  margin-top: 8px;
}
</style>

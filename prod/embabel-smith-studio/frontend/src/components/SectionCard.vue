<!--
  Copyright 2026 Ivan Rodriguez.

  Licensed under the Apache License, Version 2.0 (the "License");
  you may not use this file except in compliance with the License.
  You may obtain a copy of the License at

     https://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing, software
  distributed under the License is distributed on an "AS IS" BASIS,
  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  See the License for the specific language governing permissions and
  limitations under the License.
-->
<template>
  <q-card flat bordered class="section-card">
    <div v-if="title || $slots.title" class="section-card__head">
      <div class="section-card__title-wrap">
        <q-icon v-if="icon" :name="icon" size="18px" class="section-card__icon" />
        <div class="section-card__title">{{ title }}</div>
      </div>
      <div class="section-card__actions">
        <slot name="actions" />
      </div>
    </div>
    <div class="section-card__body">
      <slot />
    </div>
  </q-card>
</template>

<script setup lang="ts">
// A titled sub-card used to break a pane into distinct sections. Gives each section its own
// surface + header so a page reads as structured blocks rather than one dense wall of fields.
withDefaults(
  defineProps<{
    title?: string
    icon?: string
  }>(),
  { title: '', icon: '' },
)
</script>

<style scoped>
.section-card {
  background: var(--surface-2, #172033);
  border: 1px solid var(--border, #2b3a4f);
  border-radius: 12px;
}

.section-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border, #2b3a4f);
}

.section-card__title-wrap {
  display: flex;
  align-items: center;
  gap: 9px;
}

.section-card__icon {
  color: #a5b4fc;
}

.section-card__title {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.02em;
  text-transform: uppercase;
  color: var(--text-muted, #94a3b8);
}

.section-card__body {
  padding: 16px;
}
</style>

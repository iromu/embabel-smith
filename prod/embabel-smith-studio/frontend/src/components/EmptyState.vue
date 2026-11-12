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
  <div class="empty-state" :data-test="dataTest">
    <div class="empty-state__icon">
      <q-icon :name="icon" size="30px" />
    </div>
    <div class="empty-state__title">{{ title }}</div>
    <p v-if="hint" class="empty-state__hint">{{ hint }}</p>
    <slot name="action" />
  </div>
</template>

<script setup lang="ts">
// A consistent, centered empty state: a tinted icon tile, a short title, an optional hint, and an
// optional action slot. Replaces the old bare "No X yet." caption lines so empty panes read as
// intentional rather than broken.
withDefaults(
  defineProps<{
    icon: string
    title: string
    hint?: string
    dataTest?: string
  }>(),
  { hint: '', dataTest: '' },
)
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 40px 24px;
  text-align: center;
}

.empty-state__icon {
  width: 56px;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 16px;
  background: rgba(99, 102, 241, 0.1);
  border: 1px solid rgba(99, 102, 241, 0.22);
  color: #a5b4fc;
  margin-bottom: 6px;
}

.empty-state__title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-strong, #e2e8f0);
}

.empty-state__hint {
  font-size: 13px;
  color: var(--text-muted, #94a3b8);
  max-width: 46ch;
  line-height: 1.5;
  margin: 0;
}
</style>

<!--
  SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
  SPDX-License-Identifier: Apache-2.0
-->
<template>
  <div v-if="running || lastElapsedMs !== null" class="model-run-timer" data-test="model-timer">
    <q-icon
      :name="running ? 'hourglass_top' : lastFailed ? 'error' : 'check_circle'"
      :color="running ? 'primary' : lastFailed ? 'negative' : 'positive'"
      size="15px"
    />
    <span class="model-run-timer__label">{{ label }}</span>
    <span class="model-run-timer__elapsed" data-test="model-timer-elapsed">
      {{ formatElapsed(running ? elapsedMs : (lastElapsedMs ?? 0)) }}
    </span>
  </div>
</template>

<script setup lang="ts">
// A quiet read-out of the last model call: which call, and how long it took. It renders nothing
// until a model call has run at least once, ticks while one is in flight, and keeps the final
// elapsed (with a pass/fail icon) on screen until the next launch replaces it.
import { formatElapsed, useModelRunTimer } from '../composables/useModelRunTimer'

const { running, label, elapsedMs, lastElapsedMs, lastFailed } = useModelRunTimer()
</script>

<style scoped>
.model-run-timer {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--text-muted, #aab6c8);
  white-space: nowrap;
}

.model-run-timer__label {
  font-weight: 600;
}

.model-run-timer__elapsed {
  font-variant-numeric: tabular-nums;
}
</style>

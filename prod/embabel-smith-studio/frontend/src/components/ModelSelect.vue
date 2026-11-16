<!--
  SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
  SPDX-License-Identifier: Apache-2.0
-->
<template>
  <q-select
    v-model="selected"
    :options="models"
    :loading="loading"
    label="Model"
    outlined
    clearable
    placeholder="Platform default"
    style="width: 200px"
    :data-test="dataTest"
  />
</template>

<script setup lang="ts">
// The per-pane model picker. The catalog is fetched once and shared (useModelCatalog). Clearing the
// select means "let the platform choose", so it emits undefined rather than an empty string and the
// request body simply omits `model`.
import { computed } from 'vue'

import { useModelCatalog } from '../composables/useModelCatalog'

const props = withDefaults(
  defineProps<{
    modelValue?: string
    dataTest?: string
  }>(),
  { dataTest: '' },
)

const emit = defineEmits<{
  'update:modelValue': [value: string | undefined]
}>()

const { models, loading } = useModelCatalog()

const selected = computed<string | null>({
  get: () => props.modelValue ?? null,
  set: (value) => emit('update:modelValue', value || undefined),
})
</script>

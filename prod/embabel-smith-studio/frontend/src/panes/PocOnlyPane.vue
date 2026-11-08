<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="row items-center q-gutter-sm">
          <div class="text-h5">{{ title }}</div>
          <q-badge color="grey" :label="poc" />
          <q-badge color="warning" label="POC-only" />
        </div>
        <p class="text-grey-7">{{ detail }}</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="text-grey-6" data-test="poc-only-notice">
          This pane is part of the POC suite and is not backed by the studio's production cores.
          It is kept here for reference only and is disabled in the product.
        </div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    poc: string
    detail?: string
  }>(),
  { detail: '' },
)
</script>

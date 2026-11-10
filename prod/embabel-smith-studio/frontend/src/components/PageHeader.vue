<template>
  <q-card-section>
    <div class="pane-head">
      <div class="pane-head-icon">
        <q-icon :name="icon" size="24px" />
      </div>
      <div class="pane-head-text">
        <div class="text-h5">{{ title }}</div>
        <p class="text-grey-7">{{ description }}</p>
      </div>
    </div>
  </q-card-section>
</template>

<script setup lang="ts">
// A consistent, branded pane header: an icon in an indigo-tinted tile beside the title and
// description. Every studio pane uses it so the headers read as one system.
defineProps<{
  icon: string
  title: string
  description: string
}>()
</script>

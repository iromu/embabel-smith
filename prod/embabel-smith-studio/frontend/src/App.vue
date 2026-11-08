<template>
  <q-layout view="hHh lpR fFf">
    <q-header class="bg-primary text-white" elevated>
      <q-toolbar>
        <q-btn flat dense round icon="menu" aria-label="Toggle menu" @click="drawer = !drawer" />
        <q-toolbar-title>Embabel Smith Studio</q-toolbar-title>
        <q-badge color="white" text-color="primary" label="Studio" />
      </q-toolbar>
    </q-header>

    <q-drawer v-model="drawer" v-model:mini="mini" bordered :width="230" class="bg-grey-2">
      <q-list>
        <q-item
          v-for="pane in panes"
          :key="pane.to"
          tag="router-link"
          :to="pane.to"
          clickable
          active
          exact
          class="q-py-sm"
        >
          <q-item-section avatar>
            <q-icon :name="pane.icon" />
          </q-item-section>
          <q-item-section>{{ pane.label }}</q-item-section>
        </q-item>
      </q-list>
    </q-drawer>

    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const drawer = ref(true)
const mini = ref(false)

interface NavPane {
  to: string
  label: string
  icon: string
}

const panes: NavPane[] = [
  { to: '/run', label: 'Run', icon: 'play_arrow' },
  { to: '/author', label: 'Author', icon: 'edit' },
  { to: '/compose', label: 'Compose', icon: 'account_tree' },
  { to: '/retrieve', label: 'Retrieve', icon: 'search' },
  { to: '/eval', label: 'Eval', icon: 'grade' },
  { to: '/lifecycle', label: 'Lifecycle', icon: 'cycle' },
  { to: '/security', label: 'Security', icon: 'security' },
  { to: '/trust', label: 'Trust', icon: 'verified_user' },
  { to: '/traces', label: 'Traces', icon: 'history' },
  { to: '/improve', label: 'Auto-improve', icon: 'autorenew' },
  { to: '/remote', label: 'Remote', icon: 'cloud' },
  { to: '/scale', label: 'Scale', icon: 'show_chart' },
]
</script>

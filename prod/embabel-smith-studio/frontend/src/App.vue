<template>
  <q-layout view="hHh lpR fFf" class="studio-layout">
    <!-- Branded dark sidebar -->
    <q-drawer
      v-model="drawer"
      class="studio-drawer"
      :width="250"
      :breakpoint="992"
      bordered
    >
      <div class="studio-drawer-body">
        <div class="studio-brand">
          <svg
            class="studio-logo"
            viewBox="0 0 28 28"
            fill="none"
            xmlns="http://www.w3.org/2000/svg"
            aria-hidden="true"
          >
            <defs>
              <linearGradient
                id="studio-logo-g"
                x1="0"
                y1="0"
                x2="28"
                y2="28"
                gradientUnits="userSpaceOnUse"
              >
                <stop stop-color="#6366F1" />
                <stop offset="1" stop-color="#4F46E5" />
              </linearGradient>
            </defs>
            <rect width="28" height="28" rx="7" fill="url(#studio-logo-g)" />
            <circle cx="9.5" cy="18" r="2.3" fill="#fff" />
            <circle cx="18.5" cy="9.5" r="2.3" fill="#fff" />
            <circle cx="18.5" cy="18.5" r="2.3" fill="#fff" />
            <path
              d="M11 16.6 L17 11 M11.6 18 L16.2 18.2"
              stroke="#fff"
              stroke-width="1.4"
              stroke-linecap="round"
            />
          </svg>
          <div class="studio-brand-text">
            <div class="studio-brand-name">Embabel Smith</div>
            <div class="studio-brand-tag">Agent Studio</div>
          </div>
        </div>

        <q-list class="studio-nav" separator>
          <template v-for="group in navGroups" :key="group.label">
            <div class="studio-nav-group">{{ group.label }}</div>
            <q-item
              v-for="pane in group.items"
              :key="pane.to"
              tag="router-link"
              :to="pane.to"
              exact
              class="studio-nav-item"
            >
              <q-item-section avatar>
                <q-icon :name="pane.icon" />
              </q-item-section>
              <q-item-section>{{ pane.label }}</q-item-section>
            </q-item>
          </template>
        </q-list>

        <div class="studio-drawer-footer">
          <div class="row items-center no-wrap q-gutter-x-xs">
            <q-badge
              :color="status?.configured ? 'positive' : 'warning'"
              :label="status?.configured ? 'ready' : 'no endpoint'"
            />
            <span class="studio-footer-model" data-test="status-footer">
              {{ status?.configured ? status.model : 'set model env' }}
            </span>
          </div>
        </div>
      </div>
    </q-drawer>

    <!-- Clean header over the content -->
    <q-header class="studio-header">
      <q-toolbar class="items-center">
        <q-btn flat dense round icon="menu" aria-label="Toggle menu" @click="drawer = !drawer" />
        <div class="studio-crumbs">
          <span class="studio-crumbs-root">Studio</span>
          <q-icon name="chevron_right" size="15px" class="q-mx-xs text-grey-5" />
          <span class="studio-crumbs-current" data-test="crumb-current">{{ currentLabel }}</span>
        </div>
        <div class="q-mr-sm" />
        <q-chip
          v-if="status"
          :color="status.configured ? 'positive' : 'warning'"
          text-color="white"
          icon="cloud"
          dense
          data-test="status-chip"
        >
          {{ status.configured ? `${status.model} · ready` : 'no model endpoint' }}
        </q-chip>
      </q-toolbar>
    </q-header>

    <q-page-container>
      <router-view />
    </q-page-container>
  </q-layout>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getStatus } from './api/client'
import type { StatusInfo } from './api/types'

const route = useRoute()
const drawer = ref(true)
const status = ref<StatusInfo | null>(null)

interface NavPane {
  to: string
  label: string
  icon: string
}

const navGroups: { label: string; items: NavPane[] }[] = [
  {
    label: 'Run & Build',
    items: [
      { to: '/run', label: 'Run', icon: 'play_arrow' },
      { to: '/author', label: 'Author', icon: 'edit' },
      { to: '/compose', label: 'Compose', icon: 'account_tree' },
    ],
  },
  {
    label: 'Inspect',
    items: [
      { to: '/retrieve', label: 'Retrieve', icon: 'search' },
      { to: '/traces', label: 'Traces', icon: 'history' },
      { to: '/eval', label: 'Eval', icon: 'grade' },
    ],
  },
  {
    label: 'Govern',
    items: [
      { to: '/lifecycle', label: 'Lifecycle', icon: 'cycle' },
      { to: '/security', label: 'Security', icon: 'security' },
      { to: '/trust', label: 'Trust', icon: 'verified_user' },
    ],
  },
  {
    label: 'Operate',
    items: [
      { to: '/improve', label: 'Auto-improve', icon: 'autorenew' },
      { to: '/remote', label: 'Remote', icon: 'cloud' },
      { to: '/scale', label: 'Scale', icon: 'show_chart' },
    ],
  },
]

const currentLabel = computed(() => {
  for (const group of navGroups) {
    const pane = group.items.find((p) => p.to === route.path)
    if (pane) return pane.label
  }
  return 'Studio'
})

onMounted(async () => {
  try {
    status.value = await getStatus()
  } catch {
    status.value = null
  }
})
</script>

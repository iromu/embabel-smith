<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="storage"
        title="Storage"
        description="Point the studio at a database for durable packs and discovery history, and pick where run traces are stored."
      />

      <SectionCard title="Pack database" icon="storage">
        <div class="row q-col-gutter-md items-start">
          <div class="col" style="min-width: 320px; flex: 1">
            <q-input
              v-model="url"
              label="JDBC endpoint"
              outlined
              style="width: 100%"
              hint="e.g. jdbc:postgresql://localhost:5432/smith_studio"
              data-test="storage-url"
            />
          </div>
          <div class="col-auto">
            <q-input v-model="user" label="User" outlined style="width: 180px" data-test="storage-user" />
          </div>
          <div class="col-auto">
            <q-input
              v-model="password"
              label="Password"
              type="password"
              outlined
              style="width: 180px"
              data-test="storage-password"
            />
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Trace backend" icon="history">
        <q-select
          v-model="traceType"
          :options="traceBackends"
          label="Where run traces are stored"
          outlined
          style="width: 360px"
          data-test="storage-trace-type"
        />
        <div
          v-if="traceType === 'postgres-separate'"
          class="row q-col-gutter-md items-start q-mt-md"
        >
          <div class="col" style="min-width: 320px; flex: 1">
            <q-input
              v-model="traceUrl"
              label="Trace JDBC endpoint"
              outlined
              style="width: 100%"
              data-test="storage-trace-url"
            />
          </div>
          <div class="col-auto">
            <q-input v-model="traceUser" label="User" outlined style="width: 180px" data-test="storage-trace-user" />
          </div>
          <div class="col-auto">
            <q-input
              v-model="tracePassword"
              label="Password"
              type="password"
              outlined
              style="width: 180px"
              data-test="storage-trace-password"
            />
          </div>
        </div>
      </SectionCard>

      <div class="row items-center q-gutter-sm storage-footer">
        <q-btn
          label="Save &amp; connect"
          icon="save"
          color="primary"
          :loading="saving"
          @click="doSave"
          data-test="storage-save"
        />
        <q-badge v-if="status" :color="statusOk ? 'positive' : 'negative'" :label="status" />
        <div v-if="error" class="text-negative text-caption" data-test="storage-error">
          {{ error }}
        </div>
      </div>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'

import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { getStorageSettings, saveStorageSettings } from '../api/client'

const traceBackends = [
  { label: 'JSONL file (default)', value: 'jsonl' },
  { label: 'Postgres — same as packs', value: 'postgres-same' },
  { label: 'Postgres — separate database', value: 'postgres-separate' },
]

const url = ref('')
const user = ref('')
const password = ref('')
const traceType = ref('jsonl')
const traceUrl = ref('')
const traceUser = ref('')
const tracePassword = ref('')

const saving = ref(false)
const status = ref<string | null>(null)
const statusOk = ref(false)
const error = ref<string | null>(null)

onMounted(async () => {
  try {
    const s = await getStorageSettings()
    url.value = s.packDb.url ?? ''
    user.value = s.packDb.user ?? ''
    password.value = s.packDb.password ?? ''
    traceType.value = s.trace.type || 'jsonl'
    traceUrl.value = s.trace.url ?? ''
    traceUser.value = s.trace.user ?? ''
    tracePassword.value = s.trace.password ?? ''
  } catch {
    // Settings not readable yet; the form stays empty.
  }
})

async function doSave() {
  saving.value = true
  status.value = null
  statusOk.value = false
  error.value = null
  try {
    const res = await saveStorageSettings({
      url: url.value.trim(),
      user: user.value.trim(),
      password: password.value,
      traceType: traceType.value,
      traceUrl: traceType.value === 'postgres-separate' ? traceUrl.value.trim() : undefined,
      traceUser: traceType.value === 'postgres-separate' ? traceUser.value.trim() : undefined,
      tracePassword: traceType.value === 'postgres-separate' ? tracePassword.value : undefined,
    })
    statusOk.value = res.ok
    status.value = res.ok ? 'connected' : 'failed'
    if (!res.ok) error.value = res.error
  } catch (e) {
    statusOk.value = false
    status.value = 'failed'
    error.value = e instanceof Error ? e.message : String(e)
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.storage-footer {
  padding: 4px 2px;
}
</style>

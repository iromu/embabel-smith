<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Author</div>
        <p class="text-grey-7">POC 2 — describe a spec in plain language; compile a draft + quality verdict.</p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-start">
          <q-input v-model="agentName" label="Agent name (optional)" dense style="width: 240px" data-test="author-agent" />
          <q-input
            v-model="description"
            label="Describe the spec"
            dense
            style="min-width: 360px"
            data-test="author-description"
          />
          <q-btn label="Compile" color="primary" icon="edit" @click="doCompile" data-test="author-btn" />
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div v-if="noEndpoint" class="text-warning" data-test="author-no-endpoint">
          <q-badge color="warning" label="endpoint not configured" />
          <div class="q-mt-sm text-grey-7">
            Set the model endpoint (OPENAI_CUSTOM_BASE_URL / _API_KEY / _MODELS) to compile a draft.
          </div>
        </div>
        <div v-else-if="error" class="text-negative" data-test="author-error">{{ error }}</div>
        <div v-else-if="draft" data-test="author-result">
          <div class="row q-gutter-sm items-center">
            <q-badge color="secondary" :label="`goal: ${draft.goalName ?? '?'}`" />
            <q-badge
              :color="draft.degenerate ? 'negative' : 'positive'"
              :label="`quality: ${draft.qualityVerdict}`"
            />
          </div>
          <div v-if="draft.qualityReason" class="text-grey-7 q-mt-sm">{{ draft.qualityReason }}</div>
          <div v-if="draft.actions.length > 0" class="text-subtitle2 q-mt-md">Actions</div>
          <q-list dense v-if="draft.actions.length > 0" class="q-mt-sm">
            <q-item v-for="a in draft.actions" :key="a.name" class="q-py-xs">
              <q-item-section>{{ a.name }}</q-item-section>
              <q-item-section side class="text-grey-7">
                {{ a.inputTypes.join(', ') }} → {{ a.outputType }}
              </q-item-section>
            </q-item>
          </q-list>
          <div v-if="draft.issues.length > 0" class="text-negative q-mt-sm">
            <div v-for="(issue, i) in draft.issues" :key="i">{{ issue }}</div>
          </div>
        </div>
        <div v-else class="text-grey-6">Describe a spec and compile a draft.</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'

import { compile, isEndpointNotConfigured } from '../api/client'
import type { AuthorDraft } from '../api/types'

const agentName = ref('')
const description = ref('')
const draft = ref<AuthorDraft | null>(null)
const error = ref<string | null>(null)

const noEndpoint = computed(() => isEndpointNotConfigured(error.value))

async function doCompile() {
  if (!description.value.trim()) {
    return
  }
  error.value = null
  draft.value = null
  try {
    const res = await compile({
      description: description.value.trim(),
      agentName: agentName.value.trim() || undefined,
    })
    if (res.error) {
      error.value = res.error
    } else {
      draft.value = res.draft
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}
</script>

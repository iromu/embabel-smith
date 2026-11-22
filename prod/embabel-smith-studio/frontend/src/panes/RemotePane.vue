<!--
  SPDX-FileCopyrightText: Copyright (c) 2026 Ivan Rodriguez. All rights reserved.
  SPDX-License-Identifier: Apache-2.0
-->
<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="hub"
        title="Remote"
        description="The sidecars this installation configured, whether each answers, and the output of running one of its actions."
      />

      <SectionCard title="Configured bridges" icon="lan">
        <div v-if="error" class="text-negative q-mb-sm" data-test="bridges-error">{{ error }}</div>

        <div v-if="bridges.length > 0" data-test="bridge-list">
          <div
            v-for="bridge in bridges"
            :key="bridge.name"
            class="bridge-row"
            data-test="bridge-row"
            :data-bridge="bridge.name"
          >
            <div class="row items-center q-col-gutter-sm">
              <div class="col-auto text-weight-bold">{{ bridge.name }}</div>
              <div class="col-auto">
                <q-badge v-if="bridge.reachable" color="positive" label="reachable" data-test="bridge-reachable" />
                <q-badge v-else color="negative" label="not running" data-test="bridge-unreachable" />
              </div>
              <div class="col"><code class="text-grey-7">{{ bridge.baseUrl }}</code></div>
            </div>

            <div v-if="!bridge.reachable" class="text-grey-7 q-mt-xs" data-test="bridge-reason">
              {{ bridge.reason }} — probed {{ bridge.urlProbed }}. Start that process by hand; this
              product does not start it.
            </div>

            <div v-else-if="bridge.actions.length === 0" class="text-grey-7 q-mt-xs" data-test="bridge-no-actions">
              It answers, but advertises no actions.
            </div>

            <ul v-else class="action-list q-mt-xs" data-test="bridge-actions">
              <li v-for="action in bridge.actions" :key="action.name" data-test="action-row">
                <span class="text-weight-medium">{{ action.name }}</span>
                <q-badge
                  v-if="!action.runnable"
                  color="warning"
                  class="q-ml-sm"
                  label="not runnable here"
                  data-test="action-unrunnable"
                />
                <div class="text-grey-7 text-caption">
                  inputs: {{ action.inputTypeNames.join(', ') || 'none' }}
                  <template v-if="action.description"> — {{ action.description }}</template>
                </div>
              </li>
            </ul>
          </div>
        </div>

        <div v-if="malformed.length > 0" class="q-mt-md">
          <div class="text-caption text-grey-7">Configuration entries that are not <code>name@url</code>:</div>
          <div v-for="entry in malformed" :key="entry" class="text-negative text-caption" data-test="malformed-entry">
            {{ entry }}
          </div>
        </div>

        <div class="q-mt-md text-caption" data-test="mcp-line">
          <template v-if="mcpEndpoint">
            MCP endpoint: <code>{{ mcpEndpoint }}</code> — configured, not invoked. This product is
            not an MCP client, so nothing here has exercised it.
          </template>
          <template v-else>No MCP endpoint is configured.</template>
        </div>

        <EmptyState
          v-if="bridges.length === 0 && malformed.length === 0"
          icon="lan"
          title="No bridge configured"
          hint="Set smith.studio.remote-bridges to a comma-separated name@url list. Sidecars are started by hand — this pane only reports and invokes them."
          data-test="bridges-empty"
        />
      </SectionCard>

      <SectionCard title="Run an action" icon="play_arrow">
        <div class="row q-col-gutter-md items-start">
          <div class="col-auto">
            <q-select
              v-model="bridgeName"
              label="Bridge"
              outlined
              :options="bridgeOptions"
              style="width: 200px"
              data-test="remote-bridge"
              @update:model-value="action = ''"
            />
          </div>
          <div class="col-auto">
            <q-select
              v-model="action"
              label="Action"
              outlined
              :options="actionOptions"
              style="width: 220px"
              data-test="remote-action"
            />
          </div>
          <div class="col-auto">
            <q-input
              v-model="input"
              label="Input"
              outlined
              autogrow
              style="width: 300px"
              data-test="remote-input"
            />
          </div>
          <div class="col-auto">
            <q-btn
              label="Run"
              color="primary"
              icon="play_arrow"
              :disable="!canRun"
              :loading="running"
              @click="doRun"
              data-test="remote-run"
            />
          </div>
        </div>

        <div v-if="refused" class="text-negative q-mt-md" data-test="remote-error">{{ refused }}</div>

        <div v-else-if="unreachable" class="q-mt-md" data-test="remote-unreachable">
          <q-badge color="negative" label="not running" />
          <span class="q-ml-sm">{{ unreachable }}</span>
        </div>

        <div v-else-if="declined" class="q-mt-md" data-test="remote-declined">
          The sidecar declined the request (HTTP {{ declined.status }}):
          <pre class="remote-output">{{ declined.sidecarBody }}</pre>
        </div>

        <div v-else-if="output !== null" class="q-mt-md" data-test="remote-output">
          <div class="text-caption text-grey-7">
            {{ outputBridge }}/{{ outputAction }} returned:
          </div>
          <pre class="remote-output">{{ output }}</pre>
        </div>

        <div v-else-if="ranOnce" class="text-grey-7 q-mt-md" data-test="remote-nothing">Nothing to show yet.</div>
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { executeRemote, listBridges } from '../api/client'
import type { BridgeInfo } from '../api/types'

const bridges = ref<BridgeInfo[]>([])
const malformed = ref<string[]>([])
const mcpEndpoint = ref<string | null>(null)
const error = ref<string | null>(null)

const bridgeName = ref('')
const action = ref('')
const input = ref('')
const running = ref(false)
const ranOnce = ref(false)
const refused = ref<string | null>(null)
const unreachable = ref<string | null>(null)
const declined = ref<{ status: number; sidecarBody: string } | null>(null)
const output = ref<string | null>(null)
const outputBridge = ref('')
const outputAction = ref('')

const selectedBridge = computed(() => bridges.value.find((candidate) => candidate.name === bridgeName.value))

const bridgeOptions = computed(() => bridges.value.map((bridge) => bridge.name))

/**
 * Only actions the sidecar declares as one `UserInput` are offered. Anything else is still listed
 * above with its declared input types — this pane has one text box and cannot feed it.
 */
const actionOptions = computed(() =>
  (selectedBridge.value?.actions ?? []).filter((candidate) => candidate.runnable).map((candidate) => candidate.name),
)

const canRun = computed(() => bridgeName.value !== '' && action.value !== '')

async function loadBridges() {
  try {
    const res = await listBridges()
    bridges.value = res.bridges
    malformed.value = res.malformedBridges
    mcpEndpoint.value = res.mcpEndpoint
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

function clearResult() {
  refused.value = null
  unreachable.value = null
  declined.value = null
  output.value = null
}

async function doRun() {
  if (!canRun.value) {
    return
  }
  clearResult()
  running.value = true
  try {
    const res = await executeRemote({ bridge: bridgeName.value, action: action.value, input: input.value })
    ranOnce.value = true
    if (res.error) {
      refused.value = res.error
    } else if (res.reason) {
      unreachable.value = `${res.reason} — probed ${res.urlProbed}`
    } else if (res.status !== null) {
      declined.value = { status: res.status, sidecarBody: res.sidecarBody ?? '' }
    } else {
      output.value = res.output
      outputBridge.value = res.bridge ?? bridgeName.value
      outputAction.value = res.action ?? action.value
    }
  } catch (e) {
    ranOnce.value = true
    refused.value = e instanceof Error ? e.message : String(e)
  } finally {
    running.value = false
  }
}

onMounted(loadBridges)
</script>

<style scoped>
.bridge-row {
  padding: 10px 0;
  border-bottom: 1px solid var(--border, #2b3a4f);
}

.bridge-row:last-child {
  border-bottom: none;
}

.action-list {
  margin: 0;
  padding-left: 18px;
}

.remote-output {
  margin: 6px 0 0;
  padding: 10px 12px;
  border-radius: 8px;
  background: rgba(15, 23, 42, 0.6);
  border: 1px solid var(--border, #2b3a4f);
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
}
</style>

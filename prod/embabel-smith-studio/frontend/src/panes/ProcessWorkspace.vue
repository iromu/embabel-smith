<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <PageHeader
        icon="account_tree"
        title="Author a process"
        description="Describe a business process (or point at its documents), generate the GOAP flow, edit it, simulate a run, and publish a pack."
      />
      <q-separator />

      <!-- 1. Blueprint input -->
      <q-card-section>
        <div class="text-subtitle2 q-mb-xs">Blueprint</div>
        <div class="row q-col-gutter-sm items-start">
          <div class="col-auto">
            <q-select
              v-model="sourceKind"
              :options="sourceKinds"
              label="Source"
              dense
              style="width: 200px"
              data-test="pw-source-kind"
            />
          </div>
          <div class="col">
            <q-input
              v-if="sourceKind === 'path'"
              v-model="pathInput"
              label="Business-process docs (server path)"
              dense
              data-test="pw-path"
            />
            <q-input
              v-else-if="sourceKind === 'url'"
              v-model="urlInput"
              label="Repository URL"
              dense
              data-test="pw-url"
            />
            <q-input
              v-else
              v-model="textInput"
              label="Process description"
              type="textarea"
              autogrow
              dense
              data-test="pw-text"
            />
          </div>
          <div class="col-auto">
            <div class="row q-gutter-sm items-center">
              <q-checkbox v-model="autoGenerate" label="Auto" dense data-test="pw-auto" />
              <q-btn
                label="Load docs"
                icon="folder_open"
                outline
                :disable="sourceKind === 'text'"
                :loading="loading"
                @click="doLoad"
                data-test="pw-load"
              />
              <q-btn
                label="Extract process"
                icon="summarize"
                outline
                :disable="sourceKind === 'text'"
                :loading="extracting"
                @click="doExtract"
                data-test="pw-extract"
              />
              <q-btn
                label="Generate flow"
                icon="auto_awesome"
                color="primary"
                :loading="generating"
                @click="doGenerate"
                data-test="pw-generate"
              />
            </div>
          </div>
        </div>

        <div v-if="loadError" class="text-negative text-caption q-mt-sm" data-test="pw-load-error">
          {{ loadError }}
        </div>
        <q-expansion-item
          v-if="discovered && discovered.docs"
          class="q-mt-sm"
          icon="description"
          label="Loaded documents"
          expanded
          dense
          data-test="pw-docs"
        >
          <div class="row q-gutter-xs q-wrap">
            <q-badge v-for="(body, name) in discovered.docs" :key="name" color="primary" class="q-mb-xs">
              {{ name }} ({{ body.length }} chars)
            </q-badge>
          </div>
          <div class="text-caption text-grey-6 q-mt-xs">
            from {{ discovered.sourceType }}: {{ discovered.location }}
          </div>
        </q-expansion-item>

        <div v-if="extractError" class="text-negative text-caption q-mt-sm" data-test="pw-extract-error">
          {{ extractError }}
        </div>
        <q-expansion-item
          v-if="extracted"
          class="q-mt-sm"
          icon="summarize"
          label="Extracted process (used as the generation blueprint)"
          expanded
          dense
          data-test="pw-extracted"
        >
          <div class="text-caption" style="white-space: pre-wrap; max-height: 320px; overflow: auto" data-test="pw-extracted-body">
            {{ extracted }}
          </div>
        </q-expansion-item>
      </q-card-section>

      <q-separator />

      <!-- 2. Progress -->
      <q-card-section>
        <div class="row q-gutter-sm items-center no-wrap">
          <template v-for="(stage, i) in stages" :key="stage.key">
            <q-badge
              :color="stage.color"
              :label="stage.label"
              :outline="stage.state === 'pending'"
            />
            <q-icon
              v-if="i < stages.length - 1"
              name="chevron_right"
              size="14px"
              class="text-grey-5"
            />
          </template>
        </div>
      </q-card-section>

      <q-separator />

      <!-- 3. Flow graph -->
      <q-card-section>
        <div class="row justify-end no-wrap q-mb-xs">
          <q-btn
            v-if="nodes.length"
            label="Fit view"
            icon="fit_screen"
            dense
            flat
            @click="doFitView"
            data-test="pw-fit"
          />
        </div>
        <div data-test="pw-canvas">
          <div class="pw-canvas">
            <VueFlow
                v-model:nodes="nodes"
                v-model:edges="edges"
                :is-valid-connection="onValidConnection"
                :nodes-draggable="true"
                :delete-key-code="null"
                :min-zoom="0.2"
                :max-zoom="2"
                @connect="onConnect"
                @node-click="onNodeClick"
              >
                <template #node-step="props">
                  <div
                    class="compose-node compose-node--step"
                    :class="{ 'compose-node--sim': simStatus((props.data as ComposeStep).name) }"
                  >
                    <Handle
                      v-for="t in (props.data as ComposeStep).inputTypeNames"
                      :key="'in-' + t"
                      type="target"
                      :position="Position.Top"
                      :id="'in:' + t"
                    />
                    <div class="compose-node__body">
                      <div class="compose-node__title">{{ (props.data as ComposeStep).name }}</div>
                      <div class="compose-node__meta">→ {{ (props.data as ComposeStep).outputTypeName }}</div>
                    </div>
                    <Handle
                      type="source"
                      :position="Position.Bottom"
                      :id="'out:' + (props.data as ComposeStep).outputTypeName"
                    />
                  </div>
                </template>
                <template #node-goal="props">
                  <div class="compose-node compose-node--goal">
                    <Handle type="target" :position="Position.Top" :id="'in:' + (props.data as ComposeGoal).outputTypeName" />
                    <div class="compose-node__body">
                      <div class="compose-node__title">{{ (props.data as ComposeGoal).name }}</div>
                      <div class="compose-node__meta">goal · {{ (props.data as ComposeGoal).outputTypeName }}</div>
                    </div>
                  </div>
                </template>
              </VueFlow>
            </div>
            <div v-if="nodes.length === 0" class="text-grey-6 text-caption q-mt-xs" data-test="pw-empty">
              Generate a flow to see the GOAP graph here.
            </div>
          </div>

          <div class="q-mt-md" data-test="pw-editor-wrap">
            <q-card v-if="selected" flat bordered class="q-pa-sm">
              <div class="text-subtitle2 q-mb-sm">{{ selected.type === 'goal' ? 'Goal' : 'Step' }}</div>
              <template v-if="stepData">
                <q-input v-model="stepData.name" label="Name" dense data-test="pw-step-name" />
                <q-input v-model="stepData.description" label="Description" dense class="q-mt-sm" />
                <q-input v-model="stepData.outputTypeName" label="Output type" dense class="q-mt-sm" />
                <div class="q-mt-sm">
                  <div class="text-caption text-grey-7">Input types</div>
                  <div class="row q-gutter-xs q-mt-xs">
                    <q-badge v-for="t in stepData.inputTypeNames" :key="t" color="primary" class="q-pr-xs">
                      {{ t }}
                      <q-btn flat dense round size="sm" icon="close" @click="removeInputType(t)" />
                    </q-badge>
                  </div>
                  <div class="row q-gutter-xs q-mt-xs">
                    <q-input v-model="newInputType" dense outlined placeholder="Type name" style="flex: 1" />
                    <q-btn label="Add" dense outline icon="add" @click="addInputType" />
                  </div>
                </div>
                <q-input
                  v-model="stepData.prompt"
                  label="Prompt (Jinja2)"
                  type="textarea"
                  autogrow
                  dense
                  class="q-mt-sm"
                  data-test="pw-step-prompt"
                />
              </template>
              <template v-else-if="goalData">
                <q-input v-model="goalData.name" label="Name" dense />
                <q-input v-model="goalData.description" label="Description" dense class="q-mt-sm" />
                <q-input v-model="goalData.outputTypeName" label="Output type" dense class="q-mt-sm" />
              </template>
            </q-card>
            <div v-else class="text-grey-6 text-caption" data-test="pw-no-selection">
              Select a step or the goal to edit it.
            </div>
          </div>
      </q-card-section>

      <q-separator />

      <!-- 4. Simulate + Publish -->
      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="simInput"
            label="Sample input (bound to UserInput)"
            dense
            style="flex: 1; min-width: 280px"
            data-test="pw-sim-input"
          />
          <q-btn
            label="Simulate run"
            icon="play_arrow"
            outline
            :disable="nodes.length === 0"
            :loading="simulating"
            @click="doSimulate"
            data-test="pw-simulate"
          />
        </div>

        <div v-if="simResult" class="q-mt-md" data-test="pw-sim-result">
          <div class="row q-gutter-sm items-center">
            <q-badge :color="simResult.success ? 'positive' : 'negative'" :label="simResult.success ? 'succeeded' : 'failed'" />
            <span v-if="simResult.error" class="text-negative text-caption">{{ simResult.error }}</span>
          </div>
          <div v-if="simResult.steps.length > 0" class="q-mt-sm">
            <div
              v-for="(s, i) in simResult.steps"
              :key="i"
              class="row items-center no-wrap q-py-xs"
              :class="s.status === 'failed' ? 'text-negative' : 'text-grey-7'"
            >
              <q-icon :name="s.status === 'completed' ? 'check_circle' : 'cancel'" size="16px" class="q-mr-sm" />
              <span class="text-caption" style="width: 220px">{{ s.name }}</span>
              <span class="text-caption text-grey-5 q-mr-sm">{{ s.durationMs }}ms</span>
              <span class="text-caption ellipsis" style="flex: 1">{{ s.outputExcerpt }}</span>
            </div>
          </div>
          <div v-if="simResult.output" class="q-mt-sm text-caption">
            <div class="text-grey-6">Goal output:</div>
            <div style="white-space: pre-wrap">{{ simResult.output }}</div>
          </div>
        </div>

        <q-separator class="q-my-md" />

        <div class="row q-gutter-sm items-center">
          <q-select
            v-model="publishSource"
            label="Publish to (source)"
            dense
            :options="sourceOptions"
            style="width: 320px"
            data-test="pw-source"
          />
          <q-checkbox v-model="overwrite" label="Overwrite" dense />
          <q-btn
            label="Save"
            icon="save"
            outline
            :disable="nodes.length === 0"
            @click="doSave"
            data-test="pw-save"
          />
          <q-btn
            label="Publish pack"
            icon="upload"
            color="primary"
            :disable="nodes.length === 0"
            @click="doPublish"
            data-test="pw-publish"
          />
        </div>
        <div v-if="publishError" class="text-negative text-caption q-mt-sm" data-test="pw-publish-error">
          {{ publishError }}
        </div>
        <div v-else-if="publishResult" class="text-positive text-caption q-mt-sm" data-test="pw-published">
          Published pack '{{ publishResult.packName }}' (goal '{{ publishResult.goal }}') to
          {{ publishResult.published?.location }}
        </div>
        <div v-if="saveError" class="text-negative text-caption q-mt-sm" data-test="pw-save-error">
          {{ saveError }}
        </div>
        <div v-else-if="saveResult" class="text-positive text-caption q-mt-sm" data-test="pw-saved">
          Saved pack '{{ saveResult }}' to the studio library.
        </div>

        <q-separator class="q-my-md" />

        <div class="row q-col-gutter-md" data-test="pw-library">
          <div class="col">
            <div class="text-caption text-grey-7 q-mb-xs">Saved packs</div>
            <div v-if="storedPacks.length === 0" class="text-grey-6 text-caption">No saved packs yet.</div>
            <div
              v-for="p in storedPacks"
              :key="p.name"
              class="row items-center no-wrap q-py-xs"
              data-test="pw-saved-pack"
            >
              <q-icon name="inventory_2" size="16px" class="q-mr-sm text-grey-6" />
              <div class="col">
                <div class="text-caption">{{ p.name }} <span class="text-grey-6">· {{ p.steps }} steps</span></div>
                <div class="text-caption text-grey-6 ellipsis">{{ p.goal }}</div>
              </div>
              <q-btn
                label="Load"
                dense
                size="sm"
                outline
                icon="input"
                @click="doLoadStored(p.name)"
                data-test="pw-load-stored"
              />
            </div>
          </div>
          <div class="col">
            <div class="text-caption text-grey-7 q-mb-xs">Recent discoveries</div>
            <div v-if="discoveries.length === 0" class="text-grey-6 text-caption">
              No discoveries recorded yet.
            </div>
            <div
              v-for="(d, i) in discoveries"
              :key="i"
              class="row items-center no-wrap q-py-xs"
              data-test="pw-discovery"
            >
              <q-icon name="folder_open" size="16px" class="q-mr-sm text-grey-6" />
              <div class="col">
                <div class="text-caption ellipsis">{{ d.location }}</div>
                <div class="text-caption text-grey-6">{{ d.sourceType }} · {{ d.docCount }} docs</div>
              </div>
            </div>
          </div>
        </div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { Handle, Position, useVueFlow, VueFlow } from '@vue-flow/core'
import type { Connection } from '@vue-flow/core'

import PageHeader from '../components/PageHeader.vue'
import {
  compose,
  discover,
  extract,
  generate,
  listDiscoveries,
  listSources,
  listStoredPacks,
  loadStoredPack,
  savePack,
  simulate,
} from '../api/client'
import { isModelNotConfigured } from '../api/types'
import type {
  ComposeResponse,
  DiscoveryRecord,
  DiscoverResponse,
  SimulateResponse,
  SourceInfo,
  StoredPackInfo,
} from '../api/types'
import {
  assemblePack,
  isValidConnection,
  packToNodes,
  type ComposeGoal,
  type ComposeStep,
} from '../compose/model'

const DEFAULT_DOCS_PATH =
  '/home/wantez/source/github/TauricResearch/TradingAgents/docs/business'

// ---- Blueprint input ----
const sourceKinds = [
  { label: 'Docs path', value: 'path' },
  { label: 'Repo URL', value: 'url' },
  { label: 'Text', value: 'text' },
]
const sourceKind = ref<'path' | 'url' | 'text'>('path')
const pathInput = ref(DEFAULT_DOCS_PATH)
const urlInput = ref('')
const textInput = ref('')
const autoGenerate = ref(false)
const loading = ref(false)
const discovered = ref<DiscoverResponse | null>(null)
const loadError = ref<string | null>(null)
const extracting = ref(false)
const extractError = ref<string | null>(null)
const extracted = ref<string | null>(null)

// ---- Generation ----
const generating = ref(false)
const genError = ref<string | null>(null)

// ---- Flow state (Vue Flow nodes are the source of truth) ----
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const selected = ref<any>(null)
const { fitView, nodes: flowNodes } = useVueFlow()

async function doFitView(): Promise<void> {
  await fitView({ padding: 0.12, duration: 300 })
}

/**
 * Vue Flow measures node dimensions asynchronously after mount; fitting before that is a no-op.
 * Poll the flow's nodes until every one is measured, then fit (with a bounded fallback).
 */
function fitWhenMeasured(): void {
  let tries = 0
  const check = (): void => {
    const ns = (flowNodes.value ?? []) as { measured?: { width?: number } }[]
    const ready = ns.length > 0 && ns.every((n) => (n.measured?.width ?? 0) > 0)
    if (ready || ++tries > 60) {
      void doFitView()
      return
    }
    requestAnimationFrame(check)
  }
  requestAnimationFrame(check)
}

const goalNode = computed(() => (nodes.value as any[]).find((n) => n.type === 'goal') ?? null)
const goal = computed<ComposeGoal | null>(() =>
  goalNode.value ? (goalNode.value.data as ComposeGoal) : null,
)
const steps = computed<ComposeStep[]>(() =>
  (nodes.value as any[]).filter((n) => n.type === 'step').map((n) => n.data as ComposeStep),
)
const stepData = computed<ComposeStep | null>(() =>
  selected.value?.type === 'step' ? (selected.value.data as ComposeStep) : null,
)
const goalData = computed<ComposeGoal | null>(() =>
  selected.value?.type === 'goal' ? (selected.value.data as ComposeGoal) : null,
)

function onNodeClick({ node }: { node: any }) {
  selected.value = node
}
function onValidConnection(connection: Connection): boolean {
  return isValidConnection(connection.sourceHandle ?? null, connection.targetHandle ?? null)
}
function onConnect(connection: Connection): void {
  if (!isValidConnection(connection.sourceHandle ?? null, connection.targetHandle ?? null)) return
  edges.value.push({
    id: `${connection.source}-${connection.sourceHandle}-${connection.target}-${connection.targetHandle}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle ?? undefined,
    targetHandle: connection.targetHandle ?? undefined,
  })
}
const newInputType = ref('')
function addInputType() {
  const t = newInputType.value.trim()
  const data = stepData.value
  if (!t || !data) return
  if (!data.inputTypeNames.includes(t)) data.inputTypeNames.push(t)
  newInputType.value = ''
}
function removeInputType(t: string) {
  if (stepData.value) stepData.value.inputTypeNames = stepData.value.inputTypeNames.filter((x) => x !== t)
}

// ---- Simulate ----
const simulating = ref(false)
const simResult = ref<SimulateResponse | null>(null)
const simInput = ref('')
function simStatus(name: string): string {
  if (!simResult.value) return ''
  const s = simResult.value.steps.find((x) => x.name === name)
  return s ? s.status : ''
}

// ---- Publish ----
const sources = ref<SourceInfo[]>([])
const publishSource = ref('')
const sourceOptions = computed(() => sources.value.map((s) => s.uri))
const overwrite = ref(false)
const publishResult = ref<ComposeResponse | null>(null)
const publishError = ref<string | null>(null)
const saveError = ref<string | null>(null)
const saveResult = ref<string | null>(null)
const storedPacks = ref<StoredPackInfo[]>([])
const discoveries = ref<DiscoveryRecord[]>([])

// ---- Progress stages ----
type StageState = 'pending' | 'active' | 'done'
const stages = computed(() => {
  const s: { key: string; label: string; state: StageState }[] = [
    { key: 'load', label: 'Load docs', state: discovered.value ? 'done' : loading.value ? 'active' : 'pending' },
    { key: 'generate', label: 'Generate flow', state: nodes.value.length ? 'done' : generating.value ? 'active' : 'pending' },
    { key: 'simulate', label: 'Simulate', state: simResult.value ? 'done' : simulating.value ? 'active' : 'pending' },
    { key: 'publish', label: 'Publish', state: publishResult.value ? 'done' : 'pending' },
  ]
  return s.map((st) => ({
    key: st.key,
    label: st.label,
    state: st.state,
    color: st.state === 'done' ? 'positive' : st.state === 'active' ? 'primary' : 'grey-6',
  }))
})

async function doLoad() {
  loadError.value = null
  discovered.value = null
  loading.value = true
  try {
    const req = sourceKind.value === 'url' ? { url: urlInput.value.trim() } : { path: pathInput.value.trim() }
    const res = await discover(req)
    if (res.error) {
      loadError.value = res.error
    } else {
      discovered.value = res
      void refreshLibrary()
      if (autoGenerate.value) {
        await doGenerate()
      }
    }
  } catch (e) {
    loadError.value = e instanceof Error ? e.message : String(e)
  } finally {
    loading.value = false
  }
}

async function doExtract() {
  extractError.value = null
  extracted.value = null
  extracting.value = true
  try {
    const req =
      sourceKind.value === 'url' ? { url: urlInput.value.trim() } : { path: pathInput.value.trim() }
    const res = await extract(req)
    if (res.errorCode === 'MODEL_NOT_CONFIGURED') {
      extractError.value = 'Model not configured: ' + res.error
    } else if (res.error || !res.description) {
      extractError.value = res.error ?? 'Extraction returned no description.'
    } else {
      extracted.value = res.description
    }
  } catch (e) {
    extractError.value = e instanceof Error ? e.message : String(e)
  } finally {
    extracting.value = false
  }
}

async function doGenerate() {
  genError.value = null
  simResult.value = null
  generating.value = true
  try {
    // A previously extracted process is the generation blueprint; otherwise the raw source is.
    const req = extracted.value
      ? { blueprint: extracted.value }
      : sourceKind.value === 'path'
        ? { path: pathInput.value.trim() }
        : sourceKind.value === 'url'
          ? { url: urlInput.value.trim() }
          : { blueprint: textInput.value.trim() }
    const res = await generate(req as { blueprint?: string; path?: string })
    if (res.errorCode === 'MODEL_NOT_CONFIGURED') {
      genError.value = 'Model not configured: ' + res.error
    } else if (res.error || !res.pack) {
      genError.value = res.error ?? 'Generation returned no pack.'
    } else {
      const { nodes: n, edges: e } = packToNodes(res.pack)
      nodes.value = n
      edges.value = e
      selected.value = null
      fitWhenMeasured()
    }
  } catch (e) {
    genError.value = e instanceof Error ? e.message : String(e)
  } finally {
    generating.value = false
  }
}

async function doSimulate() {
  if (!goal.value || steps.value.length === 0) return
  simResult.value = null
  simulating.value = true
  try {
    const res = await simulate({ pack: assemblePack(goal.value, steps.value), input: simInput.value })
    simResult.value = res
    if (isModelNotConfigured(res)) {
      simResult.value = { success: false, output: null, error: res.error, errorCode: res.errorCode, steps: [] }
    }
  } catch (e) {
    simResult.value = { success: false, output: null, error: e instanceof Error ? e.message : String(e), errorCode: null, steps: [] }
  } finally {
    simulating.value = false
  }
}

async function doPublish() {
  if (!goal.value || steps.value.length === 0 || publishSource.value === '') {
    publishError.value = 'Add a flow and pick a source to publish to.'
    return
  }
  publishError.value = null
  publishResult.value = null
  try {
    const res = await compose({
      source: publishSource.value,
      pack: assemblePack(goal.value, steps.value),
      overwrite: overwrite.value,
    })
    if (res.error) {
      publishError.value = res.error
    } else {
      publishResult.value = res
    }
  } catch (e) {
    publishError.value = e instanceof Error ? e.message : String(e)
  }
}

async function doSave() {
  if (!goal.value || steps.value.length === 0) return
  saveError.value = null
  saveResult.value = null
  try {
    const res = await savePack({ pack: assemblePack(goal.value, steps.value) })
    if (res.error) {
      saveError.value = res.error
    } else {
      saveResult.value = res.name ?? ''
      await refreshLibrary()
    }
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e)
  }
}

async function doLoadStored(name: string) {
  saveError.value = null
  try {
    const res = await loadStoredPack(name)
    if (res.error || !res.pack) {
      saveError.value = res.error ?? 'Failed to load pack.'
      return
    }
    const { nodes: n, edges: e } = packToNodes(res.pack)
    nodes.value = n
    edges.value = e
    selected.value = null
    fitWhenMeasured()
  } catch (e) {
    saveError.value = e instanceof Error ? e.message : String(e)
  }
}

async function refreshLibrary() {
  try {
    storedPacks.value = await listStoredPacks()
  } catch {
    storedPacks.value = []
  }
  try {
    discoveries.value = await listDiscoveries()
  } catch {
    discoveries.value = []
  }
}

onMounted(async () => {
  try {
    sources.value = await listSources()
    if (sources.value.length > 0) publishSource.value = sources.value[0].uri
  } catch {
    sources.value = []
  }
  await refreshLibrary()
})
</script>

<style scoped>
.pw-canvas {
  height: 560px;
  border: 1px solid var(--border, #2b3a4f);
  border-radius: 8px;
  overflow: hidden;
  background-color: var(--bg, #0f172a);
  background-image: radial-gradient(circle, rgba(148, 163, 184, 0.18) 1px, transparent 1px);
  background-size: 22px 22px;
}
.compose-node {
  min-width: 132px;
  max-width: 220px;
  background: var(--surface, #1e293b);
  border: 1px solid var(--border, #2b3a4f);
  border-radius: 8px;
  padding: 8px 10px;
  color: var(--text-strong, #e2e8f0);
  font-size: 13px;
  cursor: pointer;
}
.compose-node--goal {
  border-color: var(--nav-active, #4f46e5);
  background: color-mix(in srgb, var(--nav-active, #4f46e5) 18%, var(--surface, #1e293b));
  box-shadow: 0 0 0 1px var(--nav-active, #4f46e5);
}
.compose-node--sim {
  border-color: #26a69a;
  box-shadow: 0 0 0 1px #26a69a;
}
.compose-node__title {
  font-weight: 600;
}
.compose-node__meta {
  color: var(--text-muted, #94a3b8);
  font-size: 11px;
}
</style>

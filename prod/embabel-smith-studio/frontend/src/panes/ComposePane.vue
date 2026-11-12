<template>
  <q-page>
    <div class="pane-stack">
      <PageHeader
        icon="account_tree"
        title="Compose"
        description="Place steps, connect them with type-checked edges, and export a composite spec-pack."
      />

      <SectionCard title="Canvas" icon="account_tree">
        <template #actions>
          <div class="row items-center q-gutter-sm">
            <q-btn label="Add step" outline icon="add" @click="addStep" data-test="compose-add-step" />
            <q-btn
              label="Add goal"
              outline
              icon="flag"
              :disable="goalNode !== null"
              @click="addGoal"
              data-test="compose-add-goal"
            />
            <q-btn label="Validate" outline icon="check" @click="doValidate" data-test="compose-validate" />
            <q-btn
              label="Export"
              icon="upload"
              color="primary"
              @click="doExport"
              data-test="compose-export"
            />
          </div>
        </template>

        <div class="row q-col-gutter-md items-start">
          <div class="col" data-test="compose-canvas">
            <div class="compose-canvas">
              <VueFlow
                v-model:nodes="nodes"
                v-model:edges="edges"
                :is-valid-connection="onValidConnection"
                :nodes-draggable="true"
                :delete-key-code="null"
                @connect="onConnect"
                @node-click="onNodeClick"
              >
                <template #node-step="props">
                  <div class="compose-node compose-node--step">
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
                    <Handle
                      type="target"
                      :position="Position.Top"
                      :id="'in:' + (props.data as ComposeGoal).outputTypeName"
                    />
                    <div class="compose-node__body">
                      <div class="compose-node__title">{{ (props.data as ComposeGoal).name }}</div>
                      <div class="compose-node__meta">goal · {{ (props.data as ComposeGoal).outputTypeName }}</div>
                    </div>
                  </div>
                </template>
              </VueFlow>
            </div>
            <EmptyState
              v-if="nodes.length === 0"
              icon="account_tree"
              title="An empty canvas"
              hint="Add a step to begin, then add a goal for the type the run returns."
              data-test="compose-empty"
            />
          </div>

          <div class="col" style="width: 320px">
            <q-card v-if="selected" flat bordered class="q-pa-sm">
              <div class="text-subtitle2 q-mb-sm">
                {{ selected.type === 'goal' ? 'Goal' : 'Step' }}
              </div>

              <template v-if="stepData">
                <q-input v-model="stepData.name" label="Name" dense data-test="compose-step-name" />
                <q-input
                  v-model="stepData.description"
                  label="Description"
                  dense
                  class="q-mt-sm"
                />
                <q-input
                  v-model="stepData.outputTypeName"
                  label="Output type"
                  dense
                  class="q-mt-sm"
                  data-test="compose-step-output"
                />
                <div class="q-mt-sm">
                  <div class="text-caption text-grey-7">Input types</div>
                  <div class="row q-gutter-xs q-mt-xs">
                    <q-badge
                      v-for="t in stepData.inputTypeNames"
                      :key="t"
                      color="primary"
                      class="q-pr-xs"
                    >
                      {{ t }}
                      <q-btn
                        flat
                        dense
                        round
                        size="sm"
                        icon="close"
                        @click="removeInputType(t)"
                      />
                    </q-badge>
                    <span v-if="stepData.inputTypeNames.length === 0" class="text-grey-6 text-caption">
                      none
                    </span>
                  </div>
                  <div class="row q-gutter-xs q-mt-xs">
                    <q-input
                      v-model="newInputType"
                      dense
                      outlined
                      placeholder="Type name"
                      style="flex: 1"
                      data-test="compose-step-input"
                    />
                    <q-btn
                      label="Add"
                      dense
                      outline
                      icon="add"
                      @click="addInputType"
                      data-test="compose-step-input-add"
                    />
                  </div>
                </div>
                <q-input
                  v-model="stepData.prompt"
                  label="Prompt (Jinja2)"
                  type="textarea"
                  autogrow
                  dense
                  class="q-mt-sm"
                  data-test="compose-step-prompt"
                />
                <q-input
                  v-model="toolGroupsText"
                  label="Tool groups (comma-separated)"
                  dense
                  class="q-mt-sm"
                />
              </template>

              <template v-else-if="goalData">
                <q-input v-model="goalData.name" label="Name" dense data-test="compose-goal-name" />
                <q-input
                  v-model="goalData.description"
                  label="Description"
                  dense
                  class="q-mt-sm"
                />
                <q-input
                  v-model="goalData.outputTypeName"
                  label="Output type (what the run returns)"
                  dense
                  class="q-mt-sm"
                  data-test="compose-goal-output"
                />
              </template>

              <q-btn
                label="Remove"
                icon="delete"
                flat
                dense
                color="negative"
                class="q-mt-md"
                @click="removeNode(selected)"
                data-test="compose-remove"
              />
            </q-card>
            <div v-else class="text-grey-6 text-caption" data-test="compose-no-selection">
              Select a step or the goal to edit its fields, or add one above.
            </div>
          </div>
        </div>
      </SectionCard>

      <SectionCard title="Validation &amp; export" icon="publish">
        <div v-if="problems" class="q-mb-md">
          <div v-if="problems.length === 0" class="text-positive" data-test="compose-valid">
            Flow is sound: the goal is reachable and every input is fed.
          </div>
          <div v-else data-test="compose-problems">
            <div class="text-negative text-caption q-mb-xs">The flow has problems:</div>
            <div v-for="(p, i) in problems" :key="i" class="text-negative text-caption">{{ p }}</div>
          </div>
        </div>

        <div class="row q-col-gutter-md items-center">
          <div class="col-auto">
            <q-select
              v-model="source"
              label="Publish to (source)"
              outlined
              :options="sourceOptions"
              style="width: 280px"
              data-test="compose-source"
            />
          </div>
          <div class="col-auto">
            <q-checkbox v-model="overwrite" label="Overwrite" dense data-test="compose-overwrite" />
          </div>
          <div class="col-auto">
            <q-checkbox v-model="push" label="Push" dense data-test="compose-push" />
          </div>
        </div>

        <div v-if="exportError" class="text-negative q-mt-sm" data-test="compose-error">
          {{ exportError }}
        </div>
        <div v-else-if="exportResult" class="text-positive q-mt-sm" data-test="compose-exported">
          Exported pack '{{ exportResult.packName }}' (goal '{{ exportResult.goal }}') to
          {{ exportResult.published?.location }}
          {{ exportResult.published?.pushed ? '(pushed)' : '' }} — run it from the Run pane.
        </div>
      </SectionCard>
    </div>
  </q-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { Handle, Position, VueFlow } from '@vue-flow/core'
import type { Connection } from '@vue-flow/core'

import EmptyState from '../components/EmptyState.vue'
import PageHeader from '../components/PageHeader.vue'
import SectionCard from '../components/SectionCard.vue'
import { compose, listSources } from '../api/client'
import type { ComposeResponse, SourceInfo } from '../api/types'
import {
  assemblePack,
  isValidConnection,
  validateFlow,
  type ComposeGoal,
  type ComposeStep,
} from '../compose/model'

// ---- Flow state (Vue Flow's node list is the source of truth) ----
// `any[]` on purpose: Vue Flow's recursive `Node` type blows up TS's structural checks in this
// TS version. The semantic payload is typed via the ComposeStep / ComposeGoal casts below.
const nodes = ref<any[]>([])
const edges = ref<any[]>([])
const selected = ref<any>(null)

// Operate on the node list as `any[]`: Vue Flow's recursive `Node` type blows up TS's inference
// inside `.find` / `.filter` callbacks, so the callbacks stay untyped and the results are cast.
const goalNode = computed(() => {
  const found = (nodes.value as unknown as any[]).find((n) => n.type === 'goal')
  return found ?? null
})
const goal = computed<ComposeGoal | null>(() =>
  goalNode.value ? (goalNode.value.data as ComposeGoal) : null,
)
const steps = computed<ComposeStep[]>(() =>
  (nodes.value as unknown as any[]).filter((n) => n.type === 'step').map((n) => n.data as ComposeStep),
)

const stepData = computed<ComposeStep | null>(() =>
  selected.value?.type === 'step' ? (selected.value.data as ComposeStep) : null,
)
const goalData = computed<ComposeGoal | null>(() =>
  selected.value?.type === 'goal' ? (selected.value.data as ComposeGoal) : null,
)

let seq = 0
function addStep() {
  seq += 1
  const node = {
    id: `step-${seq}`,
    type: 'step',
    position: { x: 80 + (seq % 4) * 240, y: 60 + Math.floor(seq / 4) * 160 },
    data: {
      name: `step${seq}`,
      description: '',
      inputTypeNames: ['UserInput'],
      outputTypeName: `Step${seq}Out`,
      prompt: '',
      toolGroups: [],
    },
  }
  nodes.value.push(node)
  selected.value = node
}

function addGoal() {
  if (goalNode.value) {
    selected.value = goalNode.value
    return
  }
  const node = {
    id: 'goal',
    type: 'goal',
    position: { x: 420, y: 300 },
    data: { name: 'goal', description: '', outputTypeName: 'Result' },
  }
  nodes.value.push(node)
  selected.value = node
}

function removeNode(node: any) {
  nodes.value = nodes.value.filter((n) => n.id !== node.id)
  edges.value = edges.value.filter((e) => e.source !== node.id && e.target !== node.id)
  if (selected.value?.id === node.id) {
    selected.value = null
  }
}

function onNodeClick({ node }: { node: any }) {
  selected.value = node
}

// ---- Type-checked edges ----
function onValidConnection(connection: Connection): boolean {
  return isValidConnection(connection.sourceHandle ?? null, connection.targetHandle ?? null)
}

function onConnect(connection: Connection): void {
  if (!isValidConnection(connection.sourceHandle ?? null, connection.targetHandle ?? null)) {
    return
  }
  const edge = {
    id: `${connection.source}-${connection.sourceHandle}-${connection.target}-${connection.targetHandle}`,
    source: connection.source,
    target: connection.target,
    sourceHandle: connection.sourceHandle ?? undefined,
    targetHandle: connection.targetHandle ?? undefined,
  }
  edges.value.push(edge)
}

// ---- Step input-type chips ----
const newInputType = ref('')
function addInputType() {
  const t = newInputType.value.trim()
  const data = stepData.value
  if (!t || !data) return
  if (!data.inputTypeNames.includes(t)) {
    data.inputTypeNames.push(t)
  }
  newInputType.value = ''
}
function removeInputType(t: string) {
  const data = stepData.value
  if (!data) return
  data.inputTypeNames = data.inputTypeNames.filter((x) => x !== t)
}

// Tool groups as a comma-separated string.
const toolGroupsText = computed({
  get: () => (stepData.value ? stepData.value.toolGroups.join(', ') : ''),
  set: (v: string) => {
    if (!stepData.value) return
    stepData.value.toolGroups = v
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
  },
})

// ---- Validation ----
const problems = ref<string[] | null>(null)
function doValidate() {
  problems.value = validateFlow(goal.value, steps.value)
}

// ---- Export ----
const sources = ref<SourceInfo[]>([])
const source = ref('')
const sourceOptions = computed(() => sources.value.map((s) => s.uri))
const overwrite = ref(false)
const push = ref(false)
const exportResult = ref<ComposeResponse | null>(null)
const exportError = ref<string | null>(null)

async function loadSources() {
  sources.value = await listSources()
  if (sources.value.length > 0 && source.value === '') {
    source.value = sources.value[0].uri
  }
}

async function doExport() {
  if (source.value === '' || !goal.value || steps.value.length === 0) {
    exportError.value = 'Add a goal and at least one step, and pick a source to publish to.'
    return
  }
  exportError.value = null
  exportResult.value = null
  try {
    exportResult.value = await compose({
      source: source.value,
      pack: assemblePack(goal.value, steps.value),
      overwrite: overwrite.value,
      push: push.value,
    })
    if (exportResult.value.error) {
      exportError.value = exportResult.value.error
    }
  } catch (e) {
    exportError.value = e instanceof Error ? e.message : String(e)
  }
}

void loadSources()
</script>

<style scoped>
.compose-canvas {
  height: 460px;
  border: 1px solid var(--border, #2b3a4f);
  border-radius: 8px;
  overflow: hidden;
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
}
.compose-node__title {
  font-weight: 600;
}
.compose-node__meta {
  color: var(--text-muted, #94a3b8);
  font-size: 11px;
}
</style>

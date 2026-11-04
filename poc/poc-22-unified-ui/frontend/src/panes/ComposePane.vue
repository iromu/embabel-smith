<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Compose</div>
        <p class="text-grey-7">
          POC 14 — place skills, connect them with type-checked edges, and export a composite skill-pack.
        </p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-input
            v-model="pick"
            label="Add a skill (from the palette)"
            dense
            data-test="compose-skill-input"
            :hint="skillNames.join(', ') || 'loading…'"
            style="min-width: 300px"
          />
          <q-btn label="Add" icon="add" @click="addNode" data-test="compose-add-btn" />
        </div>

        <div class="row q-gutter-sm items-center q-mt-sm">
          <q-input v-model="edgeFrom" label="from" dense style="min-width: 220px" data-test="compose-from" />
          <q-input v-model="edgeTo" label="to" dense style="min-width: 220px" data-test="compose-to" />
          <q-btn label="Connect" icon="link" @click="addEdge" data-test="compose-connect-btn" />
        </div>
        <div
          v-if="snap"
          class="q-mt-sm"
          :class="snap.valid ? 'text-positive' : 'text-negative'"
          data-test="compose-snap"
        >
          {{ snap.valid ? 'Snapped — types match.' : snap.reason }}
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="text-subtitle2">Canvas</div>
        <div class="q-mt-sm">
          <q-badge v-for="n in nodes" :key="'n' + n" class="q-mr-sm" color="primary" :label="n" />
          <span v-if="nodes.length === 0" class="text-grey-6">no skills placed</span>
        </div>
        <div class="q-mt-sm">
          <q-badge
            v-for="(e, i) in edges"
            :key="'e' + i"
            class="q-mr-sm"
            color="secondary"
            :label="`${e.from} → ${e.to}`"
          />
          <span v-if="edges.length === 0" class="text-grey-6">no edges</span>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm">
          <q-btn label="Validate" color="primary" @click="validate" data-test="compose-validate-btn" />
          <q-btn label="Export composite" color="positive" @click="exportComposite" data-test="compose-export-btn" />
        </div>

        <div v-if="validation" class="q-mt-md" data-test="compose-validation">
          <q-badge :color="validation.valid ? 'positive' : 'negative'" :label="validation.valid ? 'valid chain' : 'invalid'">
          </q-badge>
          <div v-if="validation.chain.length > 0" class="q-mt-sm">chain: {{ validation.chain.join(' → ') }}</div>
          <div v-for="(issue, i) in validation.issues" :key="'i' + i" class="text-negative" data-test="compose-issue">
            {{ issue }}
          </div>
        </div>

        <div v-if="composed" class="q-mt-md text-positive" data-test="compose-result">
          exported "{{ composed.goal.name }}" — steps: {{ composed.steps.join(', ') }}
        </div>
        <div v-if="composeError" class="text-negative q-mt-md">{{ composeError }}</div>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { compose, listSkills } from '../api/client'
import type { ComposeResponse, SkillInfo } from '../api/types'
import { checkEdge, validateCanvas } from '../canvas/model'
import type { CanvasResult, Edge, EdgeCheck } from '../canvas/model'

const skills = ref<SkillInfo[]>([])
const nodes = ref<string[]>([])
const edges = ref<Edge[]>([])
const pick = ref('')
const edgeFrom = ref('')
const edgeTo = ref('')
const snap = ref<EdgeCheck | null>(null)
const validation = ref<CanvasResult | null>(null)
const composed = ref<ComposeResponse | null>(null)
const composeError = ref<string | null>(null)

const skillNames = computed(() => skills.value.map((s) => s.name))

function skill(name: string): SkillInfo | undefined {
  return skills.value.find((s) => s.name === name)
}

function addNode() {
  const name = pick.value.trim()
  if (name && skill(name) && !nodes.value.includes(name)) {
    nodes.value.push(name)
  }
  pick.value = ''
}

function addEdge() {
  const source = skill(edgeFrom.value.trim())
  const target = skill(edgeTo.value.trim())
  if (!source || !target) {
    return
  }
  snap.value = checkEdge(source, target)
  edges.value.push({ from: source.name, to: target.name })
  edgeFrom.value = ''
  edgeTo.value = ''
}

function validate() {
  validation.value = validateCanvas({ nodes: nodes.value, edges: edges.value, skills: skills.value })
}

async function exportComposite() {
  composeError.value = null
  composed.value = null
  try {
    composed.value = await compose({ name: 'compose-composite', nodes: nodes.value, edges: edges.value })
  } catch (e) {
    composeError.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(async () => {
  skills.value = await listSkills()
})
</script>

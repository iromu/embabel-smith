<template>
  <q-page padding>
    <q-card flat bordered class="q-pa-md">
      <q-card-section>
        <div class="text-h5">Auto-improve</div>
        <p class="text-grey-7">
          Operate the trace-driven self-improvement loop: schedule it, trigger an iteration, and
          review the gated candidates it produces.
        </p>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row q-gutter-sm items-center">
          <q-badge
            :color="status && status.running ? 'positive' : 'grey'"
            :label="`loop: ${status ? loopStatusLabel(status) : 'unknown'}`"
          />
          <q-btn
            v-if="!status?.running"
            label="Start schedule"
            color="primary"
            icon="play_arrow"
            @click="doStart"
            data-test="loop-start"
          />
          <q-btn
            v-else
            label="Stop schedule"
            color="negative"
            icon="stop"
            @click="doStop"
            data-test="loop-stop"
          />
          <q-btn
            label="Run iteration"
            color="secondary"
            icon="replay"
            @click="doTrigger"
            data-test="loop-trigger"
          />
          <div class="q-ml-sm">
            <q-toggle
              v-model="autoPublish"
              label="Auto-publish"
              dense
              data-test="loop-autopublish"
              @update:model-value="doAutoPublish"
            />
          </div>
        </div>
        <div v-if="iteration && noEndpoint" class="text-warning q-mt-sm" data-test="loop-no-endpoint">
          <q-badge color="warning" label="model not configured" />
          <div class="q-mt-sm text-grey-7">{{ iteration.error }}</div>
        </div>
        <div v-else-if="iteration" class="text-grey-7 q-mt-sm" data-test="loop-iteration">
          last iteration: ran={{ iteration.ran }} · candidates={{ iteration.candidates }}
          <template v-if="iteration.queuedSkills.length > 0">
            · queued: {{ iteration.queuedSkills.join(', ') }}
          </template>
          <template v-if="iteration.publishedSkills.length > 0">
            · published: {{ iteration.publishedSkills.join(', ') }}
          </template>
        </div>
      </q-card-section>

      <q-separator />

      <q-card-section>
        <div class="row items-center q-gutter-sm">
          <div class="text-subtitle2">Pending candidates</div>
          <q-btn flat dense icon="refresh" @click="refresh" data-test="loop-refresh" />
        </div>

        <div v-if="error" class="text-negative q-mt-sm" data-test="loop-error">{{ error }}</div>
        <div
          v-else-if="candidates.length === 0"
          class="text-grey-6 q-mt-sm"
          data-test="loop-empty"
        >
          No pending candidates.
        </div>

        <q-list bordered separator class="q-mt-sm" v-else data-test="loop-candidates">
          <q-item v-for="c in candidates" :key="c.id">
            <q-item-section avatar>
              <q-badge :color="lifecycleColor(c.proposedState)" :label="c.proposedState" />
            </q-item-section>
            <q-item-section>
              <div class="text-weight-medium">{{ c.skill }}</div>
              <div class="text-grey-7 text-caption">
                score {{ c.score.toFixed(3) }} (Δ
                {{ scoreDelta(c) >= 0 ? '+' : '' }}{{ scoreDelta(c).toFixed(3) }} over baseline
                {{ c.baseline.toFixed(3) }}) · {{ evidenceCount(c) }}
              </div>
              <div v-if="c.diffSummary" class="text-caption q-mt-xs">{{ c.diffSummary }}</div>
            </q-item-section>
            <q-item-section side>
              <div class="row q-gutter-x-sm">
                <q-btn
                  flat
                  dense
                  color="positive"
                  icon="check"
                  :aria-label="`approve ${c.skill}`"
                  @click="doApprove(c.id)"
                />
                <q-btn
                  flat
                  dense
                  color="negative"
                  icon="close"
                  :aria-label="`reject ${c.skill}`"
                  @click="doReject(c.id)"
                />
              </div>
            </q-item-section>
          </q-item>
        </q-list>
      </q-card-section>
    </q-card>
  </q-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import {
  approveCandidate,
  getAutoPublish,
  listCandidates,
  loopStatus,
  rejectCandidate,
  setAutoPublish,
  startLoop,
  stopLoop,
  triggerLoop,
} from '../api/client'
import type { CandidateInfo, IterationInfo, LoopStatus } from '../api/types'
import { isModelNotConfigured } from '../api/types'
import { evidenceCount, lifecycleColor, loopStatusLabel, scoreDelta } from '../improve/model'

const status = ref<LoopStatus | null>(null)
const autoPublish = ref(false)
const iteration = ref<IterationInfo | null>(null)
const candidates = ref<CandidateInfo[]>([])
const error = ref<string | null>(null)

const noEndpoint = computed(() => isModelNotConfigured(iteration.value))

async function refresh() {
  error.value = null
  try {
    status.value = await loopStatus()
    autoPublish.value = status.value.autoPublish
    candidates.value = await listCandidates()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doStart() {
  try {
    status.value = await startLoop()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doStop() {
  try {
    status.value = await stopLoop()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doTrigger() {
  error.value = null
  try {
    iteration.value = await triggerLoop()
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doAutoPublish(value: boolean) {
  autoPublish.value = value
  try {
    const res = await setAutoPublish({ autoPublish: value })
    autoPublish.value = res.autoPublish
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doApprove(id: string) {
  error.value = null
  try {
    await approveCandidate(id)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

async function doReject(id: string) {
  error.value = null
  try {
    await rejectCandidate(id)
    await refresh()
  } catch (e) {
    error.value = e instanceof Error ? e.message : String(e)
  }
}

onMounted(async () => {
  await refresh()
  try {
    autoPublish.value = (await getAutoPublish()).autoPublish
  } catch {
    // auto-publish state is best-effort on first load
  }
})
</script>

/**
 * Shared picker state for the panes that operate on a spec from a source (change 06): loads the
 * studio's configured sources, then the packs of the selected source. Pure logic (no DOM) so it is
 * trivially unit-testable and shared by every pane that needs a source + spec.
 */

import { computed, onMounted, ref, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'

import { listPacks, listSources } from '../api/client'
import type { PackInfo, SourceInfo } from '../api/types'

export interface SourceSpecState {
  sources: Ref<SourceInfo[]>
  source: Ref<string>
  specs: Ref<PackInfo[]>
  spec: Ref<string>
  loading: Ref<boolean>
  sourceOptions: ComputedRef<string[]>
  specOptions: ComputedRef<string[]>
  loadSources: () => Promise<void>
  loadSpecs: () => Promise<void>
}

export function useSourceSpec(): SourceSpecState {
  const sources = ref<SourceInfo[]>([])
  const source = ref('')
  const specs = ref<PackInfo[]>([])
  const spec = ref('')
  const loading = ref(false)

  const sourceOptions = computed(() => sources.value.map((s) => s.uri))
  const specOptions = computed(() => specs.value.map((s) => s.name))

  async function loadSources() {
    sources.value = await listSources()
    if (sources.value.length > 0 && source.value === '') {
      source.value = sources.value[0].uri
    }
    if (source.value !== '') {
      await loadSpecs()
    }
  }

  async function loadSpecs() {
    specs.value = []
    if (source.value === '') {
      return
    }
    loading.value = true
    try {
      specs.value = await listPacks(source.value)
    } finally {
      loading.value = false
    }
  }

  watch(source, () => {
    spec.value = ''
    void loadSpecs()
  })

  onMounted(() => {
    void loadSources()
  })

  return {
    sources,
    source,
    specs,
    spec,
    loading,
    sourceOptions,
    specOptions,
    loadSources,
    loadSpecs,
  }
}

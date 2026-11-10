/**
 * Shared picker state for the panes that operate on a skill from a source (change 06): loads the
 * studio's configured sources, then the packs of the selected source. Pure logic (no DOM) so it is
 * trivially unit-testable and shared by every pane that needs a source + skill.
 */

import { computed, onMounted, ref, watch } from 'vue'
import type { ComputedRef, Ref } from 'vue'

import { listPacks, listSources } from '../api/client'
import type { PackInfo, SourceInfo } from '../api/types'

export interface SourceSkillState {
  sources: Ref<SourceInfo[]>
  source: Ref<string>
  skills: Ref<PackInfo[]>
  skill: Ref<string>
  loading: Ref<boolean>
  sourceOptions: ComputedRef<string[]>
  skillOptions: ComputedRef<string[]>
  loadSources: () => Promise<void>
  loadSkills: () => Promise<void>
}

export function useSourceSkill(): SourceSkillState {
  const sources = ref<SourceInfo[]>([])
  const source = ref('')
  const skills = ref<PackInfo[]>([])
  const skill = ref('')
  const loading = ref(false)

  const sourceOptions = computed(() => sources.value.map((s) => s.uri))
  const skillOptions = computed(() => skills.value.map((s) => s.name))

  async function loadSources() {
    sources.value = await listSources()
    if (sources.value.length > 0 && source.value === '') {
      source.value = sources.value[0].uri
    }
    if (source.value !== '') {
      await loadSkills()
    }
  }

  async function loadSkills() {
    skills.value = []
    if (source.value === '') {
      return
    }
    loading.value = true
    try {
      skills.value = await listPacks(source.value)
    } finally {
      loading.value = false
    }
  }

  watch(source, () => {
    skill.value = ''
    void loadSkills()
  })

  onMounted(() => {
    void loadSources()
  })

  return {
    sources,
    source,
    skills,
    skill,
    loading,
    sourceOptions,
    skillOptions,
    loadSources,
    loadSkills,
  }
}

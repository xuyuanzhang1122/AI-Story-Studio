<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { HL_COLORS } from '@/utils/scriptHighlight'

interface ChipItem {
  name: string
  type: 'character' | 'scene' | 'prop'
}

interface Props {
  show: boolean
  shotNo: number | string
  scriptText: string
  characters?: string[]
  scenes?: string[]
  props?: string[]
  maxLength?: number
  onClose: () => void
  onSave: (newText: string) => void
}

const props = withDefaults(defineProps<Props>(), {
  characters: () => [],
  scenes: () => [],
  props: () => [],
  maxLength: 5000,
})

const editingText = ref('')

watch(
  () => [props.show, props.scriptText],
  ([show]) => {
    if (show) editingText.value = props.scriptText || ''
  },
  { immediate: true },
)

const chips = computed<ChipItem[]>(() => [
  ...props.characters.map<ChipItem>(n => ({ name: n, type: 'character' })),
  ...props.scenes.map<ChipItem>(n => ({ name: n, type: 'scene' })),
  ...props.props.map<ChipItem>(n => ({ name: n, type: 'prop' })),
])

const chipColor = (t: ChipItem['type']) => {
  switch (t) {
    case 'character': return HL_COLORS.character
    case 'scene':     return HL_COLORS.scene
    case 'prop':      return HL_COLORS.prop
  }
}

const handleSave = () => {
  const trimmed = editingText.value.trim()
  if (trimmed !== (props.scriptText || '').trim()) {
    props.onSave(trimmed)
  }
  props.onClose()
}

const handleKeydown = (e: KeyboardEvent) => {
  if (e.key === 'Escape') props.onClose()
  if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) handleSave()
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-150"
      leave-active-class="transition-opacity duration-150"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="show"
        class="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60"
        @click.self="onClose"
        @keydown="handleKeydown"
      >
        <div
          class="bg-bg-elevated border border-border-default rounded-xl shadow-2xl w-[860px] max-w-[95vw] max-h-[88vh] flex flex-col overflow-hidden"
        >
          <!-- Header -->
          <div class="flex items-center justify-between px-5 py-3 border-b border-border-default">
            <h3 class="text-text-primary text-base font-semibold">
              编辑分镜 #{{ shotNo }}
            </h3>
            <button
              class="p-1.5 rounded-lg hover:bg-bg-hover transition-colors"
              :title="'关闭 (Esc)'"
              @click="onClose"
            >
              <svg class="w-5 h-5 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>

          <!-- Textarea -->
          <div class="flex-1 overflow-hidden px-5 py-4">
            <textarea
              v-model="editingText"
              :maxlength="maxLength"
              class="w-full h-full min-h-[360px] resize-none bg-[#1f2228] border border-[#4b5563] rounded-lg text-white text-sm leading-relaxed px-4 py-3 focus:outline-none focus:border-[#22D3EE]/70 placeholder-text-tertiary caret-[#22D3EE] shadow-inner"
              placeholder="请输入分镜剧本..."
              autofocus
            />
          </div>

          <!-- Bottom: chips + count + actions -->
          <div class="border-t border-border-default px-5 py-3 flex items-center justify-between gap-4">
            <!-- Chips -->
            <div class="flex flex-wrap items-center gap-1.5 flex-1 min-w-0">
              <span
                v-for="chip in chips"
                :key="chip.type + ':' + chip.name"
                class="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs"
                :style="{
                  color: chipColor(chip.type),
                  backgroundColor: chipColor(chip.type) + '1A',
                  border: '1px solid ' + chipColor(chip.type) + '40',
                }"
              >
                <svg class="w-3 h-3" fill="currentColor" viewBox="0 0 8 8" aria-hidden="true">
                  <circle cx="4" cy="4" r="3" />
                </svg>
                {{ chip.name }}
              </span>
              <span v-if="chips.length === 0" class="text-text-tertiary text-xs">
                未绑定角色 / 场景 / 道具
              </span>
            </div>

            <!-- Count + actions -->
            <div class="flex items-center gap-3 shrink-0">
              <span class="text-text-tertiary text-xs tabular-nums">
                {{ editingText.length }} / {{ maxLength }}
              </span>
              <button
                class="px-3 py-1.5 text-sm rounded bg-bg-subtle text-text-secondary hover:bg-bg-hover transition-colors"
                @click="onClose"
              >
                取消
              </button>
              <button
                class="px-3 py-1.5 text-sm rounded bg-[#22D3EE] text-black font-medium hover:bg-[#67E8F9] transition-colors"
                title="保存 (Ctrl+Enter)"
                @click="handleSave"
              >
                保存
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

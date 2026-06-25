<script setup lang="ts">
import { computed } from 'vue'

const props = withDefaults(defineProps<{
  visible: boolean
  name: string
  imageUrl?: string | null
  description?: string | null
  x: number
  y: number
  variant?: 'detail' | 'compact'
  placement?: 'cursor' | 'fixed'
}>(), {
  imageUrl: null,
  description: null,
  variant: 'detail',
  placement: 'cursor',
})

const panelStyle = computed(() => {
  const isCompact = props.variant === 'compact'
  const width = isCompact ? 420 : 760
  const estimatedHeight = isCompact ? 470 : 560
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1280
  const viewportHeight = typeof window !== 'undefined' ? window.innerHeight : 720
  const offset = props.placement === 'cursor' ? 16 : 0

  return {
    left: `${Math.max(12, Math.min(props.x + offset, viewportWidth - width - 12))}px`,
    top: `${Math.max(12, Math.min(props.y + offset, viewportHeight - estimatedHeight - 12))}px`,
    width: `${width}px`,
  }
})
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-100"
      leave-active-class="transition-opacity duration-100"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="visible"
        class="fixed z-[1000] pointer-events-none rounded border border-border-default bg-bg-elevated shadow-2xl overflow-hidden"
        :style="panelStyle"
      >
        <template v-if="variant === 'compact'">
          <div class="p-2">
            <div class="h-[390px] rounded overflow-hidden bg-bg-hover flex items-center justify-center">
              <img
                v-if="imageUrl"
                :src="imageUrl"
                :alt="name"
                class="w-full h-full object-contain bg-white"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-14 h-14 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                  <circle cx="8.5" cy="8.5" r="1.5"></circle>
                  <path d="M21 15l-5-5L5 21"></path>
                </svg>
              </div>
            </div>
            <div class="px-3 py-3 text-center text-text-primary text-base font-semibold truncate">
              {{ name }}
            </div>
          </div>
        </template>

        <template v-else>
          <div class="grid grid-cols-[minmax(0,1.35fr)_minmax(220px,0.65fr)] gap-3 p-3">
            <div class="h-[500px] rounded overflow-hidden bg-bg-hover flex items-center justify-center">
              <img
                v-if="imageUrl"
                :src="imageUrl"
                :alt="name"
                class="w-full h-full object-contain bg-white"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-16 h-16 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                  <circle cx="8.5" cy="8.5" r="1.5"></circle>
                  <path d="M21 15l-5-5L5 21"></path>
                </svg>
              </div>
            </div>

            <div class="min-w-0 max-h-[500px] overflow-y-auto pr-1">
              <div class="text-text-primary text-sm font-semibold leading-6">
                名称：{{ name }}
              </div>
              <div class="mt-2 text-text-secondary text-sm leading-6 whitespace-pre-wrap break-words">
                {{ description || '暂无简介' }}
              </div>
            </div>
          </div>
        </template>
      </div>
    </Transition>
  </Teleport>
</template>

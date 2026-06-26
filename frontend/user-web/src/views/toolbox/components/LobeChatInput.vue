<script setup lang="ts">
import { createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LobeChatInputIsland from './LobeChatInputIsland'

const props = defineProps<{
  canSend: boolean
  loading: boolean
  modelValue: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
  send: []
}>()

const mountEl = ref<HTMLDivElement | null>(null)
let root: Root | null = null

const render = () => {
  if (!root) return

  root.render(
    createElement(LobeChatInputIsland, {
      canSend: props.canSend,
      loading: props.loading,
      value: props.modelValue,
      onInput: (value: string) => {
        emit('update:modelValue', value)
      },
      onSend: () => {
        emit('send')
      },
    })
  )
}

onMounted(() => {
  if (!mountEl.value) return
  root = createRoot(mountEl.value)
  render()
})

watch(() => [props.modelValue, props.loading, props.canSend], render)

onBeforeUnmount(() => {
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="mountEl" class="lobe-chat-input-mount" />
</template>

<style scoped>
.lobe-chat-input-mount {
  width: 100%;
}
</style>

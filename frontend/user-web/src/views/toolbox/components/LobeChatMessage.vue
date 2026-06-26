<script setup lang="ts">
import { createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import type { ConversationItem } from '@/stores/toolbox'
import { useUserStore } from '@/stores/user'
import LobeChatMessageIsland, { type LobeChatMessageAction } from './LobeChatMessageIsland'

const props = defineProps<{
  conversation: ConversationItem
}>()

const emit = defineEmits<{
  action: [action: LobeChatMessageAction, payload?: string | number]
}>()

const userStore = useUserStore()
const mountEl = ref<HTMLDivElement | null>(null)
let root: Root | null = null

const render = () => {
  if (!root) return

  root.render(
    createElement(LobeChatMessageIsland, {
      conversation: JSON.parse(JSON.stringify(props.conversation)),
      userAvatar: userStore.userAvatar,
      userName: userStore.userName,
      onAction: (action: LobeChatMessageAction, payload?: string | number) => {
        emit('action', action, payload)
      },
    })
  )
}

onMounted(() => {
  if (!mountEl.value) return
  root = createRoot(mountEl.value)
  render()
})

watch(
  () => [props.conversation, userStore.userAvatar, userStore.userName],
  render,
  { deep: true }
)

onBeforeUnmount(() => {
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="mountEl" class="lobe-chat-message-mount" />
</template>

<style scoped>
.lobe-chat-message-mount {
  width: 100%;
}
</style>

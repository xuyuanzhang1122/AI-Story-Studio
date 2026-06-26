<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useToolboxStore, type ConversationItem } from '@/stores/toolbox'
import LobeChatMessage from './components/LobeChatMessage.vue'
import type { LobeChatMessageAction } from './components/LobeChatMessageIsland'
import ChatInput from './components/ChatInput.vue'
import HistoryDrawer from './components/HistoryDrawer.vue'

const toolboxStore = useToolboxStore()

const showHistoryDrawer = ref(false)
const chatArea = ref<HTMLDivElement>()

// 自动滚动到底部
const scrollToBottom = () => {
  nextTick(() => {
    if (chatArea.value) {
      chatArea.value.scrollTop = chatArea.value.scrollHeight
    }
  })
}

// 监听对话列表变化,自动滚动
watch(() => toolboxStore.conversations.length, () => {
  scrollToBottom()
})

const downloadUrl = (url: string, filename: string) => {
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
}

const handleMessageAction = async (
  conversation: ConversationItem,
  action: LobeChatMessageAction,
  payload?: string | number
) => {
  const response = conversation.aiResponse

  if (action === 'copy') {
    if (!response?.text) return
    try {
      await navigator.clipboard.writeText(response.text)
      window.$message?.success('已复制到剪贴板')
    } catch (error) {
      console.error('Failed to copy:', error)
      window.$message?.error('复制失败')
    }
    return
  }

  if (action === 'open') {
    const targetUrl = typeof payload === 'string' ? payload : response?.resultUrl
    if (targetUrl) window.open(targetUrl, '_blank')
    return
  }

  if (action === 'download-image') {
    const urls = response?.allImageUrls?.length
      ? response.allImageUrls
      : response?.resultUrl
        ? [response.resultUrl]
        : []
    const index = typeof payload === 'number' ? payload : 0
    const url = urls[index]
    if (!url) return
    downloadUrl(url, `toolbox-image-${Date.now()}-${index + 1}`)
    window.$message?.success(`开始下载图片 ${index + 1}`)
    return
  }

  if (action === 'download') {
    if (!response?.resultUrl) return
    downloadUrl(response.resultUrl, `toolbox-${conversation.contentType.toLowerCase()}-${Date.now()}`)
    window.$message?.success('开始下载')
    return
  }

  if (action === 'save-character' || action === 'save-scene') {
    if (!response?.historyId) {
      window.$message?.warning('无法保存:缺少历史记录ID')
      return
    }

    try {
      await toolboxStore.saveToAssets(response.historyId)
      window.$message?.success(action === 'save-character' ? '已保存到角色库' : '已保存到场景库')
    } catch (error: any) {
      window.$message?.error(error.message || '保存失败')
    }
  }
}

onMounted(async () => {
  console.log('[ToolboxPage] Component mounted - Chat style')
  // 初始化 store，加载持久化的对话和历史记录
  await toolboxStore.init()
})

onUnmounted(() => {
  toolboxStore.stopJobPolling()
})
</script>

<template>
  <div class="toolbox-page h-screen flex flex-col bg-bg-base">
    <!-- Header -->
    <div class="flex-shrink-0 flex items-center justify-between px-8 py-4 border-b border-border-default bg-bg-elevated">
      <div class="flex items-center gap-3">
        <h1 class="text-2xl font-bold text-text-primary">工具箱</h1>
      </div>

      <!-- History Button -->
      <button
        class="w-10 h-10 rounded flex items-center justify-center text-text-tertiary hover:bg-bg-hover hover:text-text-primary transition-all border border-border-default"
        title="历史记录"
        @click="showHistoryDrawer = true"
      >
        <svg class="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </button>
    </div>

    <!-- Notice -->
    <div class="flex-shrink-0 px-8 py-3 bg-bg-subtle border-b border-border-default">
      <p class="text-text-tertiary text-sm">
        *仅保留最近7天的生成记录,有效素材请及时引用或保存
      </p>
    </div>

    <!-- Chat Area (Scrollable) -->
    <div ref="chatArea" class="toolbox-chat-scroll flex-1 overflow-auto">
      <div class="max-w-5xl mx-auto px-8 py-4">
        <!-- Empty State -->
        <div v-if="toolboxStore.conversations.length === 0" class="flex flex-col items-center justify-center py-20">
          <div class="w-20 h-20 rounded bg-bg-subtle border border-border-default flex items-center justify-center mb-4">
            <svg class="w-10 h-10 text-text-primary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z" />
            </svg>
          </div>
          <h3 class="text-text-secondary text-lg font-medium mb-2">开始你的AI创作</h3>
          <p class="text-text-tertiary text-sm">选择类型,输入提示词,即可生成文字或图片</p>
        </div>

        <!-- Conversation Bubbles -->
        <div v-else class="space-y-3">
          <LobeChatMessage
            v-for="conversation in toolboxStore.conversations"
            :key="conversation.id"
            :conversation="conversation"
            @action="(action, payload) => handleMessageAction(conversation, action, payload)"
          />
        </div>
      </div>
    </div>

    <!-- Input Area (Fixed Bottom) -->
    <div class="flex-shrink-0">
      <ChatInput />
    </div>

    <!-- History Drawer -->
    <HistoryDrawer v-model:show="showHistoryDrawer" />
  </div>
</template>

<style scoped>
.toolbox-page {
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.03), transparent 38%),
    #111114;
}

.toolbox-chat-scroll :deep(.ant-app),
.toolbox-chat-scroll :deep(.lobe-chat-message-mount) {
  width: 100%;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-chat-item) {
  padding: 2px 0;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-user-message) {
  min-width: 220px;
  max-width: 560px;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-message-meta) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 8px;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-message-meta span) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.66);
  font-size: 11px;
  line-height: 1;
  padding: 5px 7px;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-message-text) {
  color: rgba(255, 255, 255, 0.92);
  line-height: 1.7;
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-loading) {
  align-items: center;
  color: rgba(255, 255, 255, 0.68);
  display: flex;
  gap: 10px;
  min-width: 150px;
  padding: 4px 0;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-error) {
  color: #fca5a5;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-empty-result) {
  color: rgba(255, 255, 255, 0.55);
}

.toolbox-chat-scroll :deep(.lobe-toolbox-action) {
  align-items: center;
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 6px;
  color: rgba(255, 255, 255, 0.78);
  cursor: pointer;
  display: inline-flex;
  font-size: 12px;
  height: 28px;
  justify-content: center;
  padding: 0 10px;
  transition: all 0.16s ease;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-action:hover) {
  background: rgba(255, 255, 255, 0.14);
  border-color: rgba(167, 139, 250, 0.42);
  color: #fff;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-image-grid) {
  display: grid;
  gap: 10px;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  max-width: 520px;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-image-single) {
  max-width: min(520px, 70vw);
}

.toolbox-chat-scroll :deep(.lobe-toolbox-image-shell) {
  position: relative;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-image-shell img) {
  border-radius: 8px;
  cursor: pointer;
  display: block;
  max-height: 520px;
  object-fit: cover;
  width: 100%;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-media-actions) {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-video-shell video) {
  border-radius: 8px;
  max-width: min(560px, 72vw);
  width: 100%;
}

.toolbox-chat-scroll :deep(.lobe-toolbox-message-footer) {
  color: rgba(255, 255, 255, 0.36);
  font-size: 11px;
  padding-top: 2px;
}
</style>

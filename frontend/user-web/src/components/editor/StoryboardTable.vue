<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, ref } from 'vue'
import { useEditorStore } from '@/stores/editor'
import StoryboardRow from './StoryboardRow.vue'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'
import { importApi } from '@/api/import'

const editorStore = useEditorStore()

// Polling for GENERATING status
let pollingTimer: number | null = null

const hasGeneratingAssets = computed(() => {
  return editorStore.shots.some(shot =>
    shot.shotImage.status === 'GENERATING' || shot.video.status === 'GENERATING'
  )
})

const startPolling = () => {
  if (pollingTimer) return

  pollingTimer = window.setInterval(() => {
    if (hasGeneratingAssets.value) {
      console.log('[StoryboardTable] Polling shots (有生成中的资产)...')
      editorStore.fetchShots()
    } else {
      stopPolling()
    }
  }, 3000) // Poll every 3 seconds
}

const stopPolling = () => {
  if (pollingTimer) {
    clearInterval(pollingTimer)
    pollingTimer = null
  }
}

// Toggle select all
const handleToggleSelectAll = () => {
  if (editorStore.allSelected) {
    editorStore.deselectAll()
  } else {
    editorStore.selectAll()
  }
}

// Toggle single shot selection
const handleToggleShotSelection = (shotId: number) => {
  editorStore.toggleShotSelection(shotId)
}

// Update shot script
const handleUpdateScript = async (shotId: number, scriptText: string) => {
  await editorStore.updateShot(shotId, scriptText)
}

// Merge with previous shot (merge up)
const handleMergeUp = async (shotId: number) => {
  const shots = editorStore.shots
  const currentIndex = shots.findIndex(s => s.id === shotId)
  if (currentIndex <= 0) return
  
  const prevShot = shots[currentIndex - 1]
  const currentShot = shots[currentIndex]
  
  // 首次提醒，之后直接合并
  const mergeConfirmed = localStorage.getItem('merge_confirmed')
  if (!mergeConfirmed) {
    if (!confirm(`确定将当前分镜合并到上一条吗？\n合并后当前分镜将被删除。\n\n（之后将不再提醒）`)) {
      return
    }
    localStorage.setItem('merge_confirmed', 'true')
  }
  
  await editorStore.mergeShots([prevShot.id, currentShot.id])
}

// Merge with next shot (merge down)
const handleMergeDown = async (shotId: number) => {
  const shots = editorStore.shots
  const currentIndex = shots.findIndex(s => s.id === shotId)
  if (currentIndex < 0 || currentIndex >= shots.length - 1) return
  
  const currentShot = shots[currentIndex]
  const nextShot = shots[currentIndex + 1]
  
  // 首次提醒，之后直接合并
  const mergeConfirmed = localStorage.getItem('merge_confirmed')
  if (!mergeConfirmed) {
    if (!confirm(`确定将下一条分镜合并到当前分镜吗？\n合并后下一条分镜将被删除。\n\n（之后将不再提醒）`)) {
      return
    }
    localStorage.setItem('merge_confirmed', 'true')
  }
  
  await editorStore.mergeShots([currentShot.id, nextShot.id])
}

// Delete shot
const handleDeleteShot = async (shotId: number) => {
  if (confirm('确定删除这条分镜吗？')) {
    await editorStore.deleteShot(shotId)
  }
}

// Delete selected shots
const handleDeleteSelected = async () => {
  const count = editorStore.selectedShotIds.size
  if (confirm(`确定删除选中的 ${count} 条分镜吗？`)) {
    const shotIds = Array.from(editorStore.selectedShotIds)
    for (const shotId of shotIds) {
      await editorStore.deleteShot(shotId)
    }
    editorStore.deselectAll()
  }
}

// Merge selected shots
const handleMergeSelected = async () => {
  const count = editorStore.selectedShotIds.size
  if (count < 2) {
    alert('请至少选择两条分镜进行合并')
    return
  }
  if (confirm(`确定将选中的 ${count} 条分镜合并为一条吗？\n合并后将保留第一条分镜，其他分镜的内容将追加到文本末尾。`)) {
    await editorStore.mergeShots(Array.from(editorStore.selectedShotIds))
  }
}

// Batch generate shots
const handleBatchGenerateShots = async () => {
  const count = editorStore.selectedShotIds.size
  if (!confirm(`确定为选中的 ${count} 条分镜批量生成分镜图吗？这将消耗 ${count * 50} 积分。`)) {
    return
  }

  try {
    await editorStore.batchGenerateShots({
      targetIds: Array.from(editorStore.selectedShotIds),
      mode: 'MISSING', // Only generate for shots without images
      countPerItem: 1,
      aspectRatio: '21:9', // Default aspect ratio for shots
    })
    editorStore.deselectAll()
  } catch (error) {
    console.error('[StoryboardTable] Batch generate shots failed:', error)
  }
}

// Batch generate videos
const handleBatchGenerateVideos = async () => {
  const count = editorStore.selectedShotIds.size
  if (!confirm(`确定为选中的 ${count} 条分镜批量生成视频吗？这将消耗 ${count * 100} 积分。`)) {
    return
  }

  try {
    await editorStore.batchGenerateVideos({
      targetIds: Array.from(editorStore.selectedShotIds),
      mode: 'MISSING', // Only generate for shots without videos
      countPerItem: 1,
      aspectRatio: '16:9', // Default aspect ratio for videos
    })
    editorStore.deselectAll()
  } catch (error) {
    console.error('[StoryboardTable] Batch generate videos failed:', error)
  }
}

// Add new shot
const showAddModal = ref(false)
const newShotScript = ref('')

// AI parse script
const showParseModal = ref(false)
const fullScript = ref('')
const isParsing = ref(false)
const parseSeconds = ref(0)  // 秒读计时
let parseTimer: ReturnType<typeof setInterval> | null = null
let parseAbortController: AbortController | null = null  // 用于取消请求

// 计算属性：解析按钮文本
const parseButtonText = computed(() => {
  if (isParsing.value) {
    return `解析中... ${parseSeconds.value}s`
  }
  return '开始解析'
})

const handleAddShot = async () => {
  if (!newShotScript.value.trim()) {
    alert('请输入剧本内容')
    return
  }

  await editorStore.createShot(newShotScript.value.trim())
  newShotScript.value = ''
  showAddModal.value = false
}

// Handle AI parse script
const handleParseScript = async () => {
  if (!fullScript.value.trim()) {
    alert('请输入剧本内容')
    return
  }

  isParsing.value = true
  parseSeconds.value = 0
  
  // 创建新的 AbortController
  parseAbortController = new AbortController()
  
  // 启动秒读计时器
  parseTimer = setInterval(() => {
    parseSeconds.value++
    console.log('[StoryboardTable] parseSeconds:', parseSeconds.value)
  }, 1000)
  
  let success = false
  
  try {
    console.log('[StoryboardTable] Starting AI parse...')
    await editorStore.parseAndCreateShots(fullScript.value.trim(), parseAbortController.signal)
    console.log('[StoryboardTable] AI parse completed successfully')
    success = true
  } catch (error: any) {
    // 如果是用户主动取消，不显示错误提示
    if (error.name === 'CanceledError' || error.message === 'canceled') {
      console.log('[StoryboardTable] Parse cancelled by user')
    } else {
      console.error('[StoryboardTable] Parse script failed:', error)
      alert(error.message || 'AI解析失败，请重试')
    }
  } finally {
    // 停止计时器
    if (parseTimer) {
      clearInterval(parseTimer)
      parseTimer = null
    }
    parseAbortController = null
    isParsing.value = false
    
    // 成功后关闭模态框并重置
    if (success) {
      fullScript.value = ''
      showParseModal.value = false
    }
  }
}

// ============== Excel 导入 ==============
const importFileInput = ref<HTMLInputElement | null>(null)
const supplementImageInput = ref<HTMLInputElement | null>(null)
const isImporting = ref(false)
const isSupplementingImages = ref(false)

const handleClickImport = () => {
  if (isImporting.value) return
  importFileInput.value?.click()
}

const handleClickSupplementImages = () => {
  if (isSupplementingImages.value) return
  supplementImageInput.value?.click()
}

const isImageFile = (file: File) => {
  return file.type.startsWith('image/') || /\.(png|jpe?g|webp|gif|bmp|svg)$/i.test(file.name)
}

const handleImportFileChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  // 先重置，避免选同一文件不触发 change
  input.value = ''
  if (!files.length) return
  if (!editorStore.projectId) {
    window.$message?.error('项目尚未加载')
    return
  }
  const file = files.find(f => f.name.toLowerCase().endsWith('.xlsx'))
  if (!file) {
    window.$message?.error('仅支持 .xlsx 格式的分镜表格')
    return
  }
  const assetFiles = files.filter(f => f !== file && isImageFile(f))

  isImporting.value = true
  try {
    const summary = await importApi.importStoryboardExcel(editorStore.projectId, file, assetFiles)
    // 刷新所有受影响的数据
    await Promise.all([
      editorStore.fetchShots(),
      editorStore.fetchCharacters(),
      editorStore.fetchScenes(),
      editorStore.fetchProps(),
    ])
    window.$message?.success(
      `导入成功：新增 ${summary.shotsCreated} 条分镜，涉及角色 ${summary.charactersCreated} / 场景 ${summary.scenesCreated} / 道具 ${summary.propsCreated}，匹配图片 ${summary.imagesMatched || 0} 张`
    )
  } catch (error: any) {
    console.error('[StoryboardTable] Excel import failed:', error)
    // axios 拦截器已 toast；这里兜底
    if (!error?.message?.includes('网络') && !window.$message) {
      alert(error?.message || '导入失败，请检查 Excel 格式')
    }
  } finally {
    isImporting.value = false
  }
}

const handleSupplementImageChange = async (event: Event) => {
  const input = event.target as HTMLInputElement
  const assetFiles = Array.from(input.files || []).filter(isImageFile)
  input.value = ''
  if (!assetFiles.length) return
  if (!editorStore.projectId) {
    window.$message?.error('项目尚未加载')
    return
  }

  isSupplementingImages.value = true
  try {
    const summary = await importApi.importAssetImages(editorStore.projectId, assetFiles)
    await Promise.all([
      editorStore.fetchShots(),
      editorStore.fetchCharacters(),
      editorStore.fetchScenes(),
      editorStore.fetchProps(),
    ])
    window.$message?.success(`补图完成：匹配图片 ${summary.imagesMatched || 0} 张`)
  } catch (error: any) {
    console.error('[StoryboardTable] Asset image import failed:', error)
    if (!error?.message?.includes('网络') && !window.$message) {
      alert(error?.message || '补图失败，请检查图片文件名')
    }
  } finally {
    isSupplementingImages.value = false
  }
}

// Handle cancel parse
const handleCancelParse = () => {
  if (isParsing.value && parseAbortController) {
    parseAbortController.abort()
    window.$message?.info('已取消解析')
  } else {
    // 如果没有在解析，直接关闭弹窗
    fullScript.value = ''
    showParseModal.value = false
  }
}

// Lifecycle
onMounted(() => {
  if (hasGeneratingAssets.value) {
    startPolling()
  }
})

onBeforeUnmount(() => {
  stopPolling()
  // 清理解析计时器
  if (parseTimer) {
    clearInterval(parseTimer)
    parseTimer = null
  }
  // 取消进行中的解析请求
  if (parseAbortController) {
    parseAbortController.abort()
    parseAbortController = null
  }
})

// Watch for generating status changes
computed(() => {
  if (hasGeneratingAssets.value && !pollingTimer) {
    startPolling()
  } else if (!hasGeneratingAssets.value && pollingTimer) {
    stopPolling()
  }
  return hasGeneratingAssets.value
})
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Toolbar -->
    <div class="flex items-center justify-between px-4 py-3 border-b border-border-default">
      <div class="flex items-center gap-3">
        <h3 class="text-text-primary text-base font-semibold">
          分镜表
          <span class="text-text-tertiary text-sm ml-2">({{ editorStore.shots.length }} 条)</span>
        </h3>

        <!-- Batch Actions (shown when selection exists) -->
        <div v-if="editorStore.hasSelection" class="flex items-center gap-2">
          <span class="text-text-tertiary text-sm">
            已选中 {{ editorStore.selectedShotIds.size }} 条
          </span>
          <button
            class="px-3 py-1 bg-bg-subtle text-text-secondary text-sm rounded hover:bg-bg-hover transition-colors flex items-center gap-1.5"
            @click="handleBatchGenerateShots"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2.5">
              <path d="m12 3-1.9 5.8-5.8 1.9 5.8 1.9L12 21l1.9-5.8 5.8-1.9-5.8-1.9L12 3z"></path>
            </svg>
            批量生成分镜图
          </button>
          <button
            class="px-3 py-1 bg-bg-subtle text-text-secondary text-sm rounded hover:bg-bg-hover transition-colors flex items-center gap-1.5"
            @click="handleBatchGenerateVideos"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <path stroke-linecap="round" d="M15.75 10.5l4.72-4.72a.75.75 0 011.28.53v11.38a.75.75 0 01-1.28.53l-4.72-4.72M4.5 18.75h9a2.25 2.25 0 002.25-2.25v-9a2.25 2.25 0 00-2.25-2.25h-9A2.25 2.25 0 002.25 7.5v9a2.25 2.25 0 002.25 2.25z"></path>
            </svg>
            批量生成视频
          </button>
          <button
            class="px-3 py-1 bg-red-500/20 text-red-400 text-sm rounded hover:bg-red-500/30 transition-colors"
            @click="handleDeleteSelected"
          >
            删除选中
          </button>
          <button
            v-if="editorStore.selectedShotIds.size >= 2"
            class="px-3 py-1 bg-bg-subtle text-text-secondary text-sm rounded hover:bg-bg-hover transition-colors flex items-center gap-1.5"
            @click="handleMergeSelected"
          >
            <svg class="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <path stroke-linecap="round" stroke-linejoin="round" d="M13.5 6H5.25A2.25 2.25 0 003 8.25v10.5A2.25 2.25 0 005.25 21h10.5A2.25 2.25 0 0018 18.75V10.5m-10.5 6L21 3m0 0h-5.25M21 3v5.25"></path>
            </svg>
            合并选中
          </button>
          <button
            class="px-3 py-1 bg-bg-hover text-text-tertiary text-sm rounded hover:bg-bg-hover transition-colors"
            @click="editorStore.deselectAll"
          >
            取消选择
          </button>
        </div>
      </div>

      <!-- Add Button Group -->
      <div class="flex items-center gap-2">
        <!-- Hidden file input for Excel import -->
        <input
          ref="importFileInput"
          type="file"
          accept=".xlsx,image/*"
          multiple
          class="hidden"
          @change="handleImportFileChange"
        />
        <input
          ref="supplementImageInput"
          type="file"
          accept="image/*"
          multiple
          webkitdirectory
          directory
          class="hidden"
          @change="handleSupplementImageChange"
        />

        <!-- Import Storyboard Excel Button -->
        <button
          class="flex items-center gap-2 px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isImporting"
          :title="'导入 MochiAni 风格的分镜表格 (.xlsx)，包含分镜/出场人物/场景/道具四个 Sheet'"
          @click="handleClickImport"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M7 10l5-5 5 5M12 5v12"></path>
          </svg>
          {{ isImporting ? '导入中…' : '导入分镜表格' }}
        </button>

        <button
          class="flex items-center gap-2 px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          :disabled="isSupplementingImages"
          :title="'选择角色/场景/道具图片文件夹，按 001_名称_gpt-image-2.png 这类文件名自动匹配补图'"
          @click="handleClickSupplementImages"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M3 7.5A2.5 2.5 0 015.5 5h3.2l1.6 2H18.5A2.5 2.5 0 0121 9.5v7A2.5 2.5 0 0118.5 19h-13A2.5 2.5 0 013 16.5v-9z"></path>
            <path stroke-linecap="round" stroke-linejoin="round" d="M12 11v5m-2.5-2.5h5"></path>
          </svg>
          {{ isSupplementingImages ? '补图中…' : '补充图片' }}
        </button>

        <!-- AI Parse Script Button -->
        <button
          class="flex items-center gap-2 px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors"
          @click="showParseModal = true"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z"></path>
          </svg>
          AI解析剧本
        </button>
        
        <!-- Add Shot Button -->
        <button
          class="flex items-center gap-2 px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors"
          @click="showAddModal = true"
        >
          <svg class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M12 4v16m8-8H4"></path>
          </svg>
          添加分镜
        </button>
      </div>
    </div>

    <!-- Table Container -->
    <div class="flex-1 overflow-auto">
      <!-- Empty State -->
      <div
        v-if="editorStore.shots.length === 0 && !editorStore.loading"
        class="flex flex-col items-center justify-center h-full"
      >
        <svg class="w-20 h-20 text-text-disabled mb-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
          <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M7 4v16M17 4v16M3 8h4m10 0h4M3 12h18M3 16h4m10 0h4M4 20h16a1 1 0 001-1V5a1 1 0 00-1-1H4a1 1 0 00-1 1v14a1 1 0 001 1z"></path>
        </svg>
        <p class="text-text-tertiary text-sm mb-2">暂无分镜</p>
        <p class="text-text-disabled text-xs mb-4">点击上方"添加分镜"按钮开始创作</p>
        <button
          class="px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors"
          @click="showAddModal = true"
        >
          添加第一条分镜
        </button>
      </div>

      <!-- Loading State -->
      <div
        v-else-if="editorStore.loading"
        class="flex items-center justify-center h-full"
      >
        <LoadingSpinner size="large" text="加载中..." />
      </div>

      <!-- Table -->
      <table v-else class="w-full">
        <!-- Table Header -->
        <thead class="sticky top-0 bg-bg-elevated border-b border-border-default z-10">
          <tr>
            <th class="px-3 py-3 text-left w-[48px]">
              <input
                type="checkbox"
                :checked="editorStore.allSelected"
                :indeterminate="editorStore.hasSelection && !editorStore.allSelected"
                @change="handleToggleSelectAll"
                class="w-4 h-4 rounded bg-bg-hover border-border-default text-text-primary focus:ring-2 focus:ring-[#00FFCC]/50 cursor-pointer"
              >
            </th>
            <th class="px-3 py-3 text-left w-[80px] text-text-tertiary text-xs font-semibold uppercase">
              ID
            </th>
            <th class="px-3 py-3 text-left flex-1 min-w-[200px] text-text-tertiary text-xs font-semibold uppercase">
              剧本
            </th>
            <th class="px-3 py-3 text-left w-[200px] text-text-tertiary text-xs font-semibold uppercase">
              角色画像
            </th>
            <th class="px-3 py-3 text-left w-[120px] text-text-tertiary text-xs font-semibold uppercase">
              场景画像
            </th>
            <th class="px-3 py-3 text-left w-[120px] text-text-tertiary text-xs font-semibold uppercase">
              道具画像
            </th>
            <th class="w-0 hidden"></th>
            <th class="px-3 py-3 text-left w-[120px] text-text-tertiary text-xs font-semibold uppercase">
              分镜图
            </th>
            <th class="px-3 py-3 text-left w-[120px] text-text-tertiary text-xs font-semibold uppercase">
              视频
            </th>
            <th class="px-3 py-3 text-left w-[140px] text-text-tertiary text-xs font-semibold uppercase">
              操作
            </th>
          </tr>
        </thead>

        <!-- Table Body -->
        <tbody>
          <StoryboardRow
            v-for="(shot, index) in editorStore.shots"
            :key="shot.id"
            :shot="shot"
            :selected="editorStore.selectedShotIds.has(shot.id)"
            :is-first="index === 0"
            :is-last="index === editorStore.shots.length - 1"
            :on-toggle-select="() => handleToggleShotSelection(shot.id)"
            :on-update-script="(text) => handleUpdateScript(shot.id, text)"
            :on-merge-up="() => handleMergeUp(shot.id)"
            :on-merge-down="() => handleMergeDown(shot.id)"
            :on-delete="() => handleDeleteShot(shot.id)"
          />
        </tbody>
      </table>
    </div>

    <!-- Add Shot Modal -->
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="showAddModal"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
        @click.self="showAddModal = false"
      >
        <div class="bg-bg-elevated w-[600px] rounded p-6 shadow-2xl pointer-events-auto">
          <h3 class="text-text-primary text-lg font-semibold mb-4">添加新分镜</h3>

          <textarea
            v-model="newShotScript"
            placeholder="输入剧本内容..."
            class="w-full h-32 px-4 py-3 bg-bg-base border border-border-default rounded text-text-primary text-sm resize-none focus:outline-none focus:ring-2 focus:ring-[#00FFCC]/50 placeholder-white/30"
            autofocus
          ></textarea>

          <div class="flex items-center justify-end gap-3 mt-4">
            <button
              class="px-4 py-2 bg-bg-hover text-text-tertiary text-sm rounded hover:bg-bg-hover transition-colors"
              @click="showAddModal = false; newShotScript = ''"
            >
              取消
            </button>
            <button
              class="px-4 py-2 bg-bg-subtle text-text-secondary font-medium text-sm rounded hover:bg-bg-hover transition-colors"
              @click="handleAddShot"
            >
              添加
            </button>
          </div>
        </div>
      </div>
    </Transition>

    <!-- AI Parse Script Modal -->
    <Transition
      enter-active-class="transition-all duration-200"
      leave-active-class="transition-all duration-200"
      enter-from-class="opacity-0 scale-95"
      leave-to-class="opacity-0 scale-95"
    >
      <div
        v-if="showParseModal"
        class="fixed inset-0 flex items-center justify-center z-50 p-4 pointer-events-none"
      >
        <div class="bg-bg-elevated w-[700px] rounded-xl p-8 shadow-2xl border border-border-default pointer-events-auto">
          <h3 class="text-text-primary text-xl font-bold mb-2">AI解析剧本</h3>
          <p class="text-text-tertiary text-sm mb-6">粘贴完整剧本，AI将自动提取角色、场景并拆分成多条分镜，同时自动绑定角色和场景到分镜</p>

          <textarea
            v-model="fullScript"
            placeholder="粘贴你的完整剧本或文案..."
            class="w-full h-80 px-4 py-4 bg-bg-base border border-border-default rounded-lg text-text-primary text-sm resize-none focus:outline-none focus:border-purple-500 focus:ring-1 focus:ring-purple-500 placeholder-text-tertiary"
            :disabled="isParsing"
          ></textarea>

          <div class="flex items-center justify-between mt-6">
            <span class="text-text-tertiary text-xs">提示：AI将自动提取角色形象、场景描述，并拆分成专业剧本格式，同时自动绑定角色和场景到分镜</span>
            <div class="flex items-center gap-3">
              <button
                class="px-6 py-2.5 bg-bg-hover text-text-secondary text-sm rounded-lg hover:bg-bg-subtle transition-colors"
                @click="handleCancelParse"
              >
                {{ isParsing ? '取消' : '关闭' }}
              </button>
              <button
                class="px-6 py-2.5 bg-purple-600 text-white font-semibold text-sm rounded-lg hover:bg-purple-700 transition-colors flex items-center gap-2 disabled:opacity-50 disabled:cursor-not-allowed"
                @click="handleParseScript"
                :disabled="isParsing || !fullScript.trim()"
              >
                <LoadingSpinner v-if="isParsing" size="small" />
                <svg v-else class="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M13 10V3L4 14h7v7l9-11h-7z"></path>
                </svg>
                <span v-if="isParsing">解析中... {{ parseSeconds }}s</span>
                <span v-else>开始解析</span>
              </button>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

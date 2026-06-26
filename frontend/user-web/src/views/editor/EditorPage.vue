<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useEditorStore } from '@/stores/editor'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { exportApi } from '@/api/export'
import type { ExportRequest, ExportResponse } from '@/types/api'
import StoryboardTable from '@/components/editor/StoryboardTable.vue'
import RightPanelManager from '@/components/editor/RightPanelManager.vue'
import BatchOperationBar from '@/components/editor/BatchOperationBar.vue'
import ModelConfigModal from './components/ModelConfigModal.vue'
import ExportModal from './components/ExportModal.vue'
import ExportProgressModal from './components/ExportProgressModal.vue'
import ProjectNameEditor from './components/ProjectNameEditor.vue'
import LoadingSpinner from '@/components/base/LoadingSpinner.vue'
import AppFooter from '@/components/base/AppFooter.vue'

const route = useRoute()
const router = useRouter()
const editorStore = useEditorStore()
const projectStore = useProjectStore()
const userStore = useUserStore()

const projectId = computed(() => Number(route.params.id))
const currentProject = computed(() => projectStore.currentProject)
const showModelConfigModal = ref(false)
const showExportModal = ref(false)
const showExportProgressModal = ref(false)
const exportJobId = ref<number | null>(null)

onMounted(async () => {
  if (projectId.value) {
    try {
      // Fetch project details and initialize editor
      await projectStore.fetchProjectDetail(projectId.value)
      await editorStore.initProject(projectId.value)
    } catch (error) {
      console.error('[EditorPage] Failed to initialize:', error)
      // TODO: Show error message or redirect
    }
  }

  // Register global keyboard shortcuts
  window.addEventListener('keydown', handleKeyDown)
})

onBeforeUnmount(() => {
  // Cleanup keyboard listener
  window.removeEventListener('keydown', handleKeyDown)
})

// Keyboard shortcuts handler
const handleKeyDown = (e: KeyboardEvent) => {
  // Ignore if user is typing in input/textarea/contenteditable
  const target = e.target as HTMLElement
  if (
    target instanceof HTMLInputElement ||
    target instanceof HTMLTextAreaElement ||
    target.isContentEditable
  ) {
    return
  }

  // Ctrl+Shift+Z - Redo
  if (e.ctrlKey && e.shiftKey && e.key === 'Z') {
    e.preventDefault()
    if (editorStore.canRedo) {
      editorStore.redo()
    }
    return
  }

  // Ctrl+Z - Undo (check this AFTER Ctrl+Shift+Z to avoid conflict)
  if (e.ctrlKey && e.key === 'z') {
    e.preventDefault()
    if (editorStore.canUndo) {
      editorStore.undo()
    }
    return
  }

  // Ctrl+A - Select All
  if (e.ctrlKey && e.key === 'a') {
    e.preventDefault()
    editorStore.selectAll()
    return
  }

  // Delete - Delete Selected Shots
  if (e.key === 'Delete' && editorStore.hasSelection) {
    e.preventDefault()
    handleDeleteSelected()
    return
  }
}

// Delete selected shots
const handleDeleteSelected = async () => {
  const count = editorStore.selectedShotIds.size
  if (count === 0) return

  if (confirm(`确定删除选中的 ${count} 条分镜吗？`)) {
    const shotIds = Array.from(editorStore.selectedShotIds)
    for (const shotId of shotIds) {
      await editorStore.deleteShot(shotId)
    }
    editorStore.deselectAll()
  }
}

const handleBack = () => {
  router.push('/')
}

const handleOpenModelConfig = () => {
  showModelConfigModal.value = true
}

const handleOpenExport = () => {
  showExportModal.value = true
}

const handleExportConfirm = async (request: ExportRequest) => {
  showExportModal.value = false

  try {
    console.log('[EditorPage] Submitting export task:', request)
    const response: ExportResponse = await exportApi.submitExportTask(projectId.value, request)

    exportJobId.value = response.jobId
    showExportProgressModal.value = true

    console.log('[EditorPage] Export task submitted:', response)
  } catch (error: any) {
    console.error('[EditorPage] Export task failed:', error)
    window.$message?.error(error.message || '提交导出任务失败')
  }
}

const handleExportComplete = () => {
  console.log('[EditorPage] Export completed successfully')
}

const handleExportFailed = (error: string) => {
  console.error('[EditorPage] Export failed:', error)
  window.$message?.error('导出失败: ' + error)
}

const projectTitle = computed(() => currentProject.value?.name || '未命名项目')

// 齿轮导出菜单
const showExportMenu = ref(false)

const handleExportAssets = async () => {
  console.log('[EditorPage] Export all generated images and videos')
  showExportMenu.value = false

  const shots = editorStore.shots
  if (shots.length === 0) {
    window.$message?.warning('当前项目没有分镜')
    return
  }

  // 收集所有可下载的资产
  const assetsToDownload: Array<{ url: string; filename: string; type: string }> = []

  shots.forEach(shot => {
    // 收集分镜图片
    if (shot.shotImage.status === 'READY' && shot.shotImage.url) {
      const ext = shot.shotImage.url.split('.').pop() || 'jpg'
      assetsToDownload.push({
        url: shot.shotImage.url,
        filename: `分镜${shot.shotNo}_图片.${ext}`,
        type: '分镜图'
      })
    }

    // 收集视频
    if (shot.video.status === 'READY' && shot.video.url) {
      const ext = shot.video.url.split('.').pop() || 'mp4'
      assetsToDownload.push({
        url: shot.video.url,
        filename: `分镜${shot.shotNo}_视频.${ext}`,
        type: '视频'
      })
    }
  })

  if (assetsToDownload.length === 0) {
    window.$message?.warning('当前项目没有已生成的图片或视频')
    return
  }

  window.$message?.info(`开始下载 ${assetsToDownload.length} 个文件...`)

  // 逐个下载资产文件
  let successCount = 0
  let failCount = 0

  for (const asset of assetsToDownload) {
    try {
      // 创建下载链接
      const link = document.createElement('a')
      link.href = asset.url
      link.download = asset.filename
      link.target = '_blank'
      document.body.appendChild(link)
      link.click()
      document.body.removeChild(link)

      successCount++
      console.log(`[EditorPage] Downloaded: ${asset.filename}`)

      // 添加延迟避免浏览器阻止多个下载
      await new Promise(resolve => setTimeout(resolve, 300))
    } catch (error) {
      console.error(`[EditorPage] Failed to download ${asset.filename}:`, error)
      failCount++
    }
  }

  // 显示结果消息
  if (failCount === 0) {
    window.$message?.success(`成功下载 ${successCount} 个文件`)
  } else {
    window.$message?.warning(`下载完成：成功 ${successCount} 个，失败 ${failCount} 个`)
  }
}

const handleExportScripts = () => {
  console.log('[EditorPage] Export all shot scripts')
  showExportMenu.value = false

  // 生成所有分镜文本
  const shots = editorStore.shots
  if (shots.length === 0) {
    window.$message?.warning('当前项目没有分镜')
    return
  }

  // 构建文本内容
  let content = `项目名称：${projectTitle.value}\n`
  content += `\n==================== 分镜列表 ====================\n\n`

  shots.forEach(shot => {
    content += `【分镜 #${shot.shotNo}】\n`
    content += `${shot.scriptText}\n`

    // 添加角色信息
    if (shot.characters && shot.characters.length > 0) {
      const charNames = shot.characters.map(c => c.characterName).join('、')
      content += `角色：${charNames}\n`
    }

    // 添加场景信息
    if (shot.scene) {
      content += `场景：${shot.scene.sceneName}\n`
    }

    content += `\n`
  })

  // 创建下载链接
  const blob = new Blob([content], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `${projectTitle.value}_分镜文本_${new Date().toISOString().slice(0, 10)}.txt`
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)

  window.$message?.success('分镜文本导出成功')
}
</script>

<template>
  <div class="flex flex-col h-screen bg-bg-base text-text-primary overflow-hidden" @click="showExportMenu = false">
    <!-- Header -->
    <header class="flex-shrink-0 bg-bg-elevated p-3 flex justify-between items-center border-b border-border-default">
      <!-- Left Section -->
      <div class="flex items-center gap-4">
        <!-- Brand Logo -->
        <div class="flex items-center gap-2 cursor-pointer hover:opacity-80 transition-opacity" @click="handleBack">
          <div class="w-8 h-8 rounded-lg overflow-hidden flex-shrink-0">
            <img src="@/assets/logo.svg" alt="红鹦鹉漫剧" class="w-full h-full object-cover" />
          </div>
          <span class="text-lg font-bold text-text-primary leading-none">红鹦鹉漫剧</span>
        </div>

        <!-- Divider -->
        <div class="w-px h-6 bg-border-default"></div>

        <!-- Project Name Editor -->
        <ProjectNameEditor :project-id="projectId" />

        <!-- Model Config Button -->
        <button class="btn btn-secondary text-sm" @click="handleOpenModelConfig">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="3"></circle>
            <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
          </svg>
          <span class="font-semibold">模型配置</span>
        </button>

        <!-- Export Button -->
        <button class="btn btn-primary text-sm" @click="handleOpenExport">
          <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"></path>
            <polyline points="7 10 12 15 17 10"></polyline>
            <line x1="12" x2="12" y1="15" y2="3"></line>
          </svg>
          <span class="font-semibold">导出项目</span>
        </button>

        <!-- Export Menu (Gear Icon) -->
        <div class="relative">
          <button
            @click.stop="showExportMenu = !showExportMenu"
            class="p-2 rounded hover:bg-bg-hover transition-colors"
            title="更多导出选项"
          >
            <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-secondary">
              <circle cx="12" cy="12" r="3"></circle>
              <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 0 1 0 2.83 2 2 0 0 1-2.83 0l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-2 2 2 2 0 0 1-2-2v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 0 1-2.83 0 2 2 0 0 1 0-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1-2-2 2 2 0 0 1 2-2h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 0 1 0-2.83 2 2 0 0 1 2.83 0l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 2-2 2 2 0 0 1 2 2v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 0 1 2.83 0 2 2 0 0 1 0 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 2 2 2 2 0 0 1-2 2h-.09a1.65 1.65 0 0 0-1.51 1z"></path>
            </svg>
          </button>

          <!-- Dropdown Menu -->
          <Transition
            enter-active-class="transition-all duration-200 ease-out"
            enter-from-class="opacity-0 scale-95 translate-y-[-10px]"
            enter-to-class="opacity-100 scale-100 translate-y-0"
            leave-active-class="transition-all duration-150 ease-in"
            leave-from-class="opacity-100 scale-100 translate-y-0"
            leave-to-class="opacity-0 scale-95 translate-y-[-10px]"
          >
            <div
              v-if="showExportMenu"
              class="absolute top-full right-0 mt-2 w-56 bg-bg-elevated border border-border-default rounded shadow-2xl overflow-hidden z-50"
              @click.stop
            >
              <!-- 导出所有图片视频 -->
              <button
                @click="handleExportAssets"
                class="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-bg-subtle transition-colors border-b border-border-subtle"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-primary flex-shrink-0">
                  <rect x="3" y="3" width="18" height="18" rx="2" ry="2"></rect>
                  <circle cx="8.5" cy="8.5" r="1.5"></circle>
                  <polyline points="21 15 16 10 5 21"></polyline>
                </svg>
                <div class="flex-1">
                  <p class="text-text-primary text-sm font-medium">导出所有图片视频</p>
                  <p class="text-text-tertiary text-xs mt-0.5">下载所有生成的资产文件</p>
                </div>
              </button>

              <!-- 导出所有分镜文本 -->
              <button
                @click="handleExportScripts"
                class="w-full flex items-center gap-3 px-4 py-3 text-left hover:bg-bg-subtle transition-colors"
              >
                <svg xmlns="http://www.w3.org/2000/svg" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="text-text-primary flex-shrink-0">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path>
                  <polyline points="14 2 14 8 20 8"></polyline>
                  <line x1="16" y1="13" x2="8" y2="13"></line>
                  <line x1="16" y1="17" x2="8" y2="17"></line>
                  <polyline points="10 9 9 9 8 9"></polyline>
                </svg>
                <div class="flex-1">
                  <p class="text-text-primary text-sm font-medium">导出所有分镜文本</p>
                  <p class="text-text-tertiary text-xs mt-0.5">导出为TXT文本文件</p>
                </div>
              </button>
            </div>
          </Transition>
        </div>
      </div>

      <!-- Center Section -->
      <div class="flex items-center gap-4 relative">
        <button class="flex items-center gap-2 text-sm font-semibold text-white/70 hover:text-text-primary transition-colors">
          <svg viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" width="16" height="16" fill="currentColor">
            <path d="M158.983468 70.62069C120.00535 70.62069 88.275862 102.115222 88.275862 141.080964L88.275862 953.539725C88.275862 992.54242 119.980085 1024 158.983468 1024L865.016532 1024C903.99465 1024 935.724138 992.505468 935.724138 953.539725L935.724138 141.080964C935.724138 102.07827 904.019915 70.62069 865.016532 70.62069L759.172414 70.62069 794.482759 105.931034 794.482759 52.913081C794.482759 23.807011 770.796703 0 741.578417 0L282.421583 0C253.09101 0 229.517241 23.689781 229.517241 52.913081L229.517241 105.931034 264.827586 70.62069 158.983468 70.62069ZM300.137931 141.241379 300.137931 105.931034 300.137931 52.913081C300.137931 62.615111 292.171564 70.62069 282.421583 70.62069L741.578417 70.62069C731.713218 70.62069 723.862069 62.729446 723.862069 52.913081L723.862069 105.931034 723.862069 141.241379 759.172414 141.241379 865.016532 141.241379C865.140118 141.241379 865.103448 953.539725 865.103448 953.539725 865.103448 953.384748 158.983468 953.37931 158.983468 953.37931 158.859882 953.37931 158.896552 141.080964 158.896552 141.080964 158.896552 141.235942 264.827586 141.241379 264.827586 141.241379L300.137931 141.241379ZM723.862069 158.948988C723.862069 149.246958 731.828436 141.241379 741.578417 141.241379L282.421583 141.241379C292.286782 141.241379 300.137931 149.132623 300.137931 158.948988L300.137931 105.931034C300.137931 86.429678 284.328942 70.62069 264.827586 70.62069 245.32623 70.62069 229.517241 86.429678 229.517241 105.931034L229.517241 158.948988C229.517241 188.055058 253.203297 211.862069 282.421583 211.862069L741.578417 211.862069C770.90899 211.862069 794.482759 188.172288 794.482759 158.948988L794.482759 105.931034C794.482759 86.429678 778.67377 70.62069 759.172414 70.62069 739.671058 70.62069 723.862069 86.429678 723.862069 105.931034L723.862069 158.948988ZM723.862069 441.37931C743.363425 441.37931 759.172414 425.570322 759.172414 406.068966 759.172414 386.567609 743.363425 370.758621 723.862069 370.758621L300.137931 370.758621C280.636575 370.758621 264.827586 386.567609 264.827586 406.068966 264.827586 425.570322 280.636575 441.37931 300.137931 441.37931L723.862069 441.37931ZM300.137931 547.310345C280.636575 547.310345 264.827586 563.119334 264.827586 582.62069 264.827586 602.122046 280.636575 617.931034 300.137931 617.931034L723.862069 617.931034C743.363425 617.931034 759.172414 602.122046 759.172414 582.62069 759.172414 563.119334 743.363425 547.310345 723.862069 547.310345L300.137931 547.310345ZM300.137931 723.862069C280.636575 723.862069 264.827586 739.671058 264.827586 759.172414 264.827586 778.67377 280.636575 794.482759 300.137931 794.482759L582.62069 794.482759C602.122046 794.482759 617.931034 778.67377 617.931034 759.172414 617.931034 739.671058 602.122046 723.862069 582.62069 723.862069L300.137931 723.862069Z"></path>
          </svg>
          <span>生成记录</span>
        </button>
      </div>

      <!-- Right Section -->
      <div class="flex items-center gap-4">
        <!-- Undo Button -->
        <button
          :disabled="!editorStore.canUndo"
          @click="editorStore.undo"
          class="flex items-center gap-2 p-2 rounded hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="撤销 (Ctrl+Z)"
        >
          <svg version="1.1" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 1024 1024" fill="currentColor" class="w-3 h-5">
            <path d="M32.143 329.992l220.444 220.444q7.749 7.751 17.879 11.946t21.092 4.195q5.428 0 10.753-1.059t10.338-3.135 9.528-5.093 8.349-6.853q3.841-3.839 6.855-8.351t5.092-9.528 3.135-10.338 1.058-10.753q0-10.962-4.193-21.091t-11.947-17.88l-126.364-126.364h415.541q113.923 0 196.313 81.531 81.763 80.911 81.763 191.544 0 114.895-80.011 196.464t-193.399 81.569h-167.48q-5.428 0-10.753 1.058t-10.338 3.135-9.528 5.092-8.349 6.855q-3.836 3.836-6.855 8.349-3.014 4.513-5.092 9.528t-3.135 10.338-1.058 10.753 1.058 10.753 3.135 10.338 5.092 9.528q3.019 4.513 6.855 8.349t8.349 6.855q4.513 3.014 9.528 5.092t10.338 3.135 10.753 1.058h167.48q159.671 0 272.087-114.608 111.545-113.722 111.545-273.648 0-156.626-114.457-269.89-114.602-113.408-273.84-113.408h-415.541l126.364-126.364q7.754-7.751 11.947-17.88t4.193-21.091q0-5.427-1.058-10.753t-3.135-10.338-5.092-9.527-6.855-8.351q-3.836-3.837-8.349-6.853t-9.528-5.093-10.338-3.135-10.753-1.059q-10.961 0-21.092 4.195t-17.879 11.946l-0.023 0.021-220.423 220.424q-7.754 7.751-11.947 17.88t-4.193 21.091 4.193 21.091 11.947 17.88z"></path>
          </svg>
        </button>

        <!-- Redo Button -->
        <button
          :disabled="!editorStore.canRedo"
          @click="editorStore.redo"
          class="flex items-center gap-2 p-2 rounded hover:bg-bg-hover transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
          title="重做 (Ctrl+Shift+Z)"
        >
          <svg version="1.1" xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 1024 1024" fill="currentColor" class="w-3 h-5 transform scale-x-[-1]">
            <path d="M32.143 329.992l220.444 220.444q7.749 7.751 17.879 11.946t21.092 4.195q5.428 0 10.753-1.059t10.338-3.135 9.528-5.093 8.349-6.853q3.841-3.839 6.855-8.351t5.092-9.528 3.135-10.338 1.058-10.753q0-10.962-4.193-21.091t-11.947-17.88l-126.364-126.364h415.541q113.923 0 196.313 81.531 81.763 80.911 81.763 191.544 0 114.895-80.011 196.464t-193.399 81.569h-167.48q-5.428 0-10.753 1.058t-10.338 3.135-9.528 5.092-8.349 6.855q-3.836 3.836-6.855 8.349-3.014 4.513-5.092 9.528t-3.135 10.338-1.058 10.753 1.058 10.753 3.135 10.338 5.092 9.528q3.019 4.513 6.855 8.349t8.349 6.855q4.513 3.014 9.528 5.092t10.338 3.135 10.753 1.058h167.48q159.671 0 272.087-114.608 111.545-113.722 111.545-273.648 0-156.626-114.457-269.89-114.602-113.408-273.84-113.408h-415.541l126.364-126.364q7.754-7.751 11.947-17.88t4.193-21.091q0-5.427-1.058-10.753t-3.135-10.338-5.092-9.527-6.855-8.351q-3.836-3.837-8.349-6.853t-9.528-5.093-10.338-3.135-10.753-1.059q-10.961 0-21.092 4.195t-17.879 11.946l-0.023 0.021-220.423 220.424q-7.754 7.751-11.947 17.88t-4.193 21.091 4.193 21.091 11.947 17.88z"></path>
          </svg>
        </button>

        <!-- Divider -->
        <div class="w-px h-6 bg-border-default"></div>

        <!-- 版权署名 -->
        <AppFooter compact class="whitespace-nowrap" />
      </div>
    </header>

    <!-- Main Content -->
    <main class="flex-grow flex overflow-hidden">
      <!-- Table Area -->
      <div class="relative flex-grow flex flex-col p-4 gap-4 flex-1 w-[0px]">
        <div class="flex-grow bg-bg-elevated rounded-[24px] p-2 flex flex-col overflow-hidden relative">
          <!-- Storyboard Table -->
          <StoryboardTable />
        </div>
      </div>

      <!-- Right Panel Manager -->
      <RightPanelManager />
    </main>

    <!-- Model Config Modal -->
    <ModelConfigModal
      :visible="showModelConfigModal"
      @update:visible="showModelConfigModal = $event"
      @close="showModelConfigModal = false"
    />

    <!-- Export Modal -->
    <ExportModal
      v-if="showExportModal"
      @close="showExportModal = false"
      @confirm="handleExportConfirm"
    />

    <!-- Export Progress Modal -->
    <ExportProgressModal
      v-if="showExportProgressModal && exportJobId !== null"
      :job-id="exportJobId"
      @close="showExportProgressModal = false"
      @complete="handleExportComplete"
      @failed="handleExportFailed"
    />

    <!-- Batch Operation Bar (底部浮动操作栏) -->
    <BatchOperationBar />
  </div>
</template>

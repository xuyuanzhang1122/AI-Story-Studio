<script setup lang="ts">
// {{CODE-Cycle-Integration:
//   Task_ID: [#问题1]
//   Timestamp: 2026-01-03T08:05:00+08:00
//   Phase: [D-Develop]
//   Context-Analysis: "Refactoring HomePage to show folders as card grid. Adding folder selection logic and updating CreateProjectModal integration."
//   Principle_Applied: "Context-First-Mandate, Aether-Aesthetics, Verification-Mindset"
// }}
// {{START_MODIFICATIONS}}

import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useProjectStore } from '@/stores/project'
import { useUserStore } from '@/stores/user'
import { walletApi } from '@/api/apis'
import type { ProjectVO, FolderVO } from '@/types/api'
import LobeHero from '@/components/lobe/LobeHero.vue'
import ProjectTable from '@/components/home/ProjectTable.vue'
import CreateProjectModal from '@/components/home/CreateProjectModal.vue'
import FolderModal from '@/components/home/FolderModal.vue'
import MoveProjectModal from '@/components/home/MoveProjectModal.vue'

const router = useRouter()
const projectStore = useProjectStore()
const userStore = useUserStore()

const heroTitle = '红鹦鹉 <b>漫剧</b>'

const searchKeyword = ref('')
const showSearchInput = ref(false)
const showCreateProjectModal = ref(false)
const showCreateFolderModal = ref(false)
const showEditFolderModal = ref(false)
const showMoveProjectModal = ref(false)
const editingFolder = ref<FolderVO | null>(null)  // For folder edit modal
const selectedProject = ref<ProjectVO | null>(null)
const loading = ref(false)

onMounted(async () => {
  // Fetch user profile and wallet balance
  try {
    await Promise.all([
      userStore.fetchProfile(),
      projectStore.fetchProjects(),
      projectStore.fetchFolders(),
    ])

    // Fetch wallet balance separately (optional, may not be implemented yet)
    fetchWalletBalance().catch(() => {
      // Silently fail if wallet endpoint doesn't exist
      console.warn('Wallet API not available')
    })
  } catch (error) {
    console.error('Failed to fetch initial data:', error)
  }
})

const fetchWalletBalance = async () => {
  try {
    const wallet = await walletApi.getBalance()
    userStore.setPoints(wallet.balance)
  } catch (error) {
    console.error('Failed to fetch wallet balance:', error)
  }
}

const handleSearch = async () => {
  await projectStore.fetchProjects(1, searchKeyword.value)
}

const handleCreateProject = async (data: {
  name: string
  folderId?: number
  rawText: string
}) => {
  loading.value = true
  try {
    console.log('[HomePage] Creating project with data:', data)

    // Create project with simplified fields (use default aspectRatio)
    const project = await projectStore.createProject({
      name: data.name,
      folderId: data.folderId,
      rawText: data.rawText,
      aspectRatio: '16:9',  // Default aspect ratio
      styleCode: '',  // Empty style code
    })

    console.log('[HomePage] Project created:', project)

    if (!project || !project.id) {
      throw new Error('项目创建失败：返回数据无效')
    }

    showCreateProjectModal.value = false

    // Refresh project list after creation
    await projectStore.fetchProjects()

    // Navigate to editor
    router.push(`/editor/${project.id}`)
  } catch (error: any) {
    console.error('[HomePage] Create project error:', error)
    alert(error.message || '创建项目失败')
  } finally {
    loading.value = false
  }
}

const handleCreateFolder = async (name: string) => {
  loading.value = true
  try {
    await projectStore.createFolder({ name })
    showCreateFolderModal.value = false

    // Refresh folder list after creation
    await projectStore.fetchFolders()
  } catch (error: any) {
    alert(error.message || '创建文件夹失败')
  } finally {
    loading.value = false
  }
}

const handleEditFolder = (folder: FolderVO) => {
  editingFolder.value = folder
  showEditFolderModal.value = true
}

const handleUpdateFolder = async (name: string) => {
  if (!editingFolder.value) return

  loading.value = true
  try {
    await projectStore.updateFolder(editingFolder.value.id, { name })
    showEditFolderModal.value = false
    editingFolder.value = null

    // Refresh folder list to ensure UI is updated
    await projectStore.fetchFolders()
  } catch (error: any) {
    alert(error.message || '更新文件夹失败')
  } finally {
    loading.value = false
  }
}

const handleDeleteFolder = async (folder: FolderVO) => {
  if (!confirm(`确定要删除文件夹"${folder.name}"吗？文件夹内的项目将移至未分类。`)) {
    return
  }

  try {
    await projectStore.deleteFolder(folder.id)
  } catch (error: any) {
    alert(error.message || '删除文件夹失败')
  }
}

const handleDeleteProject = async (project: ProjectVO) => {
  if (!confirm(`确定要删除项目"${project.title}"吗？此操作不可撤销。`)) {
    return
  }

  try {
    await projectStore.deleteProject(project.id)
  } catch (error: any) {
    alert(error.message || '删除项目失败')
  }
}

const handleMoveProject = (project: ProjectVO) => {
  selectedProject.value = project
  showMoveProjectModal.value = true
}

const handleConfirmMoveProject = async (folderId: number | null) => {
  if (!selectedProject.value) return

  loading.value = true
  try {
    await projectStore.moveProjectToFolder(selectedProject.value.id, folderId)
    showMoveProjectModal.value = false
    selectedProject.value = null

    // Refresh project list to reflect the folder change
    await projectStore.fetchProjects()
  } catch (error: any) {
    alert(error.message || '移动项目失败')
  } finally {
    loading.value = false
  }
}

const handleEditProject = (project: ProjectVO) => {
  router.push(`/editor/${project.id}`)
}

// Folder selection logic
const selectedFolder = computed(() => {
  if (!projectStore.selectedFolderId) return null
  return projectStore.getFolderById(projectStore.selectedFolderId)
})

const handleShowAllProjects = async () => {
  projectStore.setSelectedFolder(null)
  await projectStore.fetchProjects(1, searchKeyword.value)
}

// {{END_MODIFICATIONS}}

</script>

<template>
  <div class="home-light-page h-screen overflow-hidden">
    <div class="h-full max-w-[1400px] mx-auto px-6 py-4 flex flex-col">
      <!-- Hero 区域：占据剩余空间 -->
      <div class="home-hero-panel flex-1 mb-4 rounded-xl overflow-hidden min-h-[220px]">
        <LobeHero
          :title="heroTitle"
          description="用 AI（爱）叙事，传递希望与快乐"
        />
      </div>

      <!-- 项目区域：固定高度，内部可滚动 -->
      <div class="home-project-panel flex-shrink-0 flex flex-col overflow-hidden">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between mb-3">
          <div class="flex items-center gap-3 min-w-0">
            <button
              v-if="selectedFolder"
              class="btn btn-ghost text-sm"
              @click="handleShowAllProjects"
            >
              <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 19l-7-7 7-7" />
              </svg>
              返回
            </button>
            <div>
              <h2 class="text-base font-semibold text-text-primary">
                {{ selectedFolder ? `${selectedFolder.name} 中的项目` : '所有项目' }}
              </h2>
              <p class="text-xs text-text-tertiary">
                共 {{ projectStore.total }} 个项目 · {{ projectStore.folders.length }} 个文件夹
              </p>
            </div>
          </div>
          <div class="flex flex-wrap items-center gap-2">
            <!-- 新建项目按钮 -->
            <button
              class="btn btn-secondary text-sm whitespace-nowrap"
              @click="showCreateProjectModal = true"
            >
              <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M12 4v16m8-8H4" />
              </svg>
              新建项目
            </button>
            <!-- 新建文件夹按钮 -->
            <button
              class="btn btn-secondary text-sm whitespace-nowrap"
              @click="showCreateFolderModal = true"
            >
              <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M3 7v10a2 2 0 002 2h14a2 2 0 002-2V9a2 2 0 00-2-2h-6l-2-2H5a2 2 0 00-2 2z" />
              </svg>
              新建文件夹
            </button>
            <!-- 搜索按钮 -->
            <button
              v-if="!showSearchInput"
              class="btn btn-secondary text-sm whitespace-nowrap"
              @click="showSearchInput = true"
            >
              <svg class="w-4 h-4 mr-1.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              搜索
            </button>
            <!-- 搜索输入框 -->
            <div v-else class="flex min-w-[220px] items-center gap-2 bg-bg-subtle rounded-lg px-3 py-1.5 border border-border-default">
              <svg class="w-4 h-4 text-text-tertiary flex-shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                <path stroke-linecap="round" stroke-linejoin="round" d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
              </svg>
              <input
                v-model="searchKeyword"
                type="text"
                placeholder="搜索项目..."
                class="w-full bg-transparent text-sm text-text-primary placeholder-text-tertiary focus:outline-none"
                @keydown.enter="handleSearch"
                @keydown.esc="showSearchInput = false; searchKeyword = ''"
                autofocus
              />
              <button
                v-if="searchKeyword"
                class="text-text-tertiary hover:text-text-primary"
                @click="searchKeyword = ''"
              >
                <svg class="w-3.5 h-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
              <button
                class="text-text-tertiary hover:text-text-primary"
                @click="showSearchInput = false; searchKeyword = ''"
              >
                <svg class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M6 18L18 6M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
        </div>
        <div class="flex-1 min-h-0">
          <ProjectTable
            @edit-folder="handleEditFolder"
            @delete-folder="handleDeleteFolder"
            @delete-project="handleDeleteProject"
            @move-project="handleMoveProject"
          />
        </div>
      </div>
    </div>

    <!-- Modals -->
    <CreateProjectModal
      :show="showCreateProjectModal"
      :loading="loading"
      :folder-id="projectStore.selectedFolderId"
      @close="showCreateProjectModal = false"
      @confirm="handleCreateProject"
    />

    <FolderModal
      :show="showCreateFolderModal"
      :loading="loading"
      title="新建文件夹"
      @close="showCreateFolderModal = false"
      @confirm="handleCreateFolder"
    />

    <FolderModal
      :show="showEditFolderModal"
      :loading="loading"
      title="编辑文件夹"
      :folder-name="editingFolder?.name || ''"
      @close="showEditFolderModal = false"
      @confirm="handleUpdateFolder"
    />

    <MoveProjectModal
      :show="showMoveProjectModal"
      :loading="loading"
      :project="selectedProject"
      @close="showMoveProjectModal = false"
      @confirm="handleConfirmMoveProject"
    />
  </div>
</template>

<style scoped>
.home-light-page {
  background:
    linear-gradient(180deg, #ffffff 0%, #f8fafc 52%, #f2f5f8 100%);
  color: #101828;
}

.home-hero-panel {
  position: relative;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: #ffffff;
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.08);
}

.home-project-panel {
  height: 312px;
}

.home-light-page :deep(.text-text-primary) {
  color: #101828;
}

.home-light-page :deep(.text-text-secondary) {
  color: #475467;
}

.home-light-page :deep(.text-text-tertiary) {
  color: #667085;
}

.home-light-page :deep(.card) {
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.06);
}

.home-light-page :deep(.card:hover) {
  border-color: rgba(15, 23, 42, 0.12);
  box-shadow: 0 16px 48px rgba(15, 23, 42, 0.08);
}

.home-light-page :deep(.bg-bg-subtle) {
  background-color: #f3f6f9;
}

.home-light-page :deep(.bg-bg-hover),
.home-light-page :deep(.hover\:bg-bg-hover:hover) {
  background-color: #eef2f6;
}

.home-light-page :deep(.border-border-default),
.home-light-page :deep(.border-border-subtle) {
  border-color: rgba(15, 23, 42, 0.08);
}

.home-light-page :deep(.divide-border-subtle > :not([hidden]) ~ :not([hidden])) {
  border-color: rgba(15, 23, 42, 0.07);
}

.home-light-page :deep(.btn-secondary) {
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: #ffffff;
  color: #344054;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.04);
}

.home-light-page :deep(.btn-secondary:hover:not(:disabled)) {
  border-color: rgba(15, 23, 42, 0.18);
  background: #f8fafc;
  color: #101828;
}

.home-light-page :deep(.btn-ghost) {
  color: #475467;
}

.home-light-page :deep(.btn-ghost:hover:not(:disabled)) {
  background: #eef2f6;
  color: #101828;
}

.home-light-page :deep(input) {
  color: #101828;
}

.home-light-page :deep(input::placeholder) {
  color: #98a2b3;
}

@media (max-width: 768px) {
  .home-light-page > div {
    padding-inline: 16px;
  }

  .home-hero-panel {
    min-height: 240px;
  }

  .home-project-panel {
    height: 340px;
  }
}
</style>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useEditorStore } from '@/stores/editor'
import { usePanelManagerStore } from '@/stores/panelManager'
import CharacterLibraryModal from '../CharacterLibraryModal.vue'
import SceneLibraryModal from '../SceneLibraryModal.vue'
import PropLibraryModal from '../PropLibraryModal.vue'
import AssetHoverPreview from '../AssetHoverPreview.vue'
import { characterApi } from '@/api/character'
import { sceneApi } from '@/api/scene'
import { propApi } from '@/api/prop'

const editorStore = useEditorStore()
const panelManagerStore = usePanelManagerStore()

// Props定义 - 接收外部传入的activeTab参数
const props = withDefaults(defineProps<{
  activeTab?: 'characters' | 'scenes' | 'props'
}>(), {
  activeTab: 'characters'
})

defineEmits<{
  close: []
}>()

// 标签页状态 - 使用props传入的值初始化
const activeSidebarTab = ref<'characters' | 'scenes' | 'props'>(props.activeTab)

// 搜索状态
const characterSearchQuery = ref('')
const sceneSearchQuery = ref('')
const propSearchQuery = ref('')

// 分类状态
const selectedCharacterCategory = ref<number | null>(null)
const selectedSceneCategory = ref<number | null>(null)

// 角色分类列表
const characterCategories = ref<Array<{ id: number; name: string; count: number }>>([])

// 场景分类列表
const sceneCategories = ref<Array<{ id: number; name: string; count: number }>>([])

// 库模态框状态
const showCharacterLibraryModal = ref(false)
const showSceneLibraryModal = ref(false)
const showPropLibraryModal = ref(false)
const assetPanelRef = ref<HTMLElement | null>(null)

const hoverPreview = ref({
  visible: false,
  name: '',
  imageUrl: null as string | null,
  description: null as string | null,
  x: 0,
  y: 0,
})
const pendingHoverKey = ref<string | null>(null)
let hoverPreviewTimer: ReturnType<typeof setTimeout> | null = null
const detailPreviewWidth = 760
const detailPreviewGap = 12
const detailPreviewDelay = 680

const getCharacterName = (character: any) => {
  return character?.displayName || character?.name || character?.libraryCharacterName || '未命名角色'
}

const getSceneName = (scene: any) => {
  return scene?.displayName || scene?.name || scene?.librarySceneName || '未命名场景'
}

const getPropName = (prop: any) => {
  return prop?.displayName || prop?.name || '未命名道具'
}

const getCharacterDescription = (character: any) => {
  return character?.finalDescription || character?.description || character?.overrideDescription || ''
}

const getSceneDescription = (scene: any) => {
  return scene?.finalDescription || scene?.description || scene?.overrideDescription || ''
}

const getPropDescription = (prop: any) => {
  return prop?.description || prop?.finalDescription || prop?.overrideDescription || ''
}

const clearHoverPreviewTimer = () => {
  if (hoverPreviewTimer) {
    clearTimeout(hoverPreviewTimer)
    hoverPreviewTimer = null
  }
}

const getDetailPreviewPosition = () => {
  const panelRect = assetPanelRef.value?.getBoundingClientRect()
  const viewportWidth = typeof window !== 'undefined' ? window.innerWidth : 1280

  if (!panelRect) {
    return { x: Math.max(12, viewportWidth - detailPreviewWidth - 380), y: 12 }
  }

  return {
    x: Math.max(12, panelRect.left - detailPreviewWidth - detailPreviewGap),
    y: Math.max(12, panelRect.top + detailPreviewGap),
  }
}

const showHoverPreview = (
  payload: { key: string; name: string; imageUrl?: string | null; description?: string | null }
) => {
  clearHoverPreviewTimer()
  pendingHoverKey.value = payload.key
  hoverPreview.value.visible = false

  hoverPreviewTimer = setTimeout(() => {
    if (pendingHoverKey.value !== payload.key) return
    const position = getDetailPreviewPosition()
    hoverPreview.value = {
      visible: true,
      name: payload.name,
      imageUrl: payload.imageUrl || null,
      description: payload.description || null,
      x: position.x,
      y: position.y,
    }
    pendingHoverKey.value = null
    hoverPreviewTimer = null
  }, detailPreviewDelay)
}

const moveHoverPreview = () => {
  if (!hoverPreview.value.visible) return
  const position = getDetailPreviewPosition()
  hoverPreview.value = {
    ...hoverPreview.value,
    x: position.x,
    y: position.y,
  }
}

const hideHoverPreview = () => {
  clearHoverPreviewTimer()
  pendingHoverKey.value = null
  hoverPreview.value.visible = false
}

onUnmounted(clearHoverPreviewTimer)

// 加载分类列表
const loadCharacterCategories = async () => {
  try {
    const categories = await characterApi.getCharacterCategories()
    characterCategories.value = categories
  } catch (error) {
    console.error('[DefaultPanel] Failed to load character categories:', error)
  }
}

const loadSceneCategories = async () => {
  try {
    const categories = await sceneApi.getSceneCategories()
    sceneCategories.value = categories
  } catch (error) {
    console.error('[DefaultPanel] Failed to load scene categories:', error)
  }
}

// 过滤后的角色列表
const filteredCharacters = computed(() => {
  let chars = editorStore.characters

  // 按分类过滤
  if (selectedCharacterCategory.value !== null) {
    chars = chars.filter(c => (c as any).categoryId === selectedCharacterCategory.value)
  }

  // 按搜索关键词过滤
  if (characterSearchQuery.value.trim()) {
    const query = characterSearchQuery.value.toLowerCase()
    chars = chars.filter(c => getCharacterName(c).toLowerCase().includes(query))
  }

  return chars
})

// 过滤后的场景列表
const filteredScenes = computed(() => {
  let scenes = editorStore.scenes

  // 按搜索关键词过滤
  if (sceneSearchQuery.value.trim()) {
    const query = sceneSearchQuery.value.toLowerCase()
    scenes = scenes.filter(s => getSceneName(s).toLowerCase().includes(query))
  }

  return scenes
})

// 创建新角色
const handleCreateCharacter = () => {
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'character',
    assetId: undefined
  })
}

// 创建新场景
const handleCreateScene = () => {
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'scene',
    assetId: undefined
  })
}

// 打开角色库（从库导入）
const handleOpenCharacterLibrary = () => {
  showCharacterLibraryModal.value = true
}

// 打开场景库（从库导入）
const handleOpenSceneLibrary = () => {
  showSceneLibraryModal.value = true
}

// 关闭库模态框
const handleCloseCharacterLibraryModal = () => {
  showCharacterLibraryModal.value = false
}

const handleCloseSceneLibraryModal = () => {
  showSceneLibraryModal.value = false
}

const handleClosePropLibraryModal = () => {
  showPropLibraryModal.value = false
}

// 点击角色卡片
const handleCharacterClick = (characterId: number) => {
  const character = editorStore.characters.find(c => c.id === characterId)
  
  // 如果角色没有图片，传递剧本文本让AI解析
  if (!character?.thumbnailUrl) {
    let scriptText = editorStore.originalScript || ''
    // 限制文本长度
    if (scriptText.length > 2000) {
      scriptText = scriptText.substring(0, 2000) + '...'
    }
    
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: characterId,
      characterName: getCharacterName(character),
      prefillDescription: scriptText,  // 让AI解析
      existingDescription: (character as any)?.finalDescription || (character as any)?.description
    })
  } else {
    // 已有图片，正常打开编辑面板
    panelManagerStore.openPanel('asset-edit', {
      assetType: 'character',
      assetId: characterId,
      characterName: getCharacterName(character),
      existingThumbnailUrl: character?.thumbnailUrl,
      existingDescription: (character as any)?.finalDescription || (character as any)?.description
    })
  }
}

// 点击场景卡片
const handleSceneClick = (sceneId: number) => {
  const scene = editorStore.scenes.find(s => s.id === sceneId)
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'scene',
    assetId: sceneId,
    sceneName: getSceneName(scene),
    existingThumbnailUrl: scene?.thumbnailUrl,
    existingDescription: (scene as any)?.finalDescription || (scene as any)?.description
  })
}

// 过滤后的道具列表
const filteredProps = computed(() => {
  let props = editorStore.props

  if (propSearchQuery.value.trim()) {
    const query = propSearchQuery.value.toLowerCase()
    props = props.filter(p => getPropName(p).toLowerCase().includes(query))
  }

  return props
})

// 创建新道具
const handleCreateProp = () => {
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'prop',
    assetId: undefined
  })
}

// 打开道具库（从库导入）
const handleOpenPropLibrary = () => {
  showPropLibraryModal.value = true
}

// 点击道具卡片
const handlePropClick = (propId: number) => {
  const prop = editorStore.props.find(p => p.id === propId)
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'prop',
    assetId: propId,
    propName: getPropName(prop),
    existingThumbnailUrl: prop?.thumbnailUrl,
    existingDescription: (prop as any)?.finalDescription || (prop as any)?.description
  })
}

onMounted(() => {
  loadCharacterCategories()
  loadSceneCategories()
})
</script>

<template>
  <div ref="assetPanelRef" class="flex flex-col h-full bg-bg-elevated">
    <!-- 标签页切换 -->
    <div class="flex items-center gap-2 border-b border-border-default px-4 py-3">
      <button
        @click="activeSidebarTab = 'characters'"
        :class="[
          'px-4 py-2 rounded text-sm font-medium transition-all',
          activeSidebarTab === 'characters'
            ? 'bg-bg-hover text-white'
            : 'text-text-tertiary hover:bg-bg-subtle'
        ]"
      >
        角色
      </button>
      <button
        @click="activeSidebarTab = 'scenes'"
        :class="[
          'px-4 py-2 rounded text-sm font-medium transition-all',
          activeSidebarTab === 'scenes'
            ? 'bg-bg-hover text-white'
            : 'text-text-tertiary hover:bg-bg-subtle'
        ]"
      >
        场景
      </button>
      <button
        @click="activeSidebarTab = 'props'"
        :class="[
          'px-4 py-2 rounded text-sm font-medium transition-all',
          activeSidebarTab === 'props'
            ? 'bg-bg-hover text-white'
            : 'text-text-tertiary hover:bg-bg-subtle'
        ]"
      >
        道具
      </button>
    </div>

    <!-- 角色标签页 -->
    <div v-show="activeSidebarTab === 'characters'" class="flex-1 overflow-hidden flex flex-col">
      <!-- 顶部操作栏 -->
      <div class="px-4 py-3 border-b border-border-default">
        <div class="flex items-center gap-2 mb-3">
          <!-- 搜索框 -->
          <div class="flex-1 relative">
            <input
              v-model="characterSearchQuery"
              type="text"
              placeholder="搜索角色..."
              class="w-full px-3 py-2 pr-8 text-xs bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
            >
          </div>
          <!-- 分类选择 -->
          <select
            v-model="selectedCharacterCategory"
            class="px-3 py-2 bg-bg-subtle border border-border-default rounded-lg text-text-primary text-xs focus:outline-none focus:border-gray-900/50 cursor-pointer"
          >
            <option :value="null" class="bg-bg-elevated">全部分类</option>
            <option v-for="category in characterCategories" :key="category.id" :value="category.id" class="bg-bg-elevated">
              {{ category.name }} ({{ category.count }})
            </option>
          </select>
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <button
            @click="handleCreateCharacter"
            class="flex-1 px-4 py-2 bg-bg-subtle rounded text-text-secondary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            创建角色
          </button>
          <button
            @click="handleOpenCharacterLibrary"
            class="px-4 py-2 bg-bg-hover rounded text-text-primary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            角色库
          </button>
        </div>
      </div>

      <!-- 角色列表 -->
      <div class="flex-1 overflow-y-auto px-4 py-4">
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="char in filteredCharacters"
            :key="char.id"
            class="group cursor-pointer relative"
            @mouseenter="showHoverPreview({ key: `character-${char.id}`, name: getCharacterName(char), imageUrl: char.thumbnailUrl, description: getCharacterDescription(char) })"
            @mousemove="moveHoverPreview"
            @mouseleave="hideHoverPreview"
            @click="handleCharacterClick(char.id)"
          >
            <div class="relative w-full aspect-square rounded-lg overflow-hidden bg-bg-hover">
              <img
                v-if="char.thumbnailUrl"
                :src="char.thumbnailUrl"
                :alt="getCharacterName(char)"
                class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-7 h-7 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
                  <circle cx="12" cy="7" r="4"></circle>
                </svg>
              </div>
              <div
                v-if="pendingHoverKey === `character-${char.id}`"
                class="asset-thumb-wiper"
              ></div>
              <!-- 激活状态指示器（主人公亮点） -->
              <div
                v-if="char.isActive"
                class="absolute top-1 right-1 w-3 h-3 rounded-full bg-[#00FFCC] border-2 border-[#1E2025] shadow-[0_0_8px_3px_rgba(0,255,204,0.85)]"
              ></div>
            </div>
            <p class="text-center text-xs truncate text-[#FFB000] font-medium mt-1" :title="getCharacterName(char)">
              {{ getCharacterName(char) }}
            </p>
          </div>

          <!-- 空状态 -->
          <div
            v-if="filteredCharacters.length === 0"
            class="col-span-4 text-center py-8 text-text-tertiary text-xs"
          >
            {{ characterSearchQuery || selectedCharacterCategory !== null ? '未找到匹配的角色' : '暂无角色，点击"创建角色"开始' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 场景标签页 -->
    <div v-show="activeSidebarTab === 'scenes'" class="flex-1 overflow-hidden flex flex-col">
      <!-- 顶部操作栏 -->
      <div class="px-4 py-3 border-b border-border-default">
        <div class="flex items-center gap-2 mb-3">
          <!-- 搜索框 -->
          <div class="flex-1 relative">
            <input
              v-model="sceneSearchQuery"
              type="text"
              placeholder="搜索场景..."
              class="w-full px-3 py-2 pr-8 text-xs bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
            >
          </div>
          <!-- 分类选择 -->
          <select
            v-model="selectedSceneCategory"
            class="px-3 py-2 bg-bg-subtle border border-border-default rounded-lg text-text-primary text-xs focus:outline-none focus:border-gray-900/50 cursor-pointer"
          >
            <option :value="null" class="bg-bg-elevated">全部分类</option>
            <option v-for="category in sceneCategories" :key="category.id" :value="category.id" class="bg-bg-elevated">
              {{ category.name }} ({{ category.count }})
            </option>
          </select>
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <button
            @click="handleCreateScene"
            class="flex-1 px-4 py-2 bg-bg-subtle rounded text-text-secondary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            创建场景
          </button>
          <button
            @click="handleOpenSceneLibrary"
            class="px-4 py-2 bg-bg-hover rounded text-text-primary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            场景库
          </button>
        </div>
      </div>

      <!-- 场景列表 -->
      <div class="flex-1 overflow-y-auto px-4 py-4">
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="scene in filteredScenes"
            :key="scene.id"
            class="group cursor-pointer relative"
            @mouseenter="showHoverPreview({ key: `scene-${scene.id}`, name: getSceneName(scene), imageUrl: scene.thumbnailUrl, description: getSceneDescription(scene) })"
            @mousemove="moveHoverPreview"
            @mouseleave="hideHoverPreview"
            @click="handleSceneClick(scene.id)"
          >
            <div class="relative w-full aspect-square rounded-lg overflow-hidden bg-bg-hover">
              <img
                v-if="scene.thumbnailUrl"
                :src="scene.thumbnailUrl"
                :alt="getSceneName(scene)"
                class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <span class="text-2xl">🎬</span>
              </div>
              <div
                v-if="pendingHoverKey === `scene-${scene.id}`"
                class="asset-thumb-wiper"
              ></div>
            </div>
            <p class="text-center text-xs truncate text-[#FFB000] font-medium mt-1" :title="getSceneName(scene)">
              {{ getSceneName(scene) }}
            </p>
          </div>

          <!-- 空状态 -->
          <div
            v-if="filteredScenes.length === 0"
            class="col-span-4 text-center py-8 text-text-tertiary text-xs"
          >
            {{ sceneSearchQuery ? '未找到匹配的场景' : '暂无场景，点击"创建场景"开始' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 道具标签页 -->
    <div v-show="activeSidebarTab === 'props'" class="flex-1 overflow-hidden flex flex-col">
      <!-- 顶部操作栏 -->
      <div class="px-4 py-3 border-b border-border-default">
        <div class="flex items-center gap-2 mb-3">
          <!-- 搜索框 -->
          <div class="flex-1 relative">
            <input
              v-model="propSearchQuery"
              type="text"
              placeholder="搜索道具..."
              class="w-full px-3 py-2 pr-8 text-xs bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50"
            >
          </div>
        </div>

        <!-- 操作按钮 -->
        <div class="flex items-center gap-2">
          <button
            @click="handleCreateProp"
            class="flex-1 px-4 py-2 bg-bg-subtle rounded text-text-secondary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            创建道具
          </button>
          <button
            @click="handleOpenPropLibrary"
            class="px-4 py-2 bg-bg-hover rounded text-text-primary font-medium text-sm hover:bg-bg-hover transition-colors"
          >
            道具库
          </button>
        </div>
      </div>

      <!-- 道具列表 -->
      <div class="flex-1 overflow-y-auto px-4 py-4">
        <div class="grid grid-cols-4 gap-3">
          <div
            v-for="prop in filteredProps"
            :key="prop.id"
            class="group cursor-pointer relative"
            @mouseenter="showHoverPreview({ key: `prop-${prop.id}`, name: getPropName(prop), imageUrl: prop.thumbnailUrl, description: getPropDescription(prop) })"
            @mousemove="moveHoverPreview"
            @mouseleave="hideHoverPreview"
            @click="handlePropClick(prop.id)"
          >
            <div class="relative w-full aspect-square rounded-lg overflow-hidden bg-bg-hover">
              <img
                v-if="prop.thumbnailUrl"
                :src="prop.thumbnailUrl"
                :alt="getPropName(prop)"
                class="w-full h-full object-cover transition-transform duration-300 group-hover:scale-105"
              >
              <div v-else class="w-full h-full flex items-center justify-center">
                <svg class="w-7 h-7 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M19.428 15.428a2 2 0 00-1.022-.547l-2.387-.477a6 6 0 00-3.86.517l-.318.158a6 6 0 01-3.86.517L6.05 15.21a2 2 0 00-1.806.547M8 4h8l-1 1v5.172a2 2 0 00.586 1.414l5 5c1.26 1.26.367 3.414-1.415 3.414H4.828c-1.782 0-2.674-2.154-1.414-3.414l5-5A2 2 0 009 10.172V5L8 4z"></path>
                </svg>
              </div>
              <div
                v-if="pendingHoverKey === `prop-${prop.id}`"
                class="asset-thumb-wiper"
              ></div>
            </div>
            <p class="text-center text-xs truncate text-[#FFB000] font-medium mt-1" :title="getPropName(prop)">
              {{ getPropName(prop) }}
            </p>
          </div>

          <!-- 空状态 -->
          <div
            v-if="filteredProps.length === 0"
            class="col-span-4 text-center py-8 text-text-tertiary text-xs"
          >
            {{ propSearchQuery ? '未找到匹配的道具' : '暂无道具，点击"创建道具"开始' }}
          </div>
        </div>
      </div>
    </div>

    <!-- 角色库模态框 -->
    <CharacterLibraryModal
      v-if="showCharacterLibraryModal && editorStore.projectId"
      :project-id="editorStore.projectId"
      @close="handleCloseCharacterLibraryModal"
      @added="handleCloseCharacterLibraryModal"
    />

    <!-- 场景库模态框 -->
    <SceneLibraryModal
      v-if="showSceneLibraryModal && editorStore.projectId"
      :project-id="editorStore.projectId"
      @close="handleCloseSceneLibraryModal"
      @added="handleCloseSceneLibraryModal"
    />
    
    <!-- 道具库模态框 -->
    <PropLibraryModal
      v-if="showPropLibraryModal && editorStore.projectId"
      :project-id="editorStore.projectId"
      @close="handleClosePropLibraryModal"
      @added="handleClosePropLibraryModal"
    />

    <AssetHoverPreview
      :visible="hoverPreview.visible"
      :name="hoverPreview.name"
      :image-url="hoverPreview.imageUrl"
      :description="hoverPreview.description"
      :x="hoverPreview.x"
      :y="hoverPreview.y"
      variant="detail"
      placement="fixed"
    />
  </div>
</template>

<style scoped>
.asset-thumb-wiper {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    linear-gradient(110deg, transparent 0%, rgba(255, 255, 255, 0.12) 42%, rgba(255, 255, 255, 0.62) 50%, rgba(255, 255, 255, 0.12) 58%, transparent 100%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(0, 0, 0, 0.12));
  background-size: 220% 100%, 100% 100%;
  animation: asset-thumb-wiper-sweep 680ms ease-in-out forwards;
}

@keyframes asset-thumb-wiper-sweep {
  from {
    background-position: 120% 0, 0 0;
  }

  to {
    background-position: -120% 0, 0 0;
  }
}
</style>

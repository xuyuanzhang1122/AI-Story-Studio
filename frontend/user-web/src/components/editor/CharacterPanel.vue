<script setup lang="ts">
import { ref, computed } from 'vue'
import { useEditorStore } from '@/stores/editor'
import { usePanelManagerStore } from '@/stores/panelManager'
import CharacterLibraryModal from './CharacterLibraryModal.vue'

const editorStore = useEditorStore()

// Search state
const searchQuery = ref('')

const getCharacterName = (character: any) => {
  return character?.displayName || character?.name || character?.libraryCharacterName || '未命名角色'
}

// Filter active characters (isActive === true)
const activeCharacters = computed(() => {
  return editorStore.characters.filter((c) => c.isActive)
})

// Filter available characters (isActive === false)
const availableCharacters = computed(() => {
  const chars = editorStore.characters.filter((c) => !c.isActive)

  if (!searchQuery.value.trim()) {
    return chars
  }

  const query = searchQuery.value.toLowerCase()
  return chars.filter((c) => getCharacterName(c).toLowerCase().includes(query))
})

// Show add character library modal
const showLibraryModal = ref(false)

const handleAddCharacter = () => {
  showLibraryModal.value = true
}

const handleLibraryModalClose = () => {
  showLibraryModal.value = false
}

const handleBatchGenerate = () => {
  const activeCharIds = activeCharacters.value.map((c) => c.id)
  if (activeCharIds.length === 0) {
    window.$message?.warning('没有可生成的角色')
    return
  }
  // TODO: Implement batch generation
  console.log('[CharacterPanel] Batch generate:', activeCharIds)
  window.$message?.info(`将为 ${activeCharIds.length} 个角色批量生成图片`)
}

const handleCharacterClick = (characterId: number) => {
  const character = editorStore.characters.find(c => c.id === characterId)
  if (!character) return
  
  // 跳转到编辑面板
  const panelManagerStore = usePanelManagerStore()
  panelManagerStore.openPanel('asset-edit', {
    assetType: 'character',
    assetId: character.id,
    characterName: getCharacterName(character),
    existingThumbnailUrl: character.thumbnailUrl,
    existingDescription: (character as any).finalDescription || (character as any).description
  })
}
</script>

<template>
  <div class="flex flex-col h-full">
    <!-- Section: Active Characters -->
    <div class="flex-grow overflow-y-auto pr-2">
      <h3 class="text-sm font-bold mb-3 text-text-primary">
        作品中角色
        <span class="text-text-tertiary font-normal ml-2">({{ activeCharacters.length }})</span>
      </h3>

      <div class="grid grid-cols-4 gap-3 mb-6">
        <!-- Active Character Cards -->
        <div
          v-for="char in activeCharacters"
          :key="char.id"
          class="text-center group cursor-pointer relative rounded-lg"
          @click="handleCharacterClick(char.id)"
        >
          <!-- Thumbnail -->
          <img
            v-if="char.thumbnailUrl"
            :src="char.thumbnailUrl"
            :alt="getCharacterName(char)"
            class="w-full aspect-square rounded-lg object-cover mb-1 transition-transform duration-300 group-hover:scale-105"
          >
          <div
            v-else
            class="w-full aspect-square rounded-lg bg-bg-subtle flex items-center justify-center mb-1"
          >
            <svg class="w-6 h-6 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>

          <!-- Active Indicator (Neon Cyan Dot) -->
          <div
            class="absolute top-1 right-1 w-3 h-3 rounded-full bg-[#00FFCC] border-2 border-[#1E2025] shadow-[0_0_8px_3px_rgba(0,255,204,0.85)]"
          ></div>

          <!-- Character Name -->
          <p class="text-center text-xs truncate text-[#FFB000] font-medium mt-0.5" :title="getCharacterName(char)">
            {{ getCharacterName(char) }}
          </p>
        </div>

        <!-- Empty State -->
        <div
          v-if="activeCharacters.length === 0"
          class="col-span-4 text-center py-8 text-text-tertiary text-xs"
        >
          暂无启用的角色
        </div>
      </div>

      <!-- Section: All Available Characters -->
      <div class="flex items-center justify-between mb-3">
        <h3 class="text-sm font-bold text-text-primary">
          全部可用角色
          <span class="text-text-tertiary font-normal ml-2">({{ availableCharacters.length }})</span>
        </h3>

        <!-- Search Input -->
        <div class="relative">
          <input
            v-model="searchQuery"
            type="text"
            placeholder="搜索角色..."
            class="px-2 py-1 pr-6 text-xs bg-bg-subtle border border-border-default rounded-lg text-text-primary placeholder-text-tertiary focus:outline-none focus:border-gray-900/50 w-32"
          >
        </div>
      </div>

      <div class="grid grid-cols-4 gap-3">
        <!-- Available Character Cards -->
        <div
          v-for="char in availableCharacters"
          :key="char.id"
          class="text-center group cursor-pointer relative rounded-lg"
          @click="handleCharacterClick(char.id)"
        >
          <!-- Thumbnail -->
          <img
            v-if="char.thumbnailUrl"
            :src="char.thumbnailUrl"
            :alt="getCharacterName(char)"
            class="w-full aspect-square rounded-lg object-cover mb-1 transition-transform duration-300 group-hover:scale-105"
          >
          <div
            v-else
            class="w-full aspect-square rounded-lg bg-bg-subtle flex items-center justify-center mb-1"
          >
            <svg class="w-6 h-6 text-white/30" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"></path>
              <circle cx="12" cy="7" r="4"></circle>
            </svg>
          </div>

          <!-- Character Name -->
          <p class="text-center text-xs truncate text-[#FFB000] font-medium mt-0.5" :title="getCharacterName(char)">
            {{ getCharacterName(char) }}
          </p>
        </div>

        <!-- Add Character Button ("+") -->
        <div class="inline-block">
          <button
            class="w-full aspect-square rounded-lg border-2 border-dashed border-border-default flex flex-col items-center justify-center text-text-tertiary hover:bg-bg-subtle hover:border-white/40 transition-colors"
            @click="handleAddCharacter"
          >
            <svg class="w-6 h-6 mb-1" fill="none" stroke="currentColor" viewBox="0 0 24 24" stroke-width="2">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
            <span class="text-xs">创建</span>
          </button>
        </div>

        <!-- Empty State -->
        <div
          v-if="availableCharacters.length === 0"
          class="col-span-4 text-center py-8 text-text-tertiary text-xs"
        >
          {{ searchQuery ? '未找到匹配的角色' : '暂无可用角色' }}
        </div>
      </div>
    </div>

    <!-- Character Library Modal -->
    <CharacterLibraryModal
      v-if="showLibraryModal && editorStore.projectId"
      :project-id="editorStore.projectId"
      @close="handleLibraryModalClose"
      @added="handleLibraryModalClose"
    />
  </div>
</template>

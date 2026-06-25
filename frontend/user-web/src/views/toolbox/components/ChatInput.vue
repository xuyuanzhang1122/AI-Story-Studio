<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToolboxStore } from '@/stores/toolbox'
import { getModelAspectRatios, getVideoDurations } from '@/constants/toolboxModels'
import CustomSelect from '@/components/base/CustomSelect.vue'
import { uploadApi } from '@/api/upload'
import LobeChatInput from './LobeChatInput.vue'
import LobeDropdownSelect from './LobeDropdownSelect.vue'

const toolboxStore = useToolboxStore()

// 类型选项 (视频功能暂时禁用)
const typeOptions = [
  { label: '文字', value: 'TEXT' },
  { label: '图片', value: 'IMAGE' },
  // { label: '视频', value: 'VIDEO' }, // 暂时禁用
]

// 当前类型的模型列表
const modelOptions = computed(() => {
  const type = toolboxStore.currentInput.type
  return toolboxStore.models[type].map((model) => ({
    label: model.name,
    value: model.code,
  }))
})

// 当前模型的比例列表
const aspectRatioOptions = computed(() => {
  const type = toolboxStore.currentInput.type
  const model = toolboxStore.currentInput.model
  if (type === 'TEXT') return []

  const ratios = getModelAspectRatios(type, model)
  return ratios.map((ratio) => ({ label: ratio, value: ratio }))
})

// 视频时长选项
const durationOptions = computed(() => {
  if (toolboxStore.currentInput.type !== 'VIDEO') return []

  const model = toolboxStore.currentInput.model
  const durations = getVideoDurations(model)
  return durations.map((duration) => ({ label: `${duration}秒`, value: duration }))
})

// 是否可以发送
const canSend = computed(() => {
  const hasPrompt = toolboxStore.currentInput.prompt.trim().length > 0
  const notGenerating = !toolboxStore.isGenerating
  return hasPrompt && notGenerating
})

// 处理类型切换
const handleTypeChange = (type: 'TEXT' | 'IMAGE' | 'VIDEO') => {
  toolboxStore.updateInputType(type)
}

// 参考图片上传
const referenceImageUrl = ref<string>('')
const uploading = ref(false)

const handleImageUpload = async (e: Event) => {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return

  // 验证文件类型
  if (!file.type.startsWith('image/')) {
    window.$message?.error('请上传图片文件')
    return
  }

  // 验证文件大小 (50MB)
  if (file.size > 50 * 1024 * 1024) {
    window.$message?.error('图片大小不能超过50MB')
    return
  }

  try {
    uploading.value = true
    const result = await uploadApi.upload(file, 'image')
    referenceImageUrl.value = result.url
    window.$message?.success('图片上传成功')
  } catch (error: any) {
    window.$message?.error(error.message || '图片上传失败')
  } finally {
    uploading.value = false
    // 清空input以允许重新上传同一文件
    input.value = ''
  }
}

const removeReferenceImage = () => {
  referenceImageUrl.value = ''
}

// 处理发送
const handleSend = async () => {
  if (!canSend.value) return

  // 视频生成需要参考图片
  if (toolboxStore.currentInput.type === 'VIDEO' && !referenceImageUrl.value) {
    window.$message?.error('视频生成需要上传参考图片')
    return
  }

  try {
    // 如果是视频类型,传入参考图片URL
    if (toolboxStore.currentInput.type === 'VIDEO') {
      await toolboxStore.generateWithConversation(referenceImageUrl.value)
    } else {
      await toolboxStore.generateWithConversation()
    }
    window.$message?.success('生成请求已提交')
    // 清空参考图片
    referenceImageUrl.value = ''
  } catch (error: any) {
    window.$message?.error(error.message || '生成失败')
  }
}
</script>

<template>
  <div class="toolbox-input-panel border-t border-border-default bg-bg-elevated p-3">
    <!-- Controls Row -->
    <div class="flex items-center gap-2 mb-3">
      <!-- Type Select -->
      <LobeDropdownSelect
        :model-value="toolboxStore.currentInput.type"
        :options="typeOptions"
        @update:model-value="(v) => handleTypeChange(v as 'TEXT' | 'IMAGE' | 'VIDEO')"
      />

      <!-- Model Select -->
      <LobeDropdownSelect
        :model-value="toolboxStore.currentInput.model"
        :options="modelOptions"
        class-name="is-model"
        @update:model-value="(v) => toolboxStore.currentInput.model = String(v)"
      />

      <!-- Aspect Ratio Select (for IMAGE/VIDEO) -->
      <CustomSelect
        v-if="aspectRatioOptions.length > 0"
        v-model="toolboxStore.currentInput.aspectRatio"
        :options="aspectRatioOptions"
      />

      <!-- Duration Select (for VIDEO) -->
      <CustomSelect
        v-if="durationOptions.length > 0"
        :model-value="toolboxStore.currentInput.duration"
        :options="durationOptions"
        @update:model-value="(v) => toolboxStore.currentInput.duration = Number(v)"
      />
    </div>

    <!-- Input Area -->
    <div class="flex items-stretch gap-3">
      <!-- Reference Image Upload (for VIDEO only) -->
      <div v-if="toolboxStore.currentInput.type === 'VIDEO'" class="flex-shrink-0">
        <!-- Upload Button -->
        <label v-if="!referenceImageUrl" class="cursor-pointer">
          <input
            type="file"
            accept="image/*"
            class="hidden"
            @change="handleImageUpload"
          />
          <div
            class="w-16 h-16 rounded border-2 border-dashed flex items-center justify-center transition-all"
            :class="uploading
              ? 'border-gray-900/50 bg-gray-900/5'
              : 'border-border-default bg-bg-subtle hover:border-gray-900/50 hover:bg-gray-900/5'"
          >
            <svg v-if="!uploading" class="w-6 h-6 text-text-tertiary" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M4 16l4.586-4.586a2 2 0 012.828 0L16 16m-2-2l1.586-1.586a2 2 0 012.828 0L20 14m-6-6h.01M6 20h12a2 2 0 002-2V6a2 2 0 00-2-2H6a2 2 0 00-2 2v12a2 2 0 002 2z" />
            </svg>
            <svg v-else class="w-6 h-6 text-text-primary animate-spin" fill="none" viewBox="0 0 24 24">
              <circle class="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" stroke-width="4"></circle>
              <path class="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
          </div>
        </label>
        
        <!-- Preview -->
        <div v-else class="relative w-16 h-16 group">
          <img
            :src="referenceImageUrl"
            class="w-full h-full rounded object-cover"
          />
          <button
            class="absolute -top-1 -right-1 w-5 h-5 rounded bg-red-500 text-text-primary flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
            @click="removeReferenceImage"
          >
            <svg class="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path stroke-linecap="round" stroke-linejoin="round" stroke-width="2" d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      </div>
      
      <LobeChatInput
        v-model="toolboxStore.currentInput.prompt"
        :can-send="canSend"
        :loading="toolboxStore.isGenerating"
        @send="handleSend"
      />
    </div>

  </div>
</template>

<style scoped>
.toolbox-input-panel {
  background:
    linear-gradient(180deg, rgba(31, 31, 35, 0.92), rgba(18, 18, 20, 0.96)),
    #151518;
}

.toolbox-input-panel :deep(.lobe-chat-input-mount),
.toolbox-input-panel :deep(.ant-app),
.toolbox-input-panel :deep(.lobe-toolbox-input-shell) {
  width: 100%;
}

.toolbox-input-panel :deep(.lobe-toolbox-input-shell) {
  min-width: 0;
  position: relative;
}

.toolbox-input-panel :deep(.lobe-toolbox-typewriter) {
  align-items: center;
  color: #f8fafc;
  display: flex;
  left: 20px;
  min-height: 24px;
  pointer-events: none;
  position: absolute;
  text-shadow: 0 1px 12px rgba(0, 0, 0, 0.45);
  top: 20px;
  z-index: 20;
}

.toolbox-input-panel :deep(.lobe-toolbox-typewriter span) {
  color: inherit !important;
  font-size: 16px;
  font-weight: 700;
  letter-spacing: 0;
  line-height: 1.5;
}

.toolbox-input-panel :deep(.lobe-toolbox-typewriter > span:first-child) {
  color: #f8fafc !important;
}

.toolbox-input-panel :deep(.lobe-toolbox-chat-input) {
  background: #09090b;
  border: 1px solid rgba(167, 139, 250, 0.38);
  border-radius: 10px;
  box-shadow: 0 16px 36px rgba(0, 0, 0, 0.26);
  min-height: 128px;
  overflow: hidden;
  position: relative;
  transition: border-color 0.16s ease, box-shadow 0.16s ease;
  z-index: 1;
}

.toolbox-input-panel :deep(.lobe-toolbox-chat-input:focus-within) {
  border-color: rgba(167, 139, 250, 0.58);
  box-shadow: 0 18px 42px rgba(0, 0, 0, 0.28), 0 0 0 3px rgba(139, 92, 246, 0.14);
}

.toolbox-input-panel :deep(.lobe-toolbox-chat-input textarea) {
  background: transparent !important;
  color: rgba(255, 255, 255, 0.92) !important;
  font-size: 15px !important;
  line-height: 1.7 !important;
  min-height: 84px !important;
  padding: 20px 20px 8px !important;
  position: relative;
  z-index: 2;
}

.toolbox-input-panel :deep(.lobe-toolbox-chat-input textarea::placeholder) {
  color: transparent !important;
}

.toolbox-input-panel :deep(.lobe-toolbox-input-footer) {
  align-items: center;
  display: flex;
  gap: 12px;
  justify-content: flex-end;
  min-height: 42px;
  padding: 0 12px 10px 20px;
  position: relative;
  z-index: 3;
}

.toolbox-input-panel :deep(.lobe-toolbox-input-hint) {
  color: rgba(255, 255, 255, 0.42);
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.toolbox-input-panel :deep(.lobe-toolbox-send-button) {
  align-items: center;
  background: #f8fafc;
  border: 0;
  border-radius: 8px;
  color: #111114;
  cursor: pointer;
  display: inline-flex;
  font-size: 13px;
  font-weight: 700;
  height: 32px;
  justify-content: center;
  min-width: 64px;
  padding: 0 16px;
  transition: background 0.16s ease, opacity 0.16s ease, transform 0.16s ease;
}

.toolbox-input-panel :deep(.lobe-toolbox-send-button:not(:disabled):hover) {
  background: #ffffff;
  transform: translateY(-1px);
}

.toolbox-input-panel :deep(.lobe-toolbox-send-button:disabled) {
  cursor: not-allowed;
  opacity: 0.48;
}

.toolbox-input-panel :deep(.lobe-dropdown-select-mount) {
  flex: none;
}

.toolbox-input-panel :deep(.lobe-toolbox-dropdown-button) {
  background: #24262d;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 8px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 12px;
  font-weight: 700;
  height: 30px;
  min-width: 78px;
  padding: 0 10px 0 12px;
}

.toolbox-input-panel :deep(.is-model .lobe-toolbox-dropdown-button) {
  min-width: 118px;
}

.toolbox-input-panel :deep(.lobe-toolbox-dropdown-button:hover),
.toolbox-input-panel :deep(.lobe-toolbox-dropdown-button:focus) {
  background: #2d3038;
  border-color: rgba(167, 139, 250, 0.42);
  color: #fff;
}
</style>

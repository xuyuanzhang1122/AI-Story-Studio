<script setup lang="ts">
import { ref, watch } from 'vue'

interface Props {
  show: boolean
  title?: string
  folderName?: string
  loading?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  title: '新建文件夹',
  folderName: '',
  loading: false,
})

const emit = defineEmits<{
  close: []
  confirm: [name: string]
}>()

const localName = ref('')

watch(() => props.show, (show) => {
  if (show) {
    localName.value = props.folderName
  }
})

const handleConfirm = () => {
  if (localName.value.trim()) {
    emit('confirm', localName.value.trim())
  }
}

const handleCancel = () => {
  localName.value = ''
  emit('close')
}
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition-opacity duration-200"
      leave-active-class="transition-opacity duration-200"
      enter-from-class="opacity-0"
      leave-to-class="opacity-0"
    >
      <div
        v-if="show"
        class="folder-modal-overlay fixed inset-0 z-50 flex items-center justify-center p-4"
        @click.self="handleCancel"
      >
        <Transition
          enter-active-class="transition-all duration-200"
          leave-active-class="transition-all duration-200"
          enter-from-class="opacity-0 scale-95"
          leave-to-class="opacity-0 scale-95"
        >
          <div
            v-if="show"
            class="folder-modal-card w-full max-w-md pointer-events-auto"
          >
            <h2 class="folder-modal-title">{{ title }}</h2>

            <div class="folder-modal-body">
              <label class="folder-modal-label">文件夹名称</label>
              <input
                v-model="localName"
                type="text"
                placeholder="请输入文件夹名称"
                maxlength="50"
                class="folder-modal-input"
                @keydown.enter="handleConfirm"
                @keydown.esc="handleCancel"
              >
            </div>

            <div class="folder-modal-actions flex gap-3 justify-end">
              <button
                class="folder-modal-secondary"
                :disabled="loading"
                @click="handleCancel"
              >
                取消
              </button>
              <button
                :disabled="!localName.trim() || loading"
                class="folder-modal-primary"
                @click="handleConfirm"
              >
                <span v-if="loading">处理中...</span>
                <span v-else>确认</span>
              </button>
            </div>
          </div>
        </Transition>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
.folder-modal-overlay {
  background: rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(2px);
}

.folder-modal-card {
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.18);
  padding: 26px;
  color: #101828;
}

.folder-modal-title {
  margin-bottom: 22px;
  color: #101828;
  font-size: 18px;
  font-weight: 750;
  letter-spacing: 0;
}

.folder-modal-body {
  margin-bottom: 24px;
}

.folder-modal-label {
  display: block;
  margin-bottom: 8px;
  color: #475467;
  font-size: 14px;
  font-weight: 650;
}

.folder-modal-input {
  width: 100%;
  height: 42px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 12px;
  background: #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
  color: #101828;
  font-size: 14px;
  font-weight: 600;
  outline: none;
  padding: 0 14px;
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.folder-modal-input::placeholder {
  color: #98a2b3;
}

.folder-modal-input:focus {
  border-color: rgba(139, 92, 246, 0.58);
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.folder-modal-primary,
.folder-modal-secondary {
  min-width: 82px;
  height: 38px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.folder-modal-primary {
  border: 0;
  background: #8b5cf6;
  color: #ffffff;
}

.folder-modal-primary:not(:disabled):hover {
  background: #7c3aed;
  transform: translateY(-1px);
}

.folder-modal-primary:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}

.folder-modal-secondary {
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  color: #475467;
}

.folder-modal-secondary:hover {
  border-color: rgba(139, 92, 246, 0.38);
  color: #101828;
  background: #f8fafc;
}
</style>

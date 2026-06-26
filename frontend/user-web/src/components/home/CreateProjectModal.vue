<script setup lang="ts">
import { ref, computed } from 'vue'

const props = defineProps<{
  show: boolean
  loading?: boolean
  folderId?: number | null
}>()

const emit = defineEmits<{
  close: []
  confirm: [data: {
    name: string
    folderId?: number
    rawText: string
  }]
}>()

const projectName = ref('')
const storyText = ref('')

// Char count
const charCount = computed(() => storyText.value.length)
const maxChars = 5000

const handleConfirm = () => {
  if (!projectName.value.trim()) {
    alert('请输入项目名称')
    return
  }

  emit('confirm', {
    name: projectName.value.trim(),
    folderId: props.folderId || undefined,
    rawText: storyText.value,
  })
}

const handleClose = () => {
  // Reset form
  projectName.value = ''
  storyText.value = ''
  emit('close')
}
</script>

<template>
  <!-- Modal Overlay -->
  <Transition
    enter-active-class="transition-opacity duration-200"
    leave-active-class="transition-opacity duration-200"
    enter-from-class="opacity-0"
    leave-to-class="opacity-0"
  >
    <div
      v-if="show"
      class="create-project-overlay fixed inset-0 flex items-center justify-center z-50 p-4"
      @click.self="handleClose"
    >
      <!-- Modal Container -->
      <div class="create-project-modal w-[500px] max-h-[90vh] flex flex-col pointer-events-auto">
        <!-- Header -->
        <div class="create-project-header relative flex items-center justify-center">
          <h2 class="create-project-title">创建作品</h2>
          <button
            class="create-project-close absolute right-5 top-4 transition-colors"
            @click="handleClose"
          >
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <line x1="18" y1="6" x2="6" y2="18"></line>
              <line x1="6" y1="6" x2="18" y2="18"></line>
            </svg>
          </button>
        </div>

        <!-- Body -->
        <div class="create-project-body flex flex-col gap-5">
          <!-- Project Name -->
          <div>
            <div class="create-project-label">项目名称</div>
            <input
              v-model="projectName"
              type="text"
              placeholder="请输入项目名称"
              class="create-project-field"
            />
          </div>

          <!-- Story Script -->
          <div class="flex flex-col">
            <div class="create-project-label">故事文案（选填）</div>
            <div class="create-project-textarea-shell flex flex-col">
              <textarea
                v-model="storyText"
                placeholder="输入你的故事文案..."
                class="create-project-textarea"
                :maxlength="maxChars"
              ></textarea>
              <div class="create-project-count-row flex items-center justify-end">
                <span>{{ charCount }} / {{ maxChars }}</span>
              </div>
            </div>
          </div>
        </div>

        <!-- Footer -->
        <div class="create-project-actions flex items-center justify-center gap-4">
          <button
            class="create-project-primary"
            :disabled="loading"
            @click="handleConfirm"
          >
            {{ loading ? '创建中...' : '创建' }}
          </button>
          <button
            class="create-project-secondary"
            @click="handleClose"
          >
            取消
          </button>
        </div>
      </div>
    </div>
  </Transition>
</template>

<style scoped>
.create-project-overlay {
  background: rgba(15, 23, 42, 0.14);
  backdrop-filter: blur(2px);
}

.create-project-modal {
  overflow: hidden;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 28px 80px rgba(15, 23, 42, 0.18);
  color: #101828;
}

.create-project-header {
  min-height: 64px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(255, 255, 255, 0.96));
}

.create-project-title {
  color: #101828;
  font-size: 17px;
  font-weight: 700;
  letter-spacing: 0;
}

.create-project-close {
  color: #667085;
}

.create-project-close:hover {
  color: #101828;
}

.create-project-body {
  padding: 28px 32px 24px;
}

.create-project-label {
  margin-bottom: 8px;
  color: #475467;
  font-size: 14px;
  font-weight: 650;
}

.create-project-field,
.create-project-textarea-shell {
  width: 100%;
  border: 1px solid rgba(15, 23, 42, 0.1);
  border-radius: 12px;
  background: #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.9);
  transition: border-color 0.16s ease, box-shadow 0.16s ease, background 0.16s ease;
}

.create-project-field {
  height: 42px;
  padding: 0 14px;
  color: #101828;
  font-size: 14px;
  font-weight: 600;
  outline: none;
}

.create-project-field::placeholder,
.create-project-textarea::placeholder {
  color: #98a2b3;
}

.create-project-field:focus,
.create-project-textarea-shell:focus-within {
  border-color: rgba(139, 92, 246, 0.58);
  background: #ffffff;
  box-shadow: 0 0 0 3px rgba(139, 92, 246, 0.12), inset 0 1px 0 rgba(255, 255, 255, 0.9);
}

.create-project-textarea-shell {
  padding: 12px 12px 10px;
}

.create-project-textarea {
  height: 132px;
  resize: none;
  border: 0;
  background: transparent;
  color: #101828;
  font-size: 14px;
  font-weight: 550;
  line-height: 22px;
  outline: none;
}

.create-project-count-row {
  margin-top: 10px;
  padding-top: 10px;
  border-top: 1px solid rgba(15, 23, 42, 0.08);
  color: #667085;
  font-size: 12px;
  line-height: 1;
}

.create-project-actions {
  padding: 0 32px 28px;
}

.create-project-primary,
.create-project-secondary {
  min-width: 94px;
  height: 40px;
  border-radius: 10px;
  font-size: 14px;
  font-weight: 700;
  transition: background 0.16s ease, border-color 0.16s ease, color 0.16s ease, transform 0.16s ease;
}

.create-project-primary {
  border: 0;
  background: #8b5cf6;
  color: #ffffff;
}

.create-project-primary:not(:disabled):hover {
  background: #7c3aed;
  transform: translateY(-1px);
}

.create-project-primary:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}

.create-project-secondary {
  border: 1px solid rgba(15, 23, 42, 0.12);
  background: #ffffff;
  color: #475467;
}

.create-project-secondary:hover {
  border-color: rgba(139, 92, 246, 0.38);
  color: #101828;
  background: #f8fafc;
}
</style>

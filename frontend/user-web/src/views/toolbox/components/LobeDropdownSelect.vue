<script setup lang="ts">
import { createElement } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LobeDropdownSelectIsland, {
  type LobeDropdownOption,
} from './LobeDropdownSelectIsland'

const props = defineProps<{
  className?: string
  modelValue: number | string
  options: LobeDropdownOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: number | string]
}>()

const mountEl = ref<HTMLDivElement | null>(null)
let root: Root | null = null

const render = () => {
  if (!root) return

  root.render(
    createElement(LobeDropdownSelectIsland, {
      className: props.className,
      options: props.options.map((option) => ({ ...option })),
      value: props.modelValue,
      onChange: (value: number | string) => {
        emit('update:modelValue', value)
      },
    })
  )
}

onMounted(() => {
  if (!mountEl.value) return
  root = createRoot(mountEl.value)
  render()
})

watch(() => [props.modelValue, props.options, props.className], render, { deep: true })

onBeforeUnmount(() => {
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="mountEl" class="lobe-dropdown-select-mount" />
</template>

<style scoped>
.lobe-dropdown-select-mount {
  display: inline-flex;
}
</style>

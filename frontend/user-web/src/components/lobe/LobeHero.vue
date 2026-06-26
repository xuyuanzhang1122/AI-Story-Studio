<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount, watch } from 'vue'
import { createRoot, type Root } from 'react-dom/client'
import { createElement } from 'react'
import HeroIsland from './HeroIsland'

interface Action {
  text: string
  link: string
  type?: 'primary' | 'default'
  github?: boolean
  openExternal?: boolean
}

const props = defineProps<{
  title?: string
  description?: string
  actions?: Action[]
}>()

const mountEl = ref<HTMLDivElement | null>(null)
let root: Root | null = null

const render = () => {
  if (!root) return
  root.render(
    createElement(HeroIsland, {
      title: props.title,
      description: props.description,
      actions: props.actions,
    })
  )
}

onMounted(() => {
  if (!mountEl.value) return
  root = createRoot(mountEl.value)
  render()
})

watch(() => [props.title, props.description, props.actions], render, { deep: true })

onBeforeUnmount(() => {
  root?.unmount()
  root = null
})
</script>

<template>
  <div ref="mountEl" class="lobe-hero-mount" />
</template>

<style scoped>
.lobe-hero-mount {
  width: 100%;
  height: 100%;
  position: relative;
  overflow: hidden;
}

.lobe-hero-mount :deep(.ant-app) {
  width: 100%;
  height: 100%;
}

.lobe-hero-mount :deep(.red-parrot-hero) {
  position: relative;
  width: 100%;
  height: 100%;
  overflow: hidden;
}

.lobe-hero-mount :deep(.red-parrot-hero > .lobe-flex:last-child) {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>

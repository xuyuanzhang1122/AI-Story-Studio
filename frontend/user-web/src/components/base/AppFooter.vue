<script setup lang="ts">
withDefaults(defineProps<{ compact?: boolean }>(), { compact: false })
</script>

<template>
  <p
    class="text-text-tertiary text-center"
    :class="compact ? 'text-[11px]' : 'text-xs'"
  >
    © 2026 红鹦鹉漫剧 · 由 Xumy 开发 ·
    <a
      href="https://github.com/xuyuanzhang1122?tab=repositories"
      target="_blank"
      rel="noopener noreferrer"
      class="hover:text-text-secondary transition-colors underline-offset-2 hover:underline"
    >GitHub</a>
  </p>
</template>

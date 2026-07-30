<script setup lang="ts">
import { ref, watch } from 'vue'

const props = withDefaults(defineProps<{
  src?: string
  custom?: boolean
  alt?: string
}>(), {
  src: '',
  custom: false,
  alt: '',
})

const imageFailed = ref(false)

/// URL 或自定义状态变化表示一张新图片，必须清除旧失败状态重新尝试加载。
watch(() => [props.src, props.custom], () => {
  imageFailed.value = false
})
</script>

<template>
  <img
    v-if="custom && src && !imageFailed"
    :src="src"
    :alt="alt"
    class="object-contain"
    @error="imageFailed = true"
  >
  <UIcon
    v-else
    name="i-lucide-radar"
    aria-hidden="true"
  />
</template>

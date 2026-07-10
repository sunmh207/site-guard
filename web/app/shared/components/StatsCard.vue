<template>
  <UCard>
    <template #header>
      <div class="flex items-center gap-2">
        <UIcon :name="icon" class="w-5 h-5" :class="iconColor"/>
        <span class="font-semibold">{{ title }}</span>
      </div>
    </template>
    <div class="text-3xl font-bold text-highlighted">
      {{ formattedValue }}
    </div>
    <p class="mt-2 text-sm text-muted">{{ description }}</p>
  </UCard>
</template>

<script setup lang="ts">
interface Props {
  title: string
  value: number | string
  description: string
  icon: string
  iconColor?: string
  formatter?: (value: number | string) => string
}

const props = withDefaults(defineProps<Props>(), {
  iconColor: 'text-success',
  formatter: (value) => String(value)
})

const formattedValue = computed(() => {
  return props.formatter(props.value)
})
</script>

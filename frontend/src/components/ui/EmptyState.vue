<template>
  <div class="flex flex-col items-center gap-3 px-4 py-12 text-center">
    <component :is="icon" :size="36" class="text-accent" aria-hidden="true" />
    <h3 class="m-0 font-head text-lg font-semibold">{{ title }}</h3>
    <p v-if="text" class="m-0 max-w-md text-sm leading-relaxed text-muted">{{ text }}</p>
    <slot />
  </div>
</template>

<script setup>
import { Inbox } from 'lucide-vue-next'

defineProps({ title: { type: String, required: true }, text: { type: String, default: '' }, icon: { type: [Object, Function], default: () => Inbox } })
</script>

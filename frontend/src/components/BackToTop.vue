<template>
  <button v-if="visible" type="button" aria-label="回到頂部"
    class="fixed right-4 bottom-24 z-30 flex size-11 cursor-pointer items-center justify-center rounded-full border border-line bg-panel text-text shadow-lg hover:border-accent hover:text-accent lg:right-8 lg:bottom-8"
    @click="toTop">
    <ArrowUp :size="18" aria-hidden="true" />
  </button>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { ArrowUp } from 'lucide-vue-next'

// 回到頂部（SA FR-11.2）：捲動超過一個畫面高度時出現
const visible = ref(false)
const onScroll = () => { visible.value = window.scrollY > window.innerHeight }
const toTop = () => window.scrollTo({ top: 0, behavior: 'smooth' })
onMounted(() => window.addEventListener('scroll', onScroll, { passive: true }))
onBeforeUnmount(() => window.removeEventListener('scroll', onScroll))
</script>

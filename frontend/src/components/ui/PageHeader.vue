<template>
  <header class="flex flex-wrap items-center gap-3">
    <div class="mr-auto flex flex-col gap-1">
      <span class="lbl">{{ eyebrow }}</span>
      <h1 class="m-0 font-head text-[28px] leading-tight font-bold">{{ title }}</h1>
    </div>
    <slot />
    <ThemeToggle />
  </header>
</template>

<script setup>
import ThemeToggle from './ThemeToggle.vue'

// 各頁頂端：英文小標籤＋中文標題＋動作按鈕＋主題切換（示意圖共通）
defineProps({
  title: { type: String, required: true },
  eyebrow: { type: String, default: '' }
})
</script>

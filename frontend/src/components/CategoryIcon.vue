<template>
  <span class="flex shrink-0 items-center justify-center rounded-[9px] border" :style="{ width: size + 'px', height: size + 'px', color, borderColor: color }">
    <component :is="iconOf(icon)" :size="Math.round(size * 0.5)" aria-hidden="true" />
  </span>
</template>

<script setup>
import { iconOf } from '../utils/icons'

// 分類圖示（顏色是使用者選的分類色，屬於資料，不是主題色）
defineProps({ icon: { type: String, default: 'ellipsis' }, color: { type: String, default: 'var(--muted)' }, size: { type: Number, default: 32 } })
</script>

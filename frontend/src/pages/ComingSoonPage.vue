<template>
  <div class="flex flex-col gap-6">
    <PageHeader :eyebrow="`Stage ${route.meta.stage}`" :title="route.meta.title" />
    <AppCard class="flex flex-col items-center gap-3 py-14 text-center">
      <Construction :size="40" class="text-accent" aria-hidden="true" />
      <h2 class="m-0 font-head text-xl font-semibold">施工中</h2>
      <p class="m-0 max-w-md text-sm leading-relaxed text-muted">「{{ route.meta.title }}」預計在階段 {{ route.meta.stage }} 完成，版面請參考頁面示意圖。</p>
      <AppButton variant="ghost" to="/">回到總覽</AppButton>
    </AppCard>
  </div>
</template>

<script setup>
import { useRoute } from 'vue-router'
import { Construction } from 'lucide-vue-next'
import AppButton from '../components/ui/AppButton.vue'
import AppCard from '../components/ui/AppCard.vue'
import PageHeader from '../components/ui/PageHeader.vue'

const route = useRoute()
</script>

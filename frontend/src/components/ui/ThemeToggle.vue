<template>
  <IconButton :label="ui.dark ? '切換為淺色模式' : '切換為深色模式'" @click="ui.toggle()">
    <Sun v-if="ui.dark" :size="18" aria-hidden="true" />
    <Moon v-else :size="18" aria-hidden="true" />
  </IconButton>
</template>

<script setup>
import { Moon, Sun } from 'lucide-vue-next'
import IconButton from './IconButton.vue'
import { useUiStore } from '../../stores/ui'

const ui = useUiStore()
</script>

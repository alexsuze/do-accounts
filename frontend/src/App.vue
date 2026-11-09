<template>
  <RouterView />
  <TransactionDialog />
  <CalculatorPanel />
  <ToastHost />
</template>

<script setup>
import CalculatorPanel from './components/CalculatorPanel.vue'
import TransactionDialog from './components/TransactionDialog.vue'
import ToastHost from './components/ui/ToastHost.vue'
</script>

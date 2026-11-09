<template>
  <RouterView />
  <TransactionDialog />
  <TradeDialog />
  <CalculatorPanel />
  <ToastHost />
</template>

<script setup>
import CalculatorPanel from './components/CalculatorPanel.vue'
import TransactionDialog from './components/TransactionDialog.vue'
import TradeDialog from './components/TradeDialog.vue'
import ToastHost from './components/ui/ToastHost.vue'
</script>

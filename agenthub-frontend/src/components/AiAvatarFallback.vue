<template>
  <div class="ai-avatar-fallback" :class="type">
    <span v-if="type === 'assistant'">📚</span>
    <span v-else>🤖</span>
  </div>
</template>

<script setup>
defineProps({
  type: {
    type: String,
    default: 'default'
  }
})
</script>

<style scoped>
.ai-avatar-fallback {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18px;
  border-radius: 50%;
}

.assistant {
  background: linear-gradient(45deg, #00c9a7, #0e9f6e);
}

.default, .agent {
  background: linear-gradient(45deg, #2f6bd8, #5677fc);
}
</style> 
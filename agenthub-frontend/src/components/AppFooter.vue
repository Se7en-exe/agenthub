<template>
  <footer class="app-footer">
    <div class="footer-inner">
      <div class="brand">
        <span class="brand-name">AgentHub</span>
        <span class="brand-dot">·</span>
        <span class="brand-sub">智能体应用平台</span>
      </div>

      <nav class="links">
        <a href="#">用户协议</a>
        <a href="#">隐私政策</a>
        <a href="https://github.com/Se7en-exe/agenthub" target="_blank" rel="noopener">GitHub</a>
        <a href="mailto:john.wick2926@gmail.com">联系作者</a>
      </nav>

      <div class="meta">
        <span>© {{ currentYear }} AgentHub</span>
        <span class="sep"></span>
        <span>作者 Se7en</span>
      </div>
    </div>
  </footer>
</template>

<script setup>
import { computed } from 'vue'

const currentYear = computed(() => new Date().getFullYear())
</script>

<style scoped>
.app-footer {
  position: relative;
  z-index: 2;
  margin-top: auto;
  background: transparent;
  border-top: 1px solid #e9ecf2;
}

.footer-inner {
  max-width: 1080px;
  margin: 0 auto;
  padding: 24px 28px 30px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  flex-wrap: wrap;
}

.brand {
  display: flex;
  align-items: baseline;
  gap: 7px;
  font-size: 13.5px;
}

.brand-name {
  font-weight: 700;
  color: #101418;
  letter-spacing: -0.2px;
}

.brand-dot {
  color: #c7cdd7;
}

.brand-sub {
  color: #8b94a3;
}

.links {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-wrap: wrap;
}

.links a {
  font-size: 13px;
  color: #6b7484;
  text-decoration: none;
  transition: color 0.22s;
}

.links a:hover {
  color: #2f6bd8;
}

.meta {
  display: flex;
  align-items: center;
  gap: 9px;
  font-size: 12.5px;
  color: #a3abba;
}

.sep {
  width: 1px;
  height: 11px;
  background: #dfe3ea;
}

@media (max-width: 760px) {
  .footer-inner {
    flex-direction: column;
    align-items: flex-start;
    gap: 14px;
    padding: 22px 18px 28px;
  }

  .links {
    gap: 16px;
  }
}
</style>

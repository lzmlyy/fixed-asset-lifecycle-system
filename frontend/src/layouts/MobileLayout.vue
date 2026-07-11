<template>
  <div class="mobile-app">
    <div class="mobile-header">
      <span class="mobile-title">{{ pageTitle }}</span>
      <div class="mobile-header-right">
        <span class="mobile-user">{{ userInfo?.realName || userInfo?.username || '' }}</span>
      </div>
    </div>

    <div class="mobile-body">
      <router-view />
    </div>

    <div class="mobile-tabbar">
      <div
        v-for="tab in tabs"
        :key="tab.path"
        class="tab-item"
        :class="{ active: currentRoute.startsWith(tab.path) }"
        @click="router.push(tab.path)"
      >
        <el-icon :size="20"><component :is="tab.icon" /></el-icon>
        <span class="tab-label">{{ tab.label }}</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { Monitor, Files, List, User } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)

const tabs = [
  { path: '/m/dashboard', label: '首页', icon: Monitor },
  { path: '/m/assets', label: '资产', icon: Files },
  { path: '/m/inventory', label: '盘点', icon: List },
  { path: '/m/me', label: '我的', icon: User },
]

const currentRoute = computed(() => route.path)

const pageTitle = computed(() => {
  const map: Record<string, string> = {
    '/m/dashboard': '首页驾驶舱',
    '/m/assets': '资产台账',
    '/m/inventory': '快速盘点',
    '/m/me': '个人中心',
  }
  return map[currentRoute.value] || ''
})
</script>

<style scoped>
.mobile-app {
  display: flex;
  flex-direction: column;
  height: 100vh;
  max-width: 500px;
  margin: 0 auto;
  background: #f5f6f8;
  position: relative;
}
.mobile-header {
  height: 48px;
  background: #1F4E79;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  flex-shrink: 0;
}
.mobile-title {
  font-size: 16px;
  font-weight: 600;
  color: #fff;
}
.mobile-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}
.mobile-user {
  font-size: 12px;
  color: rgba(255,255,255,0.85);
}
.mobile-body {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
  -webkit-overflow-scrolling: touch;
}
.mobile-tabbar {
  height: 56px;
  background: #fff;
  border-top: 1px solid #e5e7eb;
  display: flex;
  flex-shrink: 0;
  padding-bottom: env(safe-area-inset-bottom, 0);
}
.tab-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 2px;
  color: #999;
  cursor: pointer;
  transition: color 0.2s;
  -webkit-tap-highlight-color: transparent;
}
.tab-item.active {
  color: #1F4E79;
}
.tab-label {
  font-size: 10px;
}
</style>

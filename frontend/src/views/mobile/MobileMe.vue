<template>
  <div class="mm-page">
    <div class="mm-avatar">
      <el-icon :size="48"><UserFilled /></el-icon>
      <div class="mm-name">{{ userInfo?.realName || userInfo?.username || '用户' }}</div>
      <div class="mm-dept">{{ userInfo?.department || '' }}</div>
      <el-tag size="small" type="primary" effect="dark" style="margin-top:6px">{{ roleLabel }}</el-tag>
    </div>

    <div class="mm-menu">
      <div class="mm-menu-item" @click="handleLogout">
        <div class="mm-mi-left"><el-icon><SwitchButton /></el-icon><span>退出登录</span></div>
        <el-icon><ArrowRight /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { ArrowRight, SwitchButton, UserFilled } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)

const roleLabel = computed(() => {
  const roles = authStore.userInfo?.roles || []
  const map: Record<string, string> = { ADMIN: '系统管理员', ASSET_MANAGER: '资产管理员', FINANCE: '财务人员', AUDITOR: '审计人员' }
  for (const r of roles) { if (map[r]) return map[r] }
  return '用户'
})

function handleLogout() {
  authStore.logout()
  router.push('/login')
}
</script>

<style scoped>
.mm-page { padding: 12px; }
.mm-avatar { display: flex; flex-direction: column; align-items: center; padding: 32px 0 24px; background: #fff; border-radius: 12px; margin-bottom: 12px; color: #1F4E79; }
.mm-name { font-size: 18px; font-weight: 700; margin-top: 10px; }
.mm-dept { font-size: 13px; color: #999; margin-top: 4px; }
.mm-menu { background: #fff; border-radius: 12px; }
.mm-menu-item { display: flex; align-items: center; justify-content: space-between; padding: 16px; border-bottom: 1px solid #f5f5f5; }
.mm-menu-item:last-child { border-bottom: none; }
.mm-mi-left { display: flex; align-items: center; gap: 10px; font-size: 15px; color: #333; }
</style>

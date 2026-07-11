<template>
  <div class="m-dash">
    <div class="m-greet">{{ greeting }}，{{ userName }}</div>

    <div class="m-stats">
      <div class="m-stat" style="background:linear-gradient(135deg,#1F4E79,#2A6BA6)">
        <span class="m-stat-num">{{ stats.assetCount ?? '-' }}</span>
        <span class="m-stat-label">资产总数</span>
      </div>
      <div class="m-stat" style="background:linear-gradient(135deg,#18A058,#36D480)">
        <span class="m-stat-num">{{ stats.inUseCount ?? 0 }}</span>
        <span class="m-stat-label">使用中</span>
      </div>
      <div class="m-stat" style="background:linear-gradient(135deg,#F0A020,#FAC050)">
        <span class="m-stat-num">{{ stats.repairingCount ?? 0 }}</span>
        <span class="m-stat-label">维修中</span>
      </div>
      <div class="m-stat" style="background:linear-gradient(135deg,#D03050,#F06070)">
        <span class="m-stat-num">{{ stats.waitingScrapCount ?? 0 }}</span>
        <span class="m-stat-label">待报废</span>
      </div>
    </div>

    <div class="m-section">
      <div class="m-sec-hd">资产净值</div>
      <div style="font-size:22px;font-weight:700;color:#1F4E79;">￥{{ formatMoney(stats.totalNetValue) }}</div>
    </div>

    <div class="m-section">
      <div class="m-sec-hd">进行中的盘点</div>
      <div v-if="inventoryTasks.length" class="m-list">
        <div v-for="t in inventoryTasks" :key="t.id" class="m-list-item" @click="router.push('/m/inventory')">
          <div class="m-li-left">
            <div class="m-li-name">{{ t.taskName }}</div>
            <div class="m-li-sub">{{ t.completedRecords }}/{{ t.totalRecords }} 已盘点</div>
          </div>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-else class="m-empty">暂无进行中的盘点任务</div>
    </div>

    <div class="m-section">
      <div class="m-sec-hd">快捷入口</div>
      <div class="m-grid">
        <div class="m-grid-item" @click="router.push('/m/inventory')"><div class="m-grid-icon"><el-icon :size="24"><List /></el-icon></div><span>快速盘点</span></div>
        <div class="m-grid-item" @click="router.push('/m/assets')"><div class="m-grid-icon"><el-icon :size="24"><Files /></el-icon></div><span>资产台账</span></div>
        <div class="m-grid-item" @click="router.push('/m/me')"><div class="m-grid-icon"><el-icon :size="24"><User /></el-icon></div><span>个人中心</span></div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { getDashboardStats } from '@/api/dashboard'
import { getInventoryTaskPage } from '@/api/inventory'
import { formatMoney } from '@/utils/format'
import { ArrowRight, List, Files, User } from '@element-plus/icons-vue'

const router = useRouter()
const authStore = useAuthStore()
const userInfo = computed(() => authStore.userInfo)
const userName = computed(() => userInfo.value?.realName || userInfo.value?.username || '用户')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '凌晨好'
  if (h < 9) return '早上好'
  if (h < 12) return '上午好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const stats = ref<any>({})
const inventoryTasks = ref<any[]>([])

onMounted(async () => {
  try { const r = await getDashboardStats(); if (r.code === 200) stats.value = r.data } catch {}
  try { const r = await getInventoryTaskPage({ pageNum: 1, pageSize: 5, status: 'IN_PROGRESS' }); if (r.code === 200) inventoryTasks.value = r.data.records || [] } catch {}
})
</script>

<style scoped>
.m-dash { padding: 12px; }
.m-greet { font-size: 20px; font-weight: 700; color: #1a1a2e; margin-bottom: 12px; }
.m-stats { display: grid; grid-template-columns: repeat(2, 1fr); gap: 10px; margin-bottom: 14px; }
.m-stat { border-radius: 12px; padding: 16px; color: #fff; }
.m-stat-num { font-size: 28px; font-weight: 700; display: block; line-height: 1.2; }
.m-stat-label { font-size: 12px; opacity: 0.9; margin-top: 4px; display: block; }
.m-section { background: #fff; border-radius: 12px; padding: 14px; margin-bottom: 12px; }
.m-sec-hd { font-size: 14px; font-weight: 600; color: #333; margin-bottom: 10px; }
.m-list { display: flex; flex-direction: column; }
.m-list-item { display: flex; align-items: center; justify-content: space-between; padding: 10px 0; border-bottom: 1px solid #f0f0f0; }
.m-list-item:last-child { border-bottom: none; }
.m-li-left { flex: 1; }
.m-li-name { font-size: 14px; font-weight: 500; }
.m-li-sub { font-size: 12px; color: #999; margin-top: 2px; }
.m-empty { font-size: 13px; color: #ccc; text-align: center; padding: 16px 0; }
.m-grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; }
.m-grid-item { display: flex; flex-direction: column; align-items: center; gap: 6px; padding: 14px 0; background: #f5f6f8; border-radius: 10px; font-size: 12px; color: #555; }
.m-grid-icon { width: 44px; height: 44px; background: #e8ecf1; border-radius: 12px; display: flex; align-items: center; justify-content: center; color: #1F4E79; }
</style>

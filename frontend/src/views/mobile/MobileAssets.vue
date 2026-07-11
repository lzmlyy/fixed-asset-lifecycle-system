<template>
  <div class="ma-page">
    <div class="ma-search">
      <el-input v-model="keyword" placeholder="搜索资产编号或名称" clearable size="large" @clear="fetchData" @keyup.enter="fetchData">
        <template #prefix><el-icon><Search /></el-icon></template>
      </el-input>
    </div>

    <div class="ma-status-row">
      <el-tag v-for="s in statusTags" :key="s.code" :type="curStatus === s.code ? '' : 'info'" size="large" effect="plain" style="margin-right:8px;margin-bottom:8px" @click="filterStatus(s.code)">
        {{ s.label }}
      </el-tag>
    </div>

    <div class="ma-list" v-infinite-scroll="loadMore" :infinite-scroll-disabled="noMore" infinite-scroll-distance="80">
      <div v-for="item in list" :key="item.id" class="ma-item" @click="viewDetail(item.id)">
        <div class="ma-item-top">
          <span class="ma-item-code">{{ item.assetCode }}</span>
          <el-tag size="small" :type="statusTagType(item.status)">{{ statusText(item.status) }}</el-tag>
        </div>
        <div class="ma-item-name">{{ item.assetName }}</div>
        <div class="ma-item-meta">
          <span>{{ item.department || '-' }}</span>
          <span>{{ item.keeper || '-' }}</span>
          <span>{{ item.location || '-' }}</span>
        </div>
        <div class="ma-item-bottom">
          <span>净值 ¥{{ formatMoney(item.netValue) }}</span>
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
      <div v-if="loading" class="ma-loading"><el-icon class="is-loading"><Loading /></el-icon> 加载中...</div>
      <div v-if="noMore && list.length > 0" class="ma-end">— 没有更多了 —</div>
      <div v-if="!loading && list.length === 0" class="ma-empty">
        <el-empty description="暂无资产" :image-size="80" />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { getAssetPage } from '@/api/asset'
import { formatMoney } from '@/utils/format'
import { ArrowRight, Search, Loading } from '@element-plus/icons-vue'

const keyword = ref('')
const curStatus = ref('')
const list = ref<any[]>([])
const loading = ref(false)
const noMore = ref(false)
let pageNum = 1
const pageSize = 15

const statusTags = [
  { code: '', label: '全部' },
  { code: 'IN_USE', label: '使用中' },
  { code: 'IDLE', label: '闲置' },
  { code: 'REPAIRING', label: '维修中' },
  { code: 'WAITING_SCRAP', label: '待报废' },
]

function filterStatus(code: string) {
  curStatus.value = curStatus.value === code ? '' : code
  list.value = []
  pageNum = 1
  noMore.value = false
  fetchData()
}

function statusText(s: string) {
  const map: Record<string, string> = { IDLE: '闲置', IN_USE: '使用中', REPAIRING: '维修中', WAITING_SCRAP: '待报废', SCRAPPED: '已报废' }
  return map[s] || s
}

function statusTagType(s: string) {
  const map: Record<string, string> = { IDLE: 'info', IN_USE: 'success', REPAIRING: 'warning', WAITING_SCRAP: 'danger', SCRAPPED: 'info' }
  return map[s] || 'info'
}

async function fetchData() {
  if (loading.value) return
  loading.value = true
  try {
    const params: Record<string, any> = { pageNum, pageSize }
    if (keyword.value) params.assetCode = keyword.value
    if (curStatus.value) params.status = curStatus.value
    const r = await getAssetPage(params)
    const records = r.data.records || []
    if (pageNum === 1) list.value = records
    else list.value.push(...records)
    if (records.length < pageSize) noMore.value = true
  } catch {} finally { loading.value = false }
}

function loadMore() {
  if (noMore.value || loading.value) return
  pageNum++
  fetchData()
}

function viewDetail(id: number) {
  window.open(`/assets/${id}`, '_blank')
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.ma-page { padding: 12px; }
.ma-search { margin-bottom: 10px; }
.ma-status-row { margin-bottom: 8px; display: flex; flex-wrap: wrap; }
.ma-list { display: flex; flex-direction: column; gap: 10px; }
.ma-item { background: #fff; border-radius: 10px; padding: 12px 14px; box-shadow: 0 1px 3px rgba(0,0,0,0.06); }
.ma-item-top { display: flex; justify-content: space-between; align-items: center; }
.ma-item-code { font-size: 14px; font-weight: 700; font-family: monospace; }
.ma-item-name { font-size: 15px; font-weight: 500; margin: 6px 0; }
.ma-item-meta { display: flex; gap: 12px; font-size: 12px; color: #888; }
.ma-item-meta span::before { content: '·'; margin-right: 4px; }
.ma-item-meta span:first-child::before { content: none; }
.ma-item-bottom { display: flex; justify-content: space-between; align-items: center; margin-top: 8px; font-size: 13px; color: #1F4E79; font-weight: 600; }
.ma-loading { text-align: center; padding: 16px; color: #999; font-size: 13px; }
.ma-end { text-align: center; padding: 12px; color: #ccc; font-size: 12px; }
.ma-empty { padding: 40px 0; }
</style>

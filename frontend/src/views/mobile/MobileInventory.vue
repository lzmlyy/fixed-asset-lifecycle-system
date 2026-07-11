<template>
  <div class="mi-page">
    <div class="mi-tabs">
      <span class="mi-tab" :class="{ active: scanMode === 'qr' }" @click="switchMode('qr')">扫码</span>
      <span class="mi-tab" :class="{ active: scanMode === 'ocr' }" @click="switchMode('ocr')">拍照</span>
      <span class="mi-tab" :class="{ active: scanMode === 'man' }" @click="switchMode('man')">输入</span>
    </div>

    <div class="mi-body">
      <div v-if="scanMode === 'qr'" class="mi-qr-wrap">
        <div v-if="!qrImg" class="mi-qr-empty">
          <el-button type="primary" size="large" @click="scanQr" style="width:80%">
            <el-icon :size="22"><Camera /></el-icon> 点击扫码
          </el-button>
          <p class="mi-qr-sub">拍照后自动识别图中的二维码</p>
          <input ref="qrInp" type="file" accept="image/*" capture="environment" style="display:none" @change="onQrFile" />
        </div>
        <div v-else class="mi-qr-has">
          <img :src="qrImg" class="mi-qr-img" />
          <div v-if="qrBusy" class="mi-qr-wait"><el-icon class="is-loading"><Loading /></el-icon> 解码中...</div>
          <div v-else-if="qrDecoded" class="mi-qr-ok">
            <el-tag type="success" size="large">识别成功</el-tag>
            <span style="font-size:16px;font-weight:700;font-family:monospace;margin-left:8px">{{ qrDecoded }}</span>
          </div>
          <div v-else class="mi-qr-fail">
            <el-tag type="danger">未识别到二维码</el-tag>
            <el-button size="small" style="margin-top:8px" @click="qrImg=null;qrDecoded=null">重拍</el-button>
          </div>
        </div>
      </div>

      <div v-if="scanMode === 'ocr'" class="mi-ocr">
        <div v-if="!ocrImg" class="mi-ocr-empty">
          <el-button type="primary" size="large" @click="takePhoto" style="width:100%"><el-icon><Camera /></el-icon> 拍照识别</el-button>
          <input ref="camInp" type="file" accept="image/*" capture="environment" style="display:none" @change="onCam" />
        </div>
        <div v-else class="mi-ocr-has">
          <img :src="ocrImg" class="mi-ocr-img" />
          <div v-if="ocrBusy" class="mi-ocr-wait"><el-icon class="is-loading"><Loading /></el-icon> 识别中...</div>
          <el-input v-model="ocrCode" placeholder="提取到的资产编号" size="large" style="margin-top:8px" :disabled="ocrBusy" clearable />
          <el-button type="primary" size="large" :disabled="!ocrCode || ocrBusy" @click="lookupByOcr" style="width:100%;margin-top:8px">查找资产</el-button>
          <el-button size="small" style="margin-top:4px" @click="ocrImg=null;ocrTxt='';ocrCode=''">重拍</el-button>
        </div>
      </div>

      <div v-if="scanMode === 'man'" class="mi-man">
        <el-input v-model="manCode" placeholder="输入资产编号" size="large" clearable @keyup.enter="lookupMan" />
        <el-button type="primary" size="large" :disabled="!manCode" @click="lookupMan" style="margin-top:12px;width:100%">查找</el-button>
      </div>

      <div v-if="currentAsset" class="mi-card">
        <div class="mi-card-hd">
          <span>资产信息</span>
          <el-tag v-if="currentAsset._tag" :type="currentAsset._tagType" size="small">{{ currentAsset._tag }}</el-tag>
        </div>
        <div class="mi-card-body">
          <div class="mi-row"><span class="mi-label">编号</span><span class="mi-val">{{ currentAsset.assetCode }}</span></div>
          <div class="mi-row"><span class="mi-label">名称</span><span class="mi-val">{{ currentAsset.assetName }}</span></div>
          <div class="mi-row"><span class="mi-label">期望地点</span><span class="mi-val">{{ currentAsset.expectedLocation || '-' }}</span></div>
          <div class="mi-row"><span class="mi-label">期望保管人</span><span class="mi-val">{{ currentAsset.expectedKeeper || '-' }}</span></div>
        </div>
        <el-button type="success" size="large" :disabled="currentAsset._scanned" :loading="scanning" @click="doScan" style="width:100%;margin-top:8px">
          {{ currentAsset._scanned ? '已盘点' : '确认盘点' }}
        </el-button>
      </div>

      <div v-if="scanLog.length" class="mi-log">
        <div class="mi-log-hd">扫描记录 ({{ scanLog.length }})</div>
        <div v-for="(s,i) in scanLog" :key="i" class="mi-log-it">
          <span style="font-size:13px;font-weight:600;font-family:monospace">{{ s.code }}</span>
          <el-tag :type="s.ok?'success':'danger'" size="small">{{ s.ok?'已确认':'未找到' }}</el-tag>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import { Loading, Camera } from '@element-plus/icons-vue'
import { BinaryBitmap, DecodeHintType, BarcodeFormat, GlobalHistogramBinarizer, HybridBinarizer, QRCodeReader, RGBLuminanceSource } from '@zxing/library'
import { getInventoryTaskPage, createInventoryTask, lookupInventoryRecord, scanInventoryRecord, performOcr } from '@/api/inventory'

const scanMode = ref('qr')

const qrImg = ref<string|null>(null)
const qrBusy = ref(false)
const qrDecoded = ref<string|null>(null)
const qrInp = ref<HTMLInputElement|null>(null)

const ocrImg = ref<string|null>(null)
const ocrTxt = ref('')
const ocrBusy = ref(false)
const ocrCode = ref('')
const manCode = ref('')
const camInp = ref<HTMLInputElement|null>(null)

const currentAsset = ref<any>(null)
const scanning = ref(false)
const scanLog = ref<{code:string;name:string;ok:boolean}[]>([])
const scanned = ref<Set<string>>(new Set())
let taskId = 0

function switchMode(m: string) {
  scanMode.value = m
  if (m !== 'qr') {
    if (qrImg.value && qrImg.value.startsWith('blob:')) { URL.revokeObjectURL(qrImg.value) }
    qrImg.value = null; qrDecoded.value = null
  }
  if (m !== 'ocr') {
    if (ocrImg.value && ocrImg.value.startsWith('blob:')) { URL.revokeObjectURL(ocrImg.value) }
    ocrImg.value = null; ocrTxt.value = ''; ocrCode.value = ''
  }
}

async function ensureTask() {
  if (taskId > 0) return
  try {
    const r = await getInventoryTaskPage({ pageNum: 1, pageSize: 1, status: 'IN_PROGRESS' })
    const records = r.data?.records || []
    if (records.length > 0) { taskId = records[0].id; return }
    const cr = await createInventoryTask({ taskName: '移动端盘点', scopeType: 'ALL' })
    taskId = cr.data
    await new Promise(r => setTimeout(r, 500))
  } catch { ElMessage.error('创建盘点任务失败') }
}

function scanQr() { qrInp.value?.click() }

async function onQrFile(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  // 先清除旧图片的 Blob URL，防止内存泄露
  if (qrImg.value && qrImg.value.startsWith('blob:')) { URL.revokeObjectURL(qrImg.value) }
  qrImg.value = URL.createObjectURL(f); qrBusy.value = true
  // 清空 input 值，确保选择同一文件时仍能触发 change 事件
  input.value = ''
  try {
    const img = await loadImage(f)
    const result = decodeQrFromImage(img)
    if (result) {
      qrDecoded.value = result
      await ensureTask()
      await doLookup(result.trim())
    } else {
      qrDecoded.value = null
      ElMessage.warning('未识别到二维码，请确保二维码清晰且光线充足')
    }
  } catch { qrDecoded.value = null; ElMessage.error('解码失败，请重试') } finally { qrBusy.value = false }
}

async function doLookup(code: string) {
  if (!code) return
  // 前端去重：已确认扫描过的直接提示
  if (scanned.value.has(code)) {
    scanLog.value.unshift({ code, name: '', ok: true })
    ElMessage.warning('该资产已盘点过')
    return
  }
  if (!taskId) await ensureTask()
  try {
    const r = await lookupInventoryRecord(taskId, code)
    if (!r.data || r.data.result === 'NOT_IN_SCOPE') {
      scanLog.value.unshift({ code, name: r.data?.assetName || '', ok: false })
      ElMessage.error(r.data ? '该资产未在盘点范围内' : '未找到该资产')
      return
    }
    const asset = { ...r.data, _scanned: r.data.scanned }
    if (asset._scanned) {
      scanned.value.add(code)
      const map: Record<string,string> = { NORMAL: '正常', LOCATION_MISMATCH: '地点不符', KEEPER_MISMATCH: '保管人不符' }
      const tags: Record<string,string> = { NORMAL: 'success', LOCATION_MISMATCH: 'warning', KEEPER_MISMATCH: 'warning' }
      asset._tag = '已盘点 - ' + (map[r.data.result] || r.data.result)
      asset._tagType = tags[r.data.result] || 'info'
    } else {
      asset._tag = '待盘点'; asset._tagType = 'info'
    }
    currentAsset.value = asset
  } catch {
    scanLog.value.unshift({ code, name: '', ok: false })
    ElMessage.error('查询失败，请检查网络连接')
  }
}

async function doScan() {
  if (!currentAsset.value) return
  const code = currentAsset.value.assetCode
  if (scanned.value.has(code)) { ElMessage.warning('已扫描过'); return }
  scanning.value = true
  try {
    await scanInventoryRecord({ recordId: currentAsset.value.recordId, actualLocation: '', actualKeeper: '', result: 'NORMAL' })
    scanned.value.add(code)
    scanLog.value.unshift({ code, name: currentAsset.value.assetName, ok: true })
    ElMessage.success('已确认: ' + code)
    currentAsset.value = null
  } catch {} finally { scanning.value = false }
}

function takePhoto() { camInp.value?.click() }

async function onCam(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  // 先清除旧图片的 Blob URL，防止内存泄露
  if (ocrImg.value && ocrImg.value.startsWith('blob:')) { URL.revokeObjectURL(ocrImg.value) }
  ocrImg.value = URL.createObjectURL(f); ocrBusy.value = true
  // 清空 input 值，确保选择同一文件时仍能触发 change 事件
  input.value = ''

  try {
    const img = await loadImage(f)
    const qrRes = decodeQrFromImage(img)
    if (qrRes) {
      ocrCode.value = qrRes.match(/(?:FA|ZC)[A-Z0-9\-]+/i)?.[0].toUpperCase() || qrRes
      ElMessage.success('识别到二维码: ' + qrRes)
      ocrBusy.value = false; return
    }

    const fd = new FormData(); fd.append('image', f)
    const r = await performOcr(fd)
    const text = r.data?.text || ''
    const m = text.match(/(?:FA|ZC)[A-Z0-9\-]+/i)
    ocrCode.value = m ? m[0].toUpperCase() : ''
    if (!ocrCode.value) ElMessage.warning('未提取到资产编号，请手动输入')
  } catch { ElMessage.error('识别失败，请重试') }
  finally { ocrBusy.value = false }
}

function loadImage(file: File): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image()
    const url = URL.createObjectURL(file)
    img.onload = () => { URL.revokeObjectURL(url); resolve(img) }
    img.onerror = () => { URL.revokeObjectURL(url); reject(new Error('图片加载失败')) }
    img.src = url
  })
}

function decodeQrFromImage(img: HTMLImageElement): string | null {
  try {
    const canvas = document.createElement('canvas')
    const maxSize = 1024
    let w = img.naturalWidth, h = img.naturalHeight
    const scale = Math.min(1, maxSize / Math.max(w, h))
    w = Math.round(w * scale); h = Math.round(h * scale)
    canvas.width = w; canvas.height = h
    const ctx = canvas.getContext('2d', { willReadFrequently: true })
    if (!ctx) return null
    ctx.drawImage(img, 0, 0, w, h)
    const imageData = ctx.getImageData(0, 0, w, h)
    // 关键修复：imageData.data 是 Uint8ClampedArray（RGBA，4字节/像素），
    // RGBLuminanceSource 需要 Int32Array（每元素一个 RGBA 像素）才能正确解析
    const srcData = new Int32Array(imageData.data.buffer, imageData.data.byteOffset, w * h)
    const source = new RGBLuminanceSource(srcData, w, h)

    const hints = new Map()
    hints.set(DecodeHintType.TRY_HARDER, true)
    hints.set(DecodeHintType.POSSIBLE_FORMATS, [BarcodeFormat.QR_CODE])

    const reader = new QRCodeReader()
    // 尝试 HybridBinarizer
    try {
      const bitmap = new BinaryBitmap(new HybridBinarizer(source))
      const result = reader.decode(bitmap, hints)
      return result.getText()
    } catch { /* fall through */ }
    // 尝试 GlobalHistogramBinarizer
    try {
      const bitmap = new BinaryBitmap(new GlobalHistogramBinarizer(source))
      const result = reader.decode(bitmap, hints)
      return result.getText()
    } catch { return null }
  } catch { return null }
}

function lookupByOcr() { if (ocrCode.value) { ensureTask(); doLookup(ocrCode.value.trim()) } }
function lookupMan() { if (manCode.value) { ensureTask(); doLookup(manCode.value.trim()); manCode.value = '' } }
</script>

<style scoped>
.mi-page { display: flex; flex-direction: column; height: 100%; }
.mi-tabs { display: flex; background: #fff; border-bottom: 1px solid #eee; padding: 0 8px; }
.mi-tab { flex: 1; text-align: center; padding: 12px 0; font-size: 15px; color: #666; border-bottom: 2px solid transparent; transition: all 0.2s; }
.mi-tab.active { color: #1F4E79; border-bottom-color: #1F4E79; font-weight: 600; }
.mi-body { flex: 1; padding: 12px; overflow-y: auto; }
.mi-qr-wrap { background: #0d1117; border-radius: 12px; min-height: 240px; display: flex; align-items: center; justify-content: center; margin-bottom: 12px; }
.mi-qr-empty { text-align: center; display: flex; flex-direction: column; align-items: center; gap: 12px; }
.mi-qr-sub { font-size: 13px; color: rgba(255,255,255,0.5); margin: 0; }
.mi-qr-has { display: flex; flex-direction: column; align-items: center; padding: 12px; }
.mi-qr-img { width: 100%; max-height: 220px; object-fit: contain; border-radius: 8px; background: #000; }
.mi-qr-wait { color: rgba(255,255,255,0.6); text-align: center; padding: 12px 0; font-size: 13px; }
.mi-qr-ok { display: flex; align-items: center; gap: 8px; padding: 12px 0; }
.mi-qr-fail { display: flex; flex-direction: column; align-items: center; padding: 12px 0; }
.mi-load { color: #666; }
.mi-ocr { margin-bottom: 12px; }
.mi-ocr-empty { padding: 40px 0; }
.mi-ocr-has { display: flex; flex-direction: column; }
.mi-ocr-img { width: 100%; max-height: 240px; object-fit: contain; border-radius: 8px; background: #000; }
.mi-ocr-wait { color: #999; text-align: center; padding: 12px 0; font-size: 13px; }
.mi-man { margin-bottom: 12px; }
.mi-card { background: #fff; border-radius: 12px; padding: 14px; margin-bottom: 12px; }
.mi-card-hd { display: flex; justify-content: space-between; align-items: center; font-size: 15px; font-weight: 600; margin-bottom: 10px; }
.mi-card-body { display: flex; flex-direction: column; gap: 8px; }
.mi-row { display: flex; }
.mi-label { color: #999; font-size: 13px; width: 80px; flex-shrink: 0; }
.mi-val { font-size: 13px; font-weight: 500; }
.mi-log { background: #fff; border-radius: 12px; padding: 12px; }
.mi-log-hd { font-size: 14px; font-weight: 600; margin-bottom: 8px; }
.mi-log-it { display: flex; justify-content: space-between; align-items: center; padding: 6px 0; border-bottom: 1px solid #f5f5f5; }
.mi-log-it:last-child { border-bottom: none; }
</style>

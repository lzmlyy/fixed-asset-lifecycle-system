<template>
  <el-dialog v-model="visibleModal" fullscreen :close-on-click-modal="false" @closed="stopAll" class="scan-dialog">
    <template #header>
      <div style="display:flex;align-items:center;gap:12px;">
        <span style="font-size:18px;font-weight:600;">扫码盘点</span>
        <el-tag v-if="currentTask" type="info" size="small">{{ currentTask.taskName }}</el-tag>
      </div>
    </template>

    <div class="scan-body">
      <div class="scan-left">
        <el-tabs v-model="mode" class="scan-tabs">
          <el-tab-pane label="二维码扫码" name="qr">
            <div class="qr-scanner-wrapper">
              <div id="qr-reader" ref="qrReaderRef" class="qr-reader"></div>
              <div v-if="qrError" class="qr-error">
                <el-alert type="warning" :closable="false" show-icon>
                  <template #title>{{ qrError }}</template>
                </el-alert>
                <el-button type="primary" style="margin-top:12px;" @click="initQrScanner">重新启动</el-button>
              </div>
              <div v-if="!qrInitialized && !qrError" class="qr-loading">
                <el-icon class="is-loading" :size="32"><Loading /></el-icon>
                <p>正在启动摄像头...</p>
              </div>
            </div>
          </el-tab-pane>

          <el-tab-pane label="拍照 OCR" name="ocr">
            <div class="ocr-wrapper">
              <div class="ocr-input-area">
                <div v-if="!ocrImage" class="ocr-placeholder">
                  <el-button type="primary" size="large" @click="triggerCamera" :icon="Camera">
                    拍照识别
                  </el-button>
                  <el-button size="large" style="margin-left:16px;" @click="triggerUpload">
                    上传图片
                  </el-button>
                  <input ref="cameraInputRef" type="file" accept="image/*" capture="environment" style="display:none" @change="onCameraCapture" />
                  <input ref="uploadInputRef" type="file" accept="image/*" style="display:none" @change="onFileUpload" />
                </div>
                <div v-else class="ocr-preview">
                  <img :src="ocrImage" alt="OCR 预览" class="ocr-preview-img" />
                  <div class="ocr-actions">
                    <el-button size="small" @click="resetOcr">重新拍照</el-button>
                  </div>
                </div>
              </div>
              <div class="ocr-result-area" v-if="ocrImage">
                <div v-if="ocrLoading" class="ocr-loading-state">
                  <el-icon class="is-loading" :size="24"><Loading /></el-icon>
                  <span>正在识别文字...</span>
                </div>
                <div v-else class="ocr-text-result">
                  <div class="ocr-label">识别结果（可编辑修正）：</div>
                  <el-input v-model="ocrText" type="textarea" :rows="4" placeholder="识别出的文字..." />
                  <div class="ocr-match">
                    <el-input v-model="extractedAssetCode" placeholder="资产编号" size="default" style="width:200px">
                      <template #prepend>资产编号</template>
                    </el-input>
                    <el-button type="primary" :disabled="!extractedAssetCode" @click="doLookupFromOcr" style="margin-left:8px;">
                      查找资产
                    </el-button>
                  </div>
                </div>
              </div>
            </div>
          </el-tab-pane>
        </el-tabs>
      </div>

      <div class="scan-right">
        <div class="scan-controls">
          <div class="control-row">
            <span class="control-label">快速确认：</span>
            <el-switch v-model="quickConfirm" active-text="扫码即确认" inactive-text="扫码定位" />
          </div>
        </div>

        <div class="scan-results">
          <div class="results-header">
            <span>扫描记录</span>
            <el-tag size="small">{{ scanResults.length }}</el-tag>
          </div>
          <div class="results-list" v-if="scanResults.length > 0">
            <div v-for="(r, i) in scanResults" :key="i" class="result-item" :class="'result-' + r.result">
              <div class="result-top">
                <span class="result-code">{{ r.assetCode }}</span>
                <el-tag :type="r.result === 'matched' ? 'success' : r.result === 'already_scanned' ? 'warning' : 'danger'" size="small">
                  {{ r.result === 'matched' ? '已确认' : r.result === 'already_scanned' ? '已扫过' : '未找到' }}
                </el-tag>
              </div>
              <div class="result-name">{{ r.assetName || '-' }}</div>
            </div>
          </div>
          <el-empty v-else description="暂无扫描记录" :image-size="60" />
        </div>
      </div>
    </div>

    <template #footer>
      <el-button @click="close">关闭</el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Loading, Camera } from '@element-plus/icons-vue'
import { Html5Qrcode } from 'html5-qrcode'
import { lookupInventoryRecord, performOcr, scanInventoryRecord } from '@/api/inventory'

const props = defineProps<{
  visible: boolean
  taskId: number
  currentTask?: any
  existingResults?: Set<string>
}>()

const emit = defineEmits<{
  'update:visible': [value: boolean]
  'scanned': []
  'locate': [recordId: number]
}>()

const visibleModal = ref(false)
const mode = ref('qr')
const quickConfirm = ref(true)
const scanResults = ref<ScanResult[]>([])
const scannedCodes = ref<Set<string>>(new Set(props.existingResults))

const qrReaderRef = ref(null)
const qrInitialized = ref(false)
const qrError = ref('')
let html5QrCode: Html5Qrcode | null = null

const ocrImage = ref<string | null>(null)
const ocrText = ref('')
const ocrLoading = ref(false)
const extractedAssetCode = ref('')
const cameraInputRef = ref<HTMLInputElement | null>(null)
const uploadInputRef = ref<HTMLInputElement | null>(null)

interface ScanResult {
  assetCode: string
  assetName: string
  recordId: number | null
  result: 'matched' | 'already_scanned' | 'not_found'
}

watch(() => props.visible, (v) => {
  visibleModal.value = v
  if (v) {
    scannedCodes.value = new Set(props.existingResults)
    nextTick(() => {
      if (mode.value === 'qr') {
        initQrScanner()
      }
    })
  }
})

watch(visibleModal, (v) => {
  emit('update:visible', v)
})

watch(mode, (newMode) => {
  if (newMode === 'qr') {
    stopOcr()
    nextTick(() => {
      if (visibleModal.value) {
        initQrScanner()
      }
    })
  } else {
    stopQrScanner()
  }
})

function stopAll() {
  stopQrScanner()
  stopOcr()
}

async function initQrScanner() {
  stopQrScanner()
  qrError.value = ''
  qrInitialized.value = false
  try {
    html5QrCode = new Html5Qrcode('qr-reader')
    try {
      await html5QrCode.start(
        { facingMode: 'environment' },
        { fps: 10, qrbox: { width: 250, height: 250 } },
        onQrScanned,
        () => {}
      )
      qrInitialized.value = true
    } catch {
      try {
        await html5QrCode.start(
          { facingMode: { exact: 'user' } },
          { fps: 10, qrbox: { width: 250, height: 250 } },
          onQrScanned,
          () => {}
        )
        qrInitialized.value = true
      } catch (e2: any) {
        qrError.value = '摄像头启动失败: ' + (e2?.message || '未知错误')
      }
    }
  } catch (e: any) {
    qrError.value = e?.message || '未知错误'
  }
}

function stopQrScanner() {
  if (html5QrCode) {
    html5QrCode.stop().catch(() => {})
    html5QrCode = null
  }
  qrInitialized.value = false
}

function stopOcr() {
  ocrImage.value = null
  ocrText.value = ''
  extractedAssetCode.value = ''
  ocrLoading.value = false
}

async function onQrScanned(decodedText: string) {
  if (!decodedText) return
  await doLookup(decodedText.trim())
}

async function doLookup(assetCode: string) {
  if (!assetCode || scannedCodes.value.has(assetCode)) {
    const existing = scanResults.value.find(r => r.assetCode === assetCode)
    if (existing) {
      ElMessage.warning('该资产已盘点')
    }
    return
  }

  scannedCodes.value.add(assetCode)

  try {
    const r = await lookupInventoryRecord(props.taskId, assetCode)
    const data = r.data

    if (!data || data.result === 'NOT_IN_SCOPE') {
      scanResults.value.unshift({
        assetCode,
        assetName: data?.assetName || '',
        recordId: null,
        result: 'not_found'
      })
      if (scanResults.value.length > 5) scanResults.value.pop()
      ElMessage.error('未在盘点范围内')
      return
    }

    if (data.scanned) {
      scanResults.value.unshift({
        assetCode,
        assetName: data.assetName,
        recordId: data.recordId,
        result: 'already_scanned'
      })
      if (scanResults.value.length > 5) scanResults.value.pop()
      ElMessage.warning('该资产已盘点')
      return
    }

    if (quickConfirm.value) {
      await scanInventoryRecord({
        recordId: data.recordId,
        actualLocation: '',
        actualKeeper: '',
        result: 'NORMAL'
      })
      scanResults.value.unshift({
        assetCode,
        assetName: data.assetName,
        recordId: data.recordId,
        result: 'matched'
      })
      if (scanResults.value.length > 5) scanResults.value.pop()
      ElMessage.success('已确认 - ' + assetCode + ' ' + data.assetName)
      emit('scanned')
    } else {
      scanResults.value.unshift({
        assetCode,
        assetName: data.assetName,
        recordId: data.recordId,
        result: 'matched'
      })
      if (scanResults.value.length > 5) scanResults.value.pop()
      ElMessage.success('已定位 - ' + assetCode + ' ' + data.assetName)
      emit('locate', data.recordId)
    }
  } catch {
    scanResults.value.unshift({
      assetCode,
      assetName: '',
      recordId: null,
      result: 'not_found'
    })
    if (scanResults.value.length > 5) scanResults.value.pop()
  }
}

function triggerCamera() {
  cameraInputRef.value?.click()
}

function triggerUpload() {
  uploadInputRef.value?.click()
}

async function onCameraCapture(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  await processImage(file)
}

async function onFileUpload(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  await processImage(file)
}

async function processImage(file: File) {
  ocrImage.value = URL.createObjectURL(file)
  ocrLoading.value = true
  ocrText.value = ''

  try {
    const formData = new FormData()
    formData.append('image', file)
    const r = await performOcr(formData)
    const text = r.data?.text || ''
    ocrText.value = text

    const assetCodeMatch = text.match(/(?:FA|ZC)[A-Z0-9\-]+/i)
    if (assetCodeMatch) {
      extractedAssetCode.value = assetCodeMatch[0].toUpperCase()
    } else {
      extractedAssetCode.value = ''
      ElMessage.warning('未能从识别结果中提取资产编号，请手动输入')
    }
  } catch {
    ElMessage.error('OCR 识别失败')
  } finally {
    ocrLoading.value = false
  }
}

async function doLookupFromOcr() {
  if (extractedAssetCode.value) {
    await doLookup(extractedAssetCode.value.trim())
  }
}

function resetOcr() {
  ocrImage.value = null
  ocrText.value = ''
  extractedAssetCode.value = ''
  ocrLoading.value = false
}

function close() {
  visibleModal.value = false
}

onBeforeUnmount(() => {
  stopAll()
})
</script>

<style scoped>
.scan-dialog :deep(.el-dialog__body) {
  padding: 8px 16px;
  height: calc(100vh - 120px);
  overflow: hidden;
}

.scan-body {
  display: flex;
  height: 100%;
  gap: 16px;
}

.scan-left {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.scan-tabs {
  flex: 1;
  display: flex;
  flex-direction: column;
}

.scan-tabs :deep(.el-tabs__content) {
  flex: 1;
  overflow: hidden;
}

.scan-tabs :deep(.el-tab-pane) {
  height: 100%;
}

.qr-scanner-wrapper {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
  min-height: 400px;
}

.qr-reader {
  width: 100%;
  max-width: 500px;
}

.qr-reader :deep(video) {
  width: 100% !important;
  max-height: 60vh !important;
  object-fit: contain;
}

.qr-reader :deep(#qr-shaded-region) {
  border-width: 50px !important;
}

.qr-error, .qr-loading {
  text-align: center;
  padding: 40px;
}

.qr-loading p {
  margin-top: 12px;
  color: #999;
}

.ocr-wrapper {
  display: flex;
  height: 100%;
  gap: 16px;
}

.ocr-input-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 8px;
  min-height: 400px;
}

.ocr-placeholder {
  text-align: center;
}

.ocr-preview {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.ocr-preview-img {
  max-width: 100%;
  max-height: 400px;
  object-fit: contain;
  border-radius: 6px;
}

.ocr-actions {
  margin-top: 12px;
}

.ocr-result-area {
  width: 320px;
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.ocr-loading-state {
  text-align: center;
  color: #999;
}

.ocr-loading-state span {
  display: block;
  margin-top: 8px;
}

.ocr-text-result {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.ocr-label {
  font-size: 13px;
  color: #666;
}

.ocr-match {
  display: flex;
  align-items: center;
}

.scan-right {
  width: 300px;
  display: flex;
  flex-direction: column;
  border-left: 1px solid var(--color-border);
  padding-left: 16px;
}

.scan-controls {
  padding: 12px 0;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 12px;
}

.control-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.control-label {
  font-size: 14px;
  font-weight: 500;
}

.scan-results {
  flex: 1;
  overflow-y: auto;
}

.results-header {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 8px;
}

.results-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.result-item {
  padding: 10px 12px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
}

.result-matched {
  border-left: 3px solid #67c23a;
}

.result-already_scanned {
  border-left: 3px solid #e6a23c;
}

.result-not_found {
  border-left: 3px solid #f56c6c;
}

.result-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-code {
  font-size: 13px;
  font-weight: 600;
  font-family: monospace;
}

.result-name {
  font-size: 12px;
  color: #999;
  margin-top: 4px;
}
</style>

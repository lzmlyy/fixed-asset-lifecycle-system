<template>
  <div class="inventory-task">
    <PageHeader title="盘点管理" description="资产盘点任务与进度跟踪">
      <template #actions>
        <el-button type="primary" @click="openCreateDialog">
          <el-icon><Plus /></el-icon>新建任务
        </el-button>
        <el-button type="success" :loading="exporting" @click="handleExportTasks">
          <el-icon><Download /></el-icon>导出
        </el-button>
      </template>
    </PageHeader>

    <!-- 筛选区 -->
    <div class="filter-bar">
      <span class="filter-label">任务状态：</span>
      <el-select v-model="filterStatus" placeholder="全部状态" clearable style="width: 180px" @change="loadTasks">
        <el-option label="进行中" value="IN_PROGRESS" />
        <el-option label="已完成" value="COMPLETED" />
      </el-select>
    </div>

    <!-- 任务列表 -->
    <div class="panel">
      <el-table :data="taskList" v-loading="loading" border stripe style="width: 100%">
        <el-table-column prop="taskCode" label="任务编号" width="150" />
        <el-table-column prop="taskName" label="任务名称" min-width="150" />
        <el-table-column label="范围类型" width="120">
          <template #default="{ row }">{{ scopeTypeText(row.scopeType) }}</template>
        </el-table-column>
        <el-table-column label="范围描述" min-width="140">
          <template #default="{ row }">{{ scopeDescription(row) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="110">
          <template #default="{ row }">
            <el-tag
              :type="row.status === 'COMPLETED' ? 'success' : row.status === 'IN_PROGRESS' ? 'warning' : 'info'"
              effect="dark"
            >
              <el-icon class="status-icon">
                <CircleCheck v-if="row.status === 'COMPLETED'" />
                <Loading v-else />
              </el-icon>
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="盘点进度" width="180">
          <template #default="{ row }">
            <div class="progress-cell">
              <el-progress
                :percentage="progressPercent(row)"
                :status="row.status === 'COMPLETED' ? 'success' : ''"
                :stroke-width="14"
                :text-inside="true"
              />
              <span class="progress-text">{{ row.completedRecords }} / {{ row.totalRecords }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="startTime" label="开始时间" width="170" />
        <el-table-column label="结束时间" width="170">
          <template #default="{ row }">{{ row.endTime || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="360" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openRecordsDialog(row)">明细</el-button>
            <el-button
              size="small"
              type="primary"
              v-if="row.status === 'IN_PROGRESS'"
              @click="openQuickScan(row)"
            >快速盘点</el-button>
            <el-button
              size="small"
              type="success"
              v-if="row.status === 'IN_PROGRESS'"
              @click="handleComplete(row)"
            >完成</el-button>
            <el-button
              size="small"
              type="warning"
              v-if="row.status === 'COMPLETED'"
              @click="handleRestart(row)"
            >重盘</el-button>
            <el-popconfirm title="确定删除该盘点任务？将同时删除所有盘点明细。" @confirm="handleDelete(row)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无盘点任务" />
        </template>
      </el-table>

      <!-- 分页 -->
      <div class="pagination-bar">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadTasks"
          @current-change="loadTasks"
        />
      </div>
    </div>

    <!-- 新建任务弹窗 -->
    <el-dialog v-model="createDialogVisible" title="新建盘点任务" width="520px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-width="100px">
        <el-form-item label="任务名称" prop="taskName">
          <el-input v-model="createForm.taskName" placeholder="请输入任务名称" />
        </el-form-item>
        <el-form-item label="范围类型" prop="scopeType">
          <el-radio-group v-model="createForm.scopeType">
            <el-radio label="ALL">全部资产</el-radio>
            <el-radio label="DEPARTMENT">按部门</el-radio>
            <el-radio label="LOCATION">按地点</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item
          label="部门"
          prop="department"
          v-if="createForm.scopeType === 'DEPARTMENT'"
        >
          <el-select v-model="createForm.department" placeholder="请选择部门" filterable allow-create clearable style="width: 100%">
            <el-option v-for="d in departmentOptions" :key="d.id" :label="d.label" :value="d.value" />
          </el-select>
        </el-form-item>
        <el-form-item
          label="地点"
          prop="location"
          v-if="createForm.scopeType === 'LOCATION'"
        >
          <el-select v-model="createForm.location" placeholder="请选择地点" filterable allow-create clearable style="width: 100%">
            <el-option v-for="l in locationOptions" :key="l.id" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 明细弹窗 -->
    <el-dialog v-model="recordsDialogVisible" title="盘点明细" width="95%" top="3vh">
      <el-descriptions :column="3" border v-if="currentTask">
        <el-descriptions-item label="任务编号">{{ currentTask.taskCode }}</el-descriptions-item>
        <el-descriptions-item label="任务名称">{{ currentTask.taskName }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ statusText(currentTask.status) }}</el-descriptions-item>
        <el-descriptions-item label="进度">{{ currentTask.completedRecords }} / {{ currentTask.totalRecords }}</el-descriptions-item>
        <el-descriptions-item label="开始时间">{{ currentTask.startTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="结束时间">{{ currentTask.endTime || '-' }}</el-descriptions-item>
      </el-descriptions>

      <el-table :data="recordList" v-loading="recordsLoading" border style="margin-top: 16px">
        <el-table-column prop="assetCode" label="资产编号" width="150" />
        <el-table-column prop="assetName" label="资产名称" min-width="140" />
        <el-table-column prop="categoryName" label="分类" width="120" />
        <el-table-column prop="expectedLocation" label="应在地点" min-width="130" />
        <el-table-column label="实际地点" min-width="130">
          <template #default="{ row }">{{ row.actualLocation || '-' }}</template>
        </el-table-column>
        <el-table-column prop="expectedKeeper" label="应在保管人" width="110" />
        <el-table-column label="实际保管人" width="110">
          <template #default="{ row }">{{ row.actualKeeper || '-' }}</template>
        </el-table-column>
        <el-table-column label="盘点结果" width="110">
          <template #default="{ row }">
            <el-tag v-if="row.result" :type="resultTagType(row.result)">{{ resultText(row.result) }}</el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="盘点时间" width="170">
          <template #default="{ row }">{{ row.scannedAt || '-' }}</template>
        </el-table-column>
        <el-table-column label="备注" min-width="120">
          <template #default="{ row }">{{ row.remark || '-' }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button
              size="small"
              @click="openEditRecordDialog(row)"
              :disabled="currentTask?.status === 'COMPLETED'"
            >编辑</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无盘点明细" />
        </template>
      </el-table>

      <template #footer>
        <el-button size="small" type="success" :loading="exporting" @click="handleExportRecords">
          <el-icon><Download /></el-icon>导出明细
        </el-button>
        <el-button @click="recordsDialogVisible = false">关闭</el-button>
        <el-button
          type="success"
          v-if="currentTask?.status === 'IN_PROGRESS'"
          @click="handleComplete(currentTask)"
        >完成任务</el-button>
      </template>
    </el-dialog>

    <!-- 编辑明细弹窗 -->
    <el-dialog v-model="editRecordDialogVisible" title="录入盘点结果" width="520px">
      <el-form :model="recordForm" :rules="recordRules" ref="recordFormRef" label-width="100px">
        <el-form-item label="资产编号">
          <span>{{ currentRecord?.assetCode }}</span>
        </el-form-item>
        <el-form-item label="资产名称">
          <span>{{ currentRecord?.assetName }}</span>
        </el-form-item>
        <el-form-item label="应在地点">
          <span>{{ currentRecord?.expectedLocation || '-' }}</span>
        </el-form-item>
        <el-form-item label="应在保管人">
          <span>{{ currentRecord?.expectedKeeper || '-' }}</span>
        </el-form-item>
        <el-form-item label="实际地点" prop="actualLocation">
          <el-select v-model="recordForm.actualLocation" placeholder="请选择实际地点" filterable allow-create clearable style="width: 100%">
            <el-option v-for="l in locationOptions" :key="l.id" :label="l.label" :value="l.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="实际保管人" prop="actualKeeper">
          <el-select v-model="recordForm.actualKeeper" placeholder="请选择实际保管人" filterable allow-create clearable style="width: 100%">
            <el-option v-for="k in keeperOptions" :key="k.id" :label="k.label" :value="k.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="盘点结果" prop="result">
          <el-select v-model="recordForm.result" placeholder="请选择盘点结果" style="width: 100%">
            <el-option label="正常" value="NORMAL" />
            <el-option label="地点不符" value="LOCATION_MISMATCH" />
            <el-option label="保管人不符" value="KEEPER_MISMATCH" />
            <el-option label="丢失" value="LOST" />
            <el-option label="账外资产" value="EXTRA" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="recordForm.remark" type="textarea" :rows="3" placeholder="备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editRecordDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdateRecord">保存</el-button>
      </template>
    </el-dialog>
    <!-- 快速盘点弹窗 -->
    <el-dialog v-model="quickScanVisible" fullscreen :close-on-click-modal="false" @closed="stopQuickScan" class="scan-dialog">
      <template #header>
        <div style="display:flex;align-items:center;gap:12px;">
          <span style="font-size:18px;font-weight:600;">快速盘点</span>
          <el-tag v-if="quickScanTask" type="info" size="small">{{ quickScanTask.taskName }}</el-tag>
          <el-tag type="warning" size="small" style="margin-left:8px">{{ quickScanProgress }}</el-tag>
        </div>
      </template>
      <div class="qs-body">
        <div class="qs-left">
          <el-tabs v-model="scanMode" class="qs-tabs">
            <el-tab-pane label="二维码扫码" name="qr">
              <div class="qs-qr-wrap">
                <div v-if="!qrImg" class="qs-qr-empty">
                  <el-button type="primary" size="large" @click="scanQr">
                    <el-icon :size="20"><Camera /></el-icon> 点击拍照扫码
                  </el-button>
                  <p style="color:rgba(255,255,255,0.5);font-size:13px;margin-top:10px">拍照后自动识别图中的资产二维码</p>
                  <input ref="qrInp" type="file" accept="image/*" capture="environment" style="display:none" @change="onQrFile" />
                </div>
                <div v-else class="qs-qr-has">
                  <img :src="qrImg" style="max-width:100%;max-height:50vh;border-radius:6px" />
                  <div v-if="qrBusy" class="qs-err"><el-icon class="is-loading"><Loading /></el-icon> 解码中...</div>
                  <div v-else-if="qrDecoded" style="color:#18A058;font-size:16px;font-weight:700;padding:12px">识别成功: {{ qrDecoded }}</div>
                  <div v-else class="qs-err"><el-tag type="danger">未识别到二维码</el-tag><el-button size="small" style="margin-top:8px" @click="qrImg=null;qrDecoded=null">重拍</el-button></div>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="拍照 OCR" name="ocr">
              <div class="qs-ocr">
                <div v-if="!ocrImg" class="qs-ocr-empty">
                  <el-button type="primary" size="large" @click="takePhoto"><el-icon :size="18"><Camera /></el-icon>拍照</el-button>
                  <el-button size="large" style="margin-left:12px" @click="pickFile">上传</el-button>
                  <input ref="camInp" type="file" accept="image/*" capture="environment" style="display:none" @change="onCam" />
                  <input ref="fileInp" type="file" accept="image/*" style="display:none" @change="onFile" />
                </div>
                <div v-else class="qs-ocr-has">
                  <img :src="ocrImg" class="qs-ocr-img" />
                  <el-button size="small" style="margin-top:8px" @click="ocrImg=null;ocrTxt='';ocrCode=''">重拍</el-button>
                  <div v-if="ocrBusy" class="qs-ocr-wait"><el-icon class="is-loading"><Loading /></el-icon> 识别中...</div>
                  <div v-else class="qs-ocr-res">
                    <el-input v-model="ocrTxt" type="textarea" :rows="3" placeholder="识别结果..." />
                    <div style="display:flex;margin-top:8px;align-items:center">
                      <el-input v-model="ocrCode" placeholder="资产编号" style="flex:1"><template #prepend>编号</template></el-input>
                      <el-button type="primary" :disabled="!ocrCode" @click="lookupByOcr" style="margin-left:8px">查找</el-button>
                    </div>
                  </div>
                </div>
              </div>
            </el-tab-pane>
            <el-tab-pane label="手动输入" name="man">
              <div class="qs-man"><el-input v-model="manCode" placeholder="资产编号" size="large" clearable @keyup.enter="lookupMan"><template #prepend>资产编号</template></el-input><el-button type="primary" size="large" :disabled="!manCode" @click="lookupMan" style="margin-left:12px">查找</el-button></div>
            </el-tab-pane>
          </el-tabs>
        </div>
        <div class="qs-right">
          <div class="qs-card" v-if="lookupAsset">
            <div class="qs-card-hd">
              <span class="qs-card-tt">资产信息</span>
              <el-tag v-if="lookupAsset._statusTag" :type="lookupAsset._statusTagType" size="small">{{ lookupAsset._statusTag }}</el-tag>
            </div>
            <el-descriptions :column="1" size="small" border style="margin-top:8px">
              <el-descriptions-item label="资产编号">{{ lookupAsset.assetCode }}</el-descriptions-item>
              <el-descriptions-item label="资产名称">{{ lookupAsset.assetName }}</el-descriptions-item>
              <el-descriptions-item label="期望地点">{{ lookupAsset.expectedLocation || '-' }}</el-descriptions-item>
              <el-descriptions-item label="期望保管人">{{ lookupAsset.expectedKeeper || '-' }}</el-descriptions-item>
            </el-descriptions>
            <div style="margin-top:12px;display:flex;gap:8px">
              <el-button type="success" size="large" :disabled="lookupAsset._scanned" :loading="scanning" @click="doScan" style="flex:1">确认盘点</el-button>
              <el-button size="large" @click="lookupAsset=null">清除</el-button>
            </div>
          </div>
          <el-empty v-else description="扫描或输入资产编号" :image-size="60" />
          <div class="qs-log" v-if="scanLog.length">
            <div class="qs-log-hd"><span>扫描记录 ({{ scanLog.length }})</span><el-button link type="primary" size="small" @click="scanLog=[];scanned.clear()">清空</el-button></div>
            <div class="qs-log-list"><div v-for="(s,i) in scanLog" :key="i" class="qs-log-it"><div class="qs-log-top"><span class="qs-log-code">{{ s.code }}</span><el-tag :type="s.ok?'success':'danger'" size="small">{{ s.ok?'已确认':'未找到' }}</el-tag></div><div class="qs-log-nm">{{ s.name||'-' }}</div></div></div>
          </div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import {
  getInventoryTaskPage,
  createInventoryTask,
  getInventoryRecords,
  updateInventoryRecord,
  completeInventoryTask,
  deleteInventoryTask,
  restartInventoryTask,
  lookupInventoryRecord,
  scanInventoryRecord,
  performOcr,
  type InventoryTaskItem,
  type InventoryRecordItem
} from '@/api/inventory'
import { Download, CircleCheck, Loading, Plus, Camera } from '@element-plus/icons-vue'
import { exportInventoryTasks, exportInventoryTaskRecords } from '@/api/export'
import { useMasterDataOptions } from '@/composables/useMasterDataOptions'
import jsqr from 'jsqr'

const { departmentOptions, locationOptions, keeperOptions, loadAll: loadMasterData } = useMasterDataOptions()

// ===== 任务列表 =====
const loading = ref(false)
const exporting = ref(false)
const taskList = ref<InventoryTaskItem[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const filterStatus = ref('')

async function loadTasks() {
  loading.value = true
  try {
    const params: Record<string, any> = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (filterStatus.value) params.status = filterStatus.value
    const res = await getInventoryTaskPage(params)
    taskList.value = res.data.records
    total.value = res.data.total
  } catch (e: any) {
    ElMessage.error(e?.message || '加载任务列表失败')
  } finally {
    loading.value = false
  }
}

async function handleExportTasks() {
  exporting.value = true
  try {
    await exportInventoryTasks()
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

async function handleExportRecords() {
  if (!currentTask.value) return
  exporting.value = true
  try {
    await exportInventoryTaskRecords(currentTask.value.id)
    ElMessage.success('导出成功')
  } finally {
    exporting.value = false
  }
}

// ===== 新建任务 =====
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = reactive({
  taskName: '',
  scopeType: 'ALL',
  department: '',
  location: ''
})
const createRules = {
  taskName: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  scopeType: [{ required: true, message: '请选择范围类型', trigger: 'change' }],
  department: [{ required: true, message: '请输入部门名称', trigger: 'blur' }],
  location: [{ required: true, message: '请输入地点', trigger: 'blur' }]
}

function openCreateDialog() {
  createForm.taskName = ''
  createForm.scopeType = 'ALL'
  createForm.department = ''
  createForm.location = ''
  createDialogVisible.value = true
}

async function handleCreate() {
  if (!createFormRef.value) return
  await createFormRef.value.validate(async (valid) => {
    if (!valid) return
    creating.value = true
    try {
      const payload: any = {
        taskName: createForm.taskName,
        scopeType: createForm.scopeType
      }
      if (createForm.scopeType === 'DEPARTMENT') payload.department = createForm.department
      if (createForm.scopeType === 'LOCATION') payload.location = createForm.location
      await createInventoryTask(payload)
      ElMessage.success('盘点任务创建成功')
      createDialogVisible.value = false
      loadTasks()
    } catch (e: any) {
      ElMessage.error(e?.message || '创建任务失败')
    } finally {
      creating.value = false
    }
  })
}

// ===== 明细弹窗 =====
const recordsDialogVisible = ref(false)
const recordsLoading = ref(false)
const currentTask = ref<InventoryTaskItem | null>(null)
const recordList = ref<InventoryRecordItem[]>([])

async function openRecordsDialog(task: InventoryTaskItem) {
  currentTask.value = task
  recordsDialogVisible.value = true
  recordsLoading.value = true
  try {
    const res = await getInventoryRecords(task.id)
    recordList.value = res.data
  } catch (e: any) {
    ElMessage.error(e?.message || '加载盘点明细失败')
  } finally {
    recordsLoading.value = false
  }
}

// ===== 编辑明细 =====
const editRecordDialogVisible = ref(false)
const updating = ref(false)
const recordFormRef = ref<FormInstance>()
const currentRecord = ref<InventoryRecordItem | null>(null)
const recordForm = reactive({
  actualLocation: '',
  actualKeeper: '',
  result: '',
  remark: ''
})
const recordRules = {
  result: [{ required: true, message: '请选择盘点结果', trigger: 'change' }]
}

function openEditRecordDialog(record: InventoryRecordItem) {
  currentRecord.value = record
  recordForm.actualLocation = record.actualLocation || ''
  recordForm.actualKeeper = record.actualKeeper || ''
  recordForm.result = record.result || ''
  recordForm.remark = record.remark || ''
  editRecordDialogVisible.value = true
}

async function handleUpdateRecord() {
  if (!recordFormRef.value || !currentRecord.value) return
  await recordFormRef.value.validate(async (valid) => {
    if (!valid) return
    updating.value = true
    try {
      await updateInventoryRecord(currentRecord.value!.id, {
        actualLocation: recordForm.actualLocation,
        actualKeeper: recordForm.actualKeeper,
        result: recordForm.result,
        remark: recordForm.remark
      })
      ElMessage.success('盘点结果保存成功')
      editRecordDialogVisible.value = false
      // 刷新明细
      if (currentTask.value) {
        const res = await getInventoryRecords(currentTask.value.id)
        recordList.value = res.data
        // 刷新任务列表中的进度
        await loadTasks()
      }
    } catch (e: any) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      updating.value = false
    }
  })
}

// ===== 完成任务 =====
async function handleComplete(task: InventoryTaskItem) {
  try {
    await ElMessageBox.confirm(
      `确认完成盘点任务"${task.taskName}"？完成后将无法再修改明细。`,
      '确认完成',
      { type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await completeInventoryTask(task.id)
    ElMessage.success('盘点任务已完成')
    // 刷新任务列表
    await loadTasks()
    // 刷新当前任务详情
    if (recordsDialogVisible.value && currentTask.value) {
      // 重新加载明细弹窗中的任务状态
      currentTask.value = taskList.value.find(t => t.id === task.id) || currentTask.value
    }
  } catch (e: any) {
    ElMessage.error(e?.message || '完成失败')
  }
}

// ===== 工具函数 =====
function scopeTypeText(type: string): string {
  const map: Record<string, string> = {
    ALL: '全部资产',
    DEPARTMENT: '按部门',
    LOCATION: '按地点'
  }
  return map[type] || type || '-'
}

function scopeDescription(row: InventoryTaskItem): string {
  if (row.scopeType === 'DEPARTMENT') return row.department || '-'
  if (row.scopeType === 'LOCATION') return row.location || '-'
  return '全部资产'
}

function statusText(status: string): string {
  const map: Record<string, string> = {
    IN_PROGRESS: '进行中',
    COMPLETED: '已完成'
  }
  return map[status] || status || '-'
}

function progressPercent(row: InventoryTaskItem): number {
  if (!row.totalRecords || row.totalRecords === 0) return 0
  return Math.round((row.completedRecords / row.totalRecords) * 100)
}

function resultText(result: string): string {
  const map: Record<string, string> = {
    NORMAL: '正常',
    LOCATION_MISMATCH: '地点不符',
    KEEPER_MISMATCH: '保管人不符',
    LOST: '丢失',
    EXTRA: '账外资产'
  }
  return map[result] || result
}

function resultTagType(result: string): string {
  const map: Record<string, string> = {
    NORMAL: 'success',
    LOCATION_MISMATCH: 'warning',
    KEEPER_MISMATCH: 'warning',
    LOST: 'danger',
    EXTRA: 'info'
  }
  return map[result] || 'info'
}

// ===== 删除任务 =====
async function handleDelete(task: InventoryTaskItem) {
  try {
    await ElMessageBox.confirm(
      `确定删除任务"${task.taskName}"吗？将同时删除所有盘点明细，不可恢复！`,
      '确认删除',
      { type: 'error', confirmButtonText: '删除', cancelButtonText: '取消' }
    )
  } catch { return }
  try {
    await deleteInventoryTask(task.id)
    ElMessage.success('任务已删除')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// ===== 重盘任务 =====
async function handleRestart(task: InventoryTaskItem) {
  try {
    await ElMessageBox.confirm(
      `确定重新盘点"${task.taskName}"吗？所有已盘点的明细将被重置为"待盘点"状态。`,
      '确认重盘',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await restartInventoryTask(task.id)
    ElMessage.success('任务已重置，可以重新盘点')
    await loadTasks()
  } catch (e: any) {
    ElMessage.error(e?.message || '重盘失败')
  }
}

// ===== 快速盘点 =====
const quickScanVisible = ref(false)
const quickScanTask = ref<InventoryTaskItem | null>(null)
const scanMode = ref('qr')
const quickScanProgress = computed(() => {
  if (!quickScanTask.value) return ''
  return `${quickScanTask.value.completedRecords}/${quickScanTask.value.totalRecords}`
})
const lookupAsset = ref<any>(null)
const scanning = ref(false)
const scanLog = ref<{code:string;name:string;ok:boolean}[]>([])
const scanned = ref<Set<string>>(new Set())

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
const fileInp = ref<HTMLInputElement|null>(null)

async function openQuickScan(task: InventoryTaskItem) {
  quickScanTask.value = task
  quickScanVisible.value = true
}

function stopQuickScan() {
  lookupAsset.value = null
  qrImg.value = null; qrDecoded.value = null
  ocrImg.value = null; ocrTxt.value = ''; ocrCode.value = ''; ocrBusy.value = false
}

function scanQr() { qrInp.value?.click() }

async function onQrFile(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  if (!f) return
  if (qrImg.value && qrImg.value.startsWith('blob:')) { URL.revokeObjectURL(qrImg.value) }
  qrImg.value = URL.createObjectURL(f); qrBusy.value = true
  input.value = ''
  try {
    const img = await loadImage(f)
    const result = decodeQrFromImage(img)
    if (result) {
      qrDecoded.value = result
      await doLookup(result.trim())
    } else {
      qrDecoded.value = null
      ElMessage.warning('未识别到二维码')
    }
  } catch { qrDecoded.value = null; ElMessage.error('解码失败') } finally { qrBusy.value = false }
}

async function doLookup(code: string) {
  if (!code || !quickScanTask.value) return
  try {
    const r = await lookupInventoryRecord(quickScanTask.value.id, code)
    if (!r.data || r.data.result === 'NOT_IN_SCOPE') {
      scanLog.value.unshift({ code, name: r.data?.assetName||'', ok: false })
      if (scanLog.value.length > 20) scanLog.value.pop()
      ElMessage.error('未在盘点范围内')
      return
    }
    const asset = { ...r.data, _scanned: r.data.scanned }
    if (asset._scanned) {
      const map: Record<string,string> = { NORMAL: '已盘点 - 正常', LOCATION_MISMATCH: '已盘点 - 地点不符', KEEPER_MISMATCH: '已盘点 - 保管人不符', MISMATCH: '已盘点 - 多项不符' }
      const tagTypes: Record<string,string> = { NORMAL: 'success', LOCATION_MISMATCH: 'warning', KEEPER_MISMATCH: 'warning', MISMATCH: 'danger' }
      asset._statusTag = map[r.data.result] || '已盘点'
      asset._statusTagType = tagTypes[r.data.result] || 'info'
    } else {
      asset._statusTag = '待盘点'
      asset._statusTagType = 'info'
    }
    lookupAsset.value = asset
  } catch {
    scanLog.value.unshift({ code, name: '', ok: false }); if (scanLog.value.length > 20) scanLog.value.pop()
  }
}

async function doScan() {
  if (!lookupAsset.value || !quickScanTask.value) return
  const code = lookupAsset.value.assetCode
  if (scanned.value.has(code)) { ElMessage.warning('已扫描过'); return }
  scanning.value = true
  try {
    await scanInventoryRecord({ recordId: lookupAsset.value.recordId, actualLocation: '', actualKeeper: '', result: 'NORMAL' })
    scanned.value.add(code)
    scanLog.value.unshift({ code, name: lookupAsset.value.assetName, ok: true })
    if (scanLog.value.length > 20) scanLog.value.pop()
    ElMessage.success('已确认: ' + code)
    if (quickScanTask.value) {
      quickScanTask.value.completedRecords = (quickScanTask.value.completedRecords||0) + 1
    }
    lookupAsset.value = null
    loadTasks()
  } catch {} finally { scanning.value = false }
}

function takePhoto() { camInp.value?.click() }
function pickFile() { fileInp.value?.click() }

async function onCam(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (f) await procImg(f)
}
async function onFile(e: Event) {
  const input = e.target as HTMLInputElement
  const f = input.files?.[0]
  input.value = ''
  if (f) await procImg(f)
}

async function procImg(file: File) {
  if (ocrImg.value && ocrImg.value.startsWith('blob:')) { URL.revokeObjectURL(ocrImg.value) }
  ocrImg.value = URL.createObjectURL(file); ocrBusy.value = true; ocrTxt.value = ''

  try {
    const img = await loadImage(file)

    const qrResult = decodeQrFromImage(img)
    if (qrResult) {
      ocrTxt.value = '[二维码识别] ' + qrResult
      const m = qrResult.match(/(?:FA|ZC)[A-Z0-9\-]+/i)
      ocrCode.value = m ? m[0].toUpperCase() : qrResult
      ocrBusy.value = false
      ElMessage.success('图片中识别到二维码: ' + qrResult)
      return
    }

    const fd = new FormData(); fd.append('image', file)
    const r = await performOcr(fd); ocrTxt.value = r.data?.text||''
    const m = ocrTxt.value.match(/(?:FA|ZC)[A-Z0-9\-]+/i)
    ocrCode.value = m ? m[0].toUpperCase() : ''
    if (!ocrCode.value) ElMessage.warning('未提取到资产编号，请手动输入')
  } catch { ElMessage.error('识别失败') }
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
    const maxSize = 800
    let w = img.naturalWidth, h = img.naturalHeight
    const scale = Math.min(1, maxSize / Math.max(w, h))
    w *= scale; h *= scale
    canvas.width = w; canvas.height = h
    const ctx = canvas.getContext('2d')
    if (!ctx) return null
    ctx.drawImage(img, 0, 0, w, h)
    const imageData = ctx.getImageData(0, 0, w, h)
    const result = jsqr(imageData.data, w, h)
    return result ? result.data : null
  } catch { return null }
}

function lookupByOcr() { if (ocrCode.value) doLookup(ocrCode.value.trim()) }
function lookupMan() { if (manCode.value) { doLookup(manCode.value.trim()); manCode.value = '' } }

// 初始化加载
loadTasks()
loadMasterData()
</script>

<style scoped>
.filter-bar {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  margin-bottom: var(--space-base);
}
.filter-label {
  font-size: 14px;
  color: var(--color-text-secondary);
}
.panel {
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  padding: var(--space-base);
}
.pagination-bar {
  margin-top: var(--space-base);
  display: flex;
  justify-content: flex-end;
}
.status-icon {
  margin-right: 4px;
  vertical-align: -2px;
}
.progress-cell {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.progress-cell .el-progress {
  flex: 1;
}
.progress-text {
  font-size: 11px;
  color: var(--color-text-secondary);
  text-align: right;
}

/* 快速盘点弹窗 */
.scan-dialog :deep(.el-dialog__body) { padding: 8px 16px; height: calc(100vh - 120px); overflow: hidden; }
.qs-body { display: flex; height: 100%; gap: 16px; }
.qs-left { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.qs-tabs { flex: 1; display: flex; flex-direction: column; }
.qs-tabs :deep(.el-tabs__content) { flex: 1; overflow: hidden; }
.qs-tabs :deep(.el-tab-pane) { height: 100%; }
.qs-qr-wrap { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; background: #0d1117; border-radius: 8px; overflow: hidden; min-height: 350px; }
.qs-qr-empty { text-align: center; display: flex; flex-direction: column; align-items: center; }
.qs-qr-has { display: flex; flex-direction: column; align-items: center; padding: 16px; }
.qs-err, .qs-load { text-align: center; padding: 40px; color: #999; }
.qs-ocr { flex: 1; padding: 20px; display: flex; flex-direction: column; align-items: center; }
.qs-ocr-empty { margin-top: 60px; }
.qs-ocr-has { display: flex; flex-direction: column; align-items: center; }
.qs-ocr-img { max-width: 100%; max-height: 280px; border-radius: 6px; }
.qs-ocr-wait { color: #999; margin: 12px 0; }
.qs-ocr-res { width: 100%; max-width: 400px; margin-top: 8px; }
.qs-man { display: flex; align-items: center; justify-content: center; padding: 60px 20px; }
.qs-right { width: 310px; display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.qs-card { background: #fff; border: 1px solid var(--color-border); border-radius: 6px; padding: 14px; }
.qs-card-hd { display: flex; align-items: center; justify-content: space-between; }
.qs-card-tt { font-size: 15px; font-weight: 600; }
.qs-log { background: #fff; border: 1px solid var(--color-border); border-radius: 6px; padding: 10px; }
.qs-log-hd { display: flex; align-items: center; justify-content: space-between; font-size: 13px; font-weight: 500; margin-bottom: 6px; }
.qs-log-list { display: flex; flex-direction: column; gap: 4px; }
.qs-log-it { padding: 6px 8px; border-radius: 4px; border: 1px solid var(--color-border); }
.qs-log-top { display: flex; justify-content: space-between; align-items: center; }
.qs-log-code { font-size: 12px; font-weight: 600; font-family: monospace; }
.qs-log-nm { font-size: 11px; color: #999; margin-top: 2px; }

@media (max-width: 768px) {
  .panel {
    padding: 8px;
    overflow-x: auto;
    -webkit-overflow-scrolling: touch;
  }
  .panel :deep(.el-table) {
    min-width: 750px;
  }
  .filter-bar {
    flex-wrap: wrap;
    gap: 6px;
  }
  .pagination-bar {
    justify-content: center;
  }
  .scan-dialog :deep(.el-dialog__body) {
    padding: 4px 8px;
    height: auto;
    overflow: auto;
  }
  .qs-body {
    flex-direction: column;
  }
  .qs-left {
    height: 350px;
    flex-shrink: 0;
  }
  .qs-right {
    width: 100%;
  }
  .qs-man {
    padding: 30px 10px;
    flex-wrap: wrap;
  }
}
</style>

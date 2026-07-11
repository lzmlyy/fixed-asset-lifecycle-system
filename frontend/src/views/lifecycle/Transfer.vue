<template>
  <div>
    <PageHeader title="调拨管理" description="管理资产调拨流程，记录调出部门、调入部门、调入保管人和调拨日期等信息。" />

    <div class="search-form">
      <el-form :model="query" :inline="true" size="default" label-width="80">
        <el-form-item label="单据编号"><el-input v-model="query.orderCode" placeholder="单据编号" clearable style="width:150px" /></el-form-item>
        <el-form-item label="单据状态">
          <el-select v-model="query.status" placeholder="选择状态" clearable style="width:140px">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="审批中" value="APPROVING" />
            <el-option label="已完成" value="COMPLETED" />
            <el-option label="已驳回" value="REJECTED" />
            <el-option label="已取消" value="CANCELLED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="search">查询</el-button>
          <el-button @click="resetQuery">重置</el-button>
          <el-button type="primary" @click="openCreate">新增调拨</el-button>
        </el-form-item>
      </el-form>
    </div>

    <div class="table-wrapper">
      <el-table :data="tableData" border stripe v-loading="loading" style="width:100%">
        <el-table-column prop="orderCode" label="单据编号" width="160" />
        <el-table-column prop="assetCode" label="资产编号" width="140" />
        <el-table-column prop="assetName" label="资产名称" min-width="120" />
        <el-table-column prop="fromDepartment" label="调出部门" width="100" />
        <el-table-column prop="toDepartment" label="调入部门" width="100" />
        <el-table-column prop="toLocation" label="存放地点" width="110" show-overflow-tooltip />
        <el-table-column prop="toKeeper" label="调入保管人" width="90" />
        <el-table-column prop="transferDate" label="调拨日期" width="100" />
        <el-table-column prop="status" label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="row.status === 'COMPLETED' ? 'success' : row.status === 'CANCELLED' ? 'danger' : row.status === 'REJECTED' ? 'danger' : row.status === 'APPROVING' ? 'warning' : 'info'" size="small">{{ row.status === 'DRAFT' ? '草稿' : row.status === 'APPROVING' ? '审批中' : row.status === 'COMPLETED' ? '已完成' : row.status === 'REJECTED' ? '已驳回' : '已取消' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="160" />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="viewDetail(row)">查看</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="success" size="small" @click="openEdit(row)">编辑</el-button>
            <el-button v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" link type="warning" size="small" @click="submitForApproval(row)">提交审批</el-button>
            <el-popconfirm v-if="row.status === 'DRAFT' || row.status === 'REJECTED'" title="确定删除该调拨申请吗？" @confirm="handleDelete(row)"><template #reference><el-button link type="danger" size="small">删除</el-button></template></el-popconfirm>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchData"
        @size-change="fetchData"
      />
    </div>

    <el-dialog v-model="createVisible" :title="editId ? '编辑调拨' : '新增调拨'" width="560px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="90" size="default">
        <el-form-item label="选择资产" prop="assetId">
          <AssetSelect v-model="form.assetId" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="调入部门" prop="toDepartment">
              <el-select v-model="form.toDepartment" placeholder="选择调入部门" filterable allow-create clearable style="width:100%">
                <el-option v-for="d in departmentOptions" :key="d.id" :label="d.label" :value="d.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="存放地点">
              <el-select v-model="form.toLocation" placeholder="选择存放地点" filterable allow-create clearable style="width:100%">
                <el-option v-for="l in locationOptions" :key="l.id" :label="l.label" :value="l.value" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="调入保管人" prop="toKeeper">
              <el-select v-model="form.toKeeper" placeholder="选择调入保管人" filterable allow-create clearable style="width:100%">
                <el-option v-for="k in keeperOptions" :key="k.id" :label="k.label" :value="k.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="调拨日期" prop="transferDate">
              <el-date-picker v-model="form.transferDate" type="date" placeholder="选择日期" value-format="YYYY-MM-DD" style="width:100%" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="备注"><el-input v-model="form.remark" placeholder="备注" /></el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitCreate">确认</el-button>
      </template>
    </el-dialog>

    <LifecycleDetailDialog v-model:visible="detailVisible" :data="detailData" title="调拨详情" business-type="TRANSFER" />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import PageHeader from '@/components/PageHeader.vue'
import AssetSelect from '@/components/AssetSelect.vue'
import LifecycleDetailDialog from '@/components/LifecycleDetailDialog.vue'
import { getTransferPage, getTransferDetail, createTransfer, updateTransfer, deleteTransfer } from '@/api/lifecycle'
import { submitApproval } from '@/api/approval'
import { useMasterDataOptions } from '@/composables/useMasterDataOptions'

const { departmentOptions, locationOptions, keeperOptions, loadAll: loadMasterData } = useMasterDataOptions()

const loading = ref(false)
const submitLoading = ref(false)
const tableData = ref<any[]>([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const formRef = ref()
const createVisible = ref(false)
const editId = ref<number | null>(null)
const detailVisible = ref(false)
const detailData = ref<any>(null)

const query = reactive({
  orderCode: '',
  status: ''
})

const form = reactive<any>({
  assetId: undefined,
  toDepartment: '',
  toLocation: '',
  toKeeper: '',
  transferDate: '',
  remark: ''
})

const formRules = {
  assetId: [{ required: true, message: '请选择资产', trigger: 'change' }],
  toDepartment: [{ required: true, message: '请输入调入部门', trigger: 'blur' }],
  toKeeper: [{ required: true, message: '请输入调入保管人', trigger: 'blur' }],
  transferDate: [{ required: true, message: '请选择调拨日期', trigger: 'change' }]
}

async function fetchData() {
  loading.value = true
  try {
    const params = { ...query, pageNum: pageNum.value, pageSize: pageSize.value }
    const r = await getTransferPage(params)
    if (r.code === 200) {
      tableData.value = r.data.records
      total.value = r.data.total
    }
  } catch {} finally {
    loading.value = false
  }
}

function search() { pageNum.value = 1; fetchData() }

function resetQuery() {
  Object.assign(query, { orderCode: '', status: '' })
  pageNum.value = 1
  fetchData()
}

function openCreate() {
  editId.value = null
  Object.assign(form, { assetId: undefined, toDepartment: '', toLocation: '', toKeeper: '', transferDate: '', remark: '' })
  createVisible.value = true
}

async function openEdit(row: any) {
  try {
    const r = await getTransferDetail(row.id)
    if (r.code === 200) {
      editId.value = row.id
      const d = r.data
      Object.assign(form, {
        assetId: d.assetId,
        toDepartment: d.toDepartment,
        toLocation: d.toLocation,
        toKeeper: d.toKeeper,
        transferDate: d.transferDate,
        remark: d.remark || ''
      })
      createVisible.value = true
    }
  } catch {}
}

async function submitCreate() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitLoading.value = true
  try {
    if (editId.value) {
      await updateTransfer(editId.value, form)
      ElMessage.success('调拨申请已更新')
    } else {
      await createTransfer(form)
      ElMessage.success('调拨申请已保存为草稿')
    }
    createVisible.value = false
    editId.value = null
    fetchData()
  } catch {} finally {
    submitLoading.value = false
  }
}

async function submitForApproval(row: any) {
  try {
    await submitApproval({ businessType: 'TRANSFER', businessId: row.id })
    ElMessage.success('已提交审批')
    fetchData()
  } catch {}
}

async function handleDelete(row: any) {
  try {
    await deleteTransfer(row.id)
    ElMessage.success('已删除')
    fetchData()
  } catch {}
}

async function viewDetail(row: any) {
  try {
    const r = await getTransferDetail(row.id)
    if (r.code === 200) {
      detailData.value = r.data
      detailVisible.value = true
    }
  } catch {}
}

onMounted(() => { fetchData(); loadMasterData() })
</script>

<style scoped>
.search-form {
  background: #fff;
  padding: 16px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
  margin-bottom: 12px;
}
.table-wrapper {
  background: #fff;
  padding: 12px;
  border-radius: 6px;
  border: 1px solid var(--color-border);
}
</style>

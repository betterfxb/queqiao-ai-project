<template>
  <div class="pg">
    <header class="pg-head">
      <div>
        <h2 class="pg-title">服务产品（SKU）</h2>
        <p class="pg-sub">把服务变成可定价、可下单的产品；下单时选择套餐，财务报表可按 SKU 统计营收。</p>
      </div>
      <el-button type="primary" :disabled="!canWrite" @click="openCreate">+ 新建产品</el-button>
    </header>

    <el-table :data="list" v-loading="loading" class="pg-table" stripe>
      <el-table-column prop="name" label="产品名称" min-width="180" />
      <el-table-column prop="category" label="分类" width="120">
        <template #default="{ row }"><el-tag size="small" effect="plain">{{ row.category }}</el-tag></template>
      </el-table-column>
      <el-table-column label="价格" width="120" align="right">
        <template #default="{ row }"><span class="pg-price">¥{{ row.price }}</span></template>
      </el-table-column>
      <el-table-column label="有效期" width="110" align="center">
        <template #default="{ row }">{{ row.durationDays && row.durationDays > 0 ? row.durationDays + ' 天' : '不限期' }}</template>
      </el-table-column>
      <el-table-column prop="benefits" label="权益说明" min-width="220" show-overflow-tooltip />
      <el-table-column label="状态" width="100" align="center">
        <template #default="{ row }">
          <el-tag :type="row.status === '在售' ? 'success' : 'info'" size="small">{{ row.status || '在售' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" align="center" fixed="right">
        <template #default="{ row }">
          <el-button size="small" type="primary" link :disabled="!canWrite" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" link :disabled="!canWrite" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑产品' : '新建产品'" width="520px">
      <el-form :model="form" label-width="92px" @submit.prevent>
        <el-form-item label="产品名称" required>
          <el-input v-model="form.name" placeholder="如：年度尊享会员卡" />
        </el-form-item>
        <el-form-item label="分类" required>
          <el-select v-model="form.category" placeholder="选择分类" style="width:100%">
            <el-option v-for="c in PRODUCT_CATEGORIES" :key="c" :label="c" :value="c" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格" required>
          <el-input-number v-model="form.price" :min="0" :step="100" style="width:100%" />
        </el-form-item>
        <el-form-item label="有效期(天)">
          <el-input-number v-model="form.durationDays" :min="0" :step="30" style="width:100%" />
          <span class="pg-hint">0 = 不限期</span>
        </el-form-item>
        <el-form-item label="权益说明">
          <el-input v-model="form.benefits" type="textarea" :rows="3" placeholder="产品包含的服务内容" />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="form.status">
            <el-radio value="在售">在售</el-radio>
            <el-radio value="下架">下架</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialog = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  fetchProducts, createProduct, updateProduct, deleteProduct,
  PRODUCT_CATEGORIES, type ServiceProductItem,
} from '@/api/modules/products'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const canWrite = computed(() => auth.isAdmin)

const list = ref<ServiceProductItem[]>([])
const loading = ref(false)
const dialog = ref(false)
const saving = ref(false)

const blank = () => ({ name: '', category: '', price: 0, durationDays: 0, benefits: '', status: '在售' })
const form = reactive<Partial<ServiceProductItem> & { id?: string }>(blank())

const load = async () => {
  loading.value = true
  try {
    list.value = await fetchProducts()
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  Object.assign(form, blank())
  dialog.value = true
}
const openEdit = (row: ServiceProductItem) => {
  Object.assign(form, row)
  dialog.value = true
}
const save = async () => {
  if (!form.name || !form.category || !(form.price! > 0)) {
    ElMessage.warning('请填写名称、分类，且价格大于 0')
    return
  }
  saving.value = true
  try {
    if (form.id) {
      await updateProduct(form.id, form)
      ElMessage.success('已保存')
    } else {
      await createProduct(form as any)
      ElMessage.success('已创建')
    }
    dialog.value = false
    await load()
  } finally {
    saving.value = false
  }
}
const remove = async (row: ServiceProductItem) => {
  await ElMessageBox.confirm('确认删除该产品？', '提示', { type: 'warning' }).catch(() => Promise.reject())
  await deleteProduct(row.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(load)
</script>

<style scoped>
.pg { padding: 20px 24px; }
.pg-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 16px; }
.pg-title { font-size: 1.125rem; font-weight: 600; color: var(--dt-ink-1); margin: 0; }
.pg-sub { margin: 6px 0 0; color: var(--dt-ink-3); font-size: 0.8125rem; }
.pg-table { border-radius: var(--dt-radius-lg); overflow: hidden; }
.pg-price { color: var(--dt-primary); font-weight: 600; }
.pg-hint { margin-left: 8px; color: var(--dt-ink-3); font-size: 0.75rem; }
</style>

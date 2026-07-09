<template>
  <section class="admin-banners">
    <h2>横幅管理</h2>

    <div class="toolbar">
      <el-input v-model="filters.keyword" placeholder="标题/副标题" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="草稿" value="DRAFT" />
        <el-option label="上架" value="ACTIVE" />
        <el-option label="下架" value="INACTIVE" />
      </el-select>
      <el-button type="primary" @click="loadBanners">查询</el-button>
      <el-button type="success" data-testid="admin-banner-create" @click="openCreate">新增横幅</el-button>
    </div>

    <el-table :data="banners" data-testid="admin-banners-table">
      <el-table-column prop="title" label="标题" min-width="150" />
      <el-table-column prop="status" label="状态">
        <template slot-scope="{ row }">
          <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortOrder" label="排序" width="90" />
      <el-table-column prop="targetType" label="跳转类型" width="110" />
      <el-table-column prop="targetUrl" label="跳转地址" min-width="160" />
      <el-table-column label="时间窗口" min-width="180">
        <template slot-scope="{ row }">{{ formatWindow(row) }}</template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template slot-scope="{ row }">
          <el-button size="mini" @click="openEdit(row)">编辑</el-button>
          <el-button size="mini" type="success" :disabled="row.status === 'ACTIVE'" @click="changeStatus(row, 'ACTIVE')">上架</el-button>
          <el-button size="mini" :disabled="row.status === 'INACTIVE'" @click="changeStatus(row, 'INACTIVE')">下架</el-button>
          <el-button size="mini" @click="move(row, -1)">上移</el-button>
          <el-button size="mini" @click="move(row, 1)">下移</el-button>
          <el-button size="mini" type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      layout="total, prev, pager, next"
      :page-size="page.size"
      :current-page.sync="page.page"
      :total="total"
      @current-change="loadBanners" />

    <el-dialog :title="dialogTitle" :visible.sync="dialogVisible" width="640px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="form.subtitle" /></el-form-item>
        <el-form-item label="图片地址"><el-input v-model="form.imageUrl" /></el-form-item>
        <el-form-item label="跳转类型">
          <el-select v-model="form.targetType" clearable placeholder="可选">
            <el-option label="商品" value="GOODS" />
            <el-option label="服务" value="SERVICE" />
            <el-option label="领养" value="ADOPTION" />
            <el-option label="社区" value="COMMUNITY" />
            <el-option label="外链" value="EXTERNAL" />
          </el-select>
        </el-form-item>
        <el-form-item label="跳转地址"><el-input v-model="form.targetUrl" placeholder="/goods 或 https://..." /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="草稿" value="DRAFT" />
            <el-option label="上架" value="ACTIVE" />
            <el-option label="下架" value="INACTIVE" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序"><el-input v-model.number="form.sortOrder" /></el-form-item>
        <el-form-item label="开始时间"><el-date-picker v-model="form.startsAt" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" placeholder="可选" /></el-form-item>
        <el-form-item label="结束时间"><el-date-picker v-model="form.endsAt" type="datetime" value-format="yyyy-MM-dd'T'HH:mm:ss.SSS'Z'" placeholder="可选" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" data-testid="admin-banner-save" @click="save">保存</el-button>
      </span>
    </el-dialog>
  </section>
</template>

<script>
import { createBanner, deleteBanner, listAdminBanners, updateBanner, updateBannerOrder, updateBannerStatus } from '@/api/banner'

const emptyForm = () => ({
  id: null,
  title: '',
  subtitle: '',
  imageUrl: '',
  targetType: '',
  targetUrl: '',
  status: 'DRAFT',
  sortOrder: 0,
  startsAt: null,
  endsAt: null,
  version: 0
})

export default {
  name: 'AdminBannersView',
  data() {
    return {
      banners: [],
      total: 0,
      loading: false,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 10 },
      dialogVisible: false,
      form: emptyForm()
    }
  },
  computed: {
    dialogTitle() {
      return this.form.id ? '编辑横幅' : '新增横幅'
    }
  },
  created() {
    this.loadBanners()
  },
  methods: {
    async loadBanners() {
      this.loading = true
      try {
        const response = await listAdminBanners({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.banners = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    openCreate() {
      this.form = emptyForm()
      this.dialogVisible = true
    },
    openEdit(row) {
      this.form = { ...row }
      this.dialogVisible = true
    },
    async save() {
      try {
        const payload = {
          title: this.form.title,
          subtitle: this.form.subtitle || null,
          imageUrl: this.form.imageUrl,
          targetType: this.form.targetType || null,
          targetUrl: this.form.targetUrl || null,
          status: this.form.status,
          sortOrder: Number(this.form.sortOrder) || 0,
          startsAt: this.form.startsAt || null,
          endsAt: this.form.endsAt || null,
          version: this.form.version || 0
        }
        const response = this.form.id
          ? await updateBanner(this.form.id, payload)
          : await createBanner(payload)
        const saved = response.data
        const index = this.banners.findIndex(item => item.id === saved.id)
        if (index >= 0) this.$set(this.banners, index, saved)
        else this.banners.unshift(saved)
        this.dialogVisible = false
        this.$message && this.$message.success('横幅已保存')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async changeStatus(row, status) {
      try {
        const response = await updateBannerStatus(row.id, { status, version: row.version })
        Object.assign(row, response.data)
        this.$message && this.$message.success('状态已更新')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async move(row, delta) {
      try {
        const response = await updateBannerOrder(row.id, { sortOrder: row.sortOrder + delta, version: row.version })
        Object.assign(row, response.data)
        this.banners.sort((a, b) => a.sortOrder - b.sortOrder)
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async remove(row) {
      try {
        await deleteBanner(row.id, row.version)
        this.banners = this.banners.filter(item => item.id !== row.id)
        this.total = Math.max(0, this.total - 1)
        this.$message && this.$message.success('横幅已删除')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    statusLabel(status) {
      return { DRAFT: '草稿', ACTIVE: '上架', INACTIVE: '下架' }[status] || status
    },
    statusTagType(status) {
      if (status === 'ACTIVE') return 'success'
      if (status === 'INACTIVE') return 'info'
      return 'warning'
    },
    formatWindow(row) {
      const start = this.formatTime(row.startsAt) || '立即'
      const end = this.formatTime(row.endsAt) || '长期'
      return `${start} ~ ${end}`
    },
    formatTime(value) {
      if (!value) return ''
      const date = typeof value === 'string' ? new Date(value) : value
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.admin-banners { max-width: 1180px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; align-items: center; }
.toolbar .el-input { max-width: 240px; }
.el-pagination { margin-top: 16px; text-align: right; }
</style>

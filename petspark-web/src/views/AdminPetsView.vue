<template>
  <section class="admin-console-page admin-pets">
    <AdminPageHeader
      eyebrow="内容与档案"
      title="宠物管理"
      description="查看全平台宠物档案及其公开、领养状态。"
    >
      <template #actions>
        <el-button :loading="loading" @click="load">刷新数据</el-button>
      </template>
    </AdminPageHeader>

    <AdminTableShell title="宠物档案" :total="total">
      <template #filters>
        <el-input
          v-model.trim="filters.keyword"
          clearable
          placeholder="搜索宠物名称"
          @keyup.enter.native="search"
        />
        <el-select v-model="filters.publicStatus" clearable placeholder="公开状态">
          <el-option label="已公开" value="PUBLISHED" />
          <el-option label="未公开" value="HIDDEN" />
        </el-select>
        <el-select v-model="filters.adoptionStatus" clearable placeholder="领养状态">
          <el-option label="可领养" value="ADOPTING" />
          <el-option label="已领养" value="ADOPTED" />
          <el-option label="不开放" value="NOT_ADOPTING" />
        </el-select>
        <el-button type="primary" :loading="loading" @click="search">查询</el-button>
      </template>

      <el-table v-loading="loading" :data="pets" data-testid="admin-pets-table" empty-text="暂无宠物档案">
        <el-table-column prop="name" label="宠物名称" min-width="150" />
        <el-table-column prop="species" label="物种" min-width="110" />
        <el-table-column prop="breedName" label="品种" min-width="140" />
        <el-table-column label="公开状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.publicStatus || 'HIDDEN'" :label="publicStatusLabel(row.publicStatus)" />
          </template>
        </el-table-column>
        <el-table-column label="领养状态" width="120">
          <template #default="{ row }">
            <StatusTag :status="row.adoptionStatus || 'NOT_ADOPTING'" :label="adoptionStatusLabel(row.adoptionStatus)" />
          </template>
        </el-table-column>
      </el-table>

      <template #pagination>
        <el-pagination
          background
          layout="prev, pager, next"
          :current-page="page.page"
          :page-size="page.size"
          :total="total"
          @current-change="changePage"
        />
      </template>
    </AdminTableShell>
  </section>
</template>

<script>
import { listAdminPets } from '@/api/pets'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'AdminPetsView',
  components: { AdminPageHeader, AdminTableShell, StatusTag },
  data() {
    return {
      pets: [],
      total: 0,
      loading: false,
      filters: { keyword: '', publicStatus: '', adoptionStatus: '' },
      page: { page: 1, size: 20 }
    }
  },
  created() {
    this.load()
  },
  methods: {
    async load() {
      this.loading = true
      try {
        const response = await listAdminPets({
          keyword: this.filters.keyword || undefined,
          publicStatus: this.filters.publicStatus || undefined,
          adoptionStatus: this.filters.adoptionStatus || undefined,
          page: this.page.page,
          size: this.page.size
        })
        const payload = response.data && response.data.data ? response.data.data : response.data
        this.pets = (payload && payload.items) || []
        this.total = (payload && payload.total) || 0
      } catch (error) {
        this.$message && this.$message.error(error.response?.data?.message || '后台宠物加载失败')
      } finally {
        this.loading = false
      }
    },
    search() {
      this.page.page = 1
      this.load()
    },
    changePage(page) {
      this.page.page = page
      this.load()
    },
    publicStatusLabel(status) {
      return { PUBLISHED: '已公开', HIDDEN: '未公开' }[status] || status || '未公开'
    },
    adoptionStatusLabel(status) {
      return { ADOPTING: '可领养', ADOPTED: '已领养', NOT_ADOPTING: '不开放' }[status] || status || '不开放'
    }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
</style>

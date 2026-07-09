<template>
  <section class="my-stray-clues">
    <h2 data-testid="my-stray-title">我的流浪救助线索</h2>
    <div class="toolbar">
      <el-select v-model="filters.status" placeholder="状态" clearable @change="loadClues">
        <el-option label="待受理" value="SUBMITTED" />
        <el-option label="已指派" value="ASSIGNED" />
        <el-option label="救助中" value="IN_RESCUE" />
        <el-option label="已解决" value="RESOLVED" />
        <el-option label="已关闭" value="CLOSED" />
      </el-select>
      <el-button type="primary" @click="loadClues">查询</el-button>
    </div>

    <el-table :data="clues" data-testid="my-stray-table">
      <el-table-column prop="clueNo" label="线索号" width="170" />
      <el-table-column label="动物" width="90">
        <template slot-scope="{ row }">{{ animalLabel(row.animalType) }}</template>
      </el-table-column>
      <el-table-column prop="location" label="位置" />
      <el-table-column label="状态" width="110">
        <template slot-scope="{ row }"><el-tag :type="statusTagType(row.status)">{{ row.statusLabel || statusLabel(row.status) }}</el-tag></template>
      </el-table-column>
      <el-table-column prop="createdAt" label="提交时间" width="180" />
      <el-table-column label="操作" width="90">
        <template slot-scope="{ row }"><el-button size="mini" @click="openDetail(row)">详情</el-button></template>
      </el-table-column>
    </el-table>

    <el-dialog title="线索详情" :visible.sync="showDetail" width="640px">
      <div v-if="current" data-testid="my-stray-detail">
        <p><strong>线索号：</strong>{{ current.clueNo }}</p>
        <p><strong>动物类型：</strong>{{ animalLabel(current.animalType) }}</p>
        <p><strong>发现位置：</strong>{{ current.location }}</p>
        <p><strong>现场描述：</strong>{{ current.description }}</p>
        <p><strong>状态：</strong>{{ current.statusLabel || statusLabel(current.status) }}</p>
        <p v-if="current.adminNote"><strong>处理备注：</strong>{{ current.adminNote }}</p>
        <p v-if="current.handoffNote"><strong>后续说明：</strong>{{ current.handoffNote }}</p>
        <div v-if="current.images && current.images.length" class="thumbs">
          <img v-for="img in current.images" :key="img.fileId" :src="img.previewUrl" alt="线索图片">
        </div>
      </div>
      <span slot="footer"><el-button @click="showDetail = false">关闭</el-button></span>
    </el-dialog>
  </section>
</template>

<script>
import { getMyStrayClue, listMyStrayClues } from '@/api/stray'

export default {
  name: 'MyStrayCluesView',
  data() {
    return {
      clues: [],
      total: 0,
      filters: { status: undefined },
      page: { page: 1, size: 10 },
      showDetail: false,
      current: null
    }
  },
  created() {
    this.loadClues()
  },
  methods: {
    async loadClues() {
      try {
        const response = await listMyStrayClues({
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.clues = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async openDetail(row) {
      try {
        const response = await getMyStrayClue(row.id)
        this.current = response.data
        this.showDetail = true
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    animalLabel(type) {
      return { DOG: '狗', CAT: '猫', OTHER: '其他' }[type] || type
    },
    statusLabel(status) {
      return { SUBMITTED: '待受理', ASSIGNED: '已指派', IN_RESCUE: '救助中', RESOLVED: '已解决', CLOSED: '已关闭' }[status] || status
    },
    statusTagType(status) {
      if (status === 'RESOLVED') return 'success'
      if (status === 'CLOSED') return 'info'
      if (status === 'IN_RESCUE') return 'warning'
      return ''
    }
  }
}
</script>

<style scoped>
.my-stray-clues { max-width: 1080px; margin: 24px auto; }
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.thumbs { display: flex; gap: 10px; flex-wrap: wrap; }
.thumbs img { width: 120px; height: 90px; object-fit: cover; border-radius: 6px; }
</style>

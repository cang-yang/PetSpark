<template>
  <section class="admin-community-page">
    <el-card>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索帖子" clearable />
        <el-select v-model="filters.status" placeholder="状态" clearable>
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已隐藏" value="HIDDEN" />
        </el-select>
        <el-button type="primary" @click="loadPosts">查询</el-button>
      </div>
      <el-table :data="posts" data-testid="admin-community-posts">
        <el-table-column prop="title" label="标题" min-width="220" />
        <el-table-column prop="authorName" label="作者" width="140" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="moderationReason" label="处理原因" min-width="180" />
        <el-table-column label="操作" width="180">
          <template slot-scope="scope">
            <el-button size="mini" @click="moderate(scope.row, 'PUBLISHED')">发布</el-button>
            <el-button size="mini" type="warning" @click="moderate(scope.row, 'HIDDEN')">隐藏</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </section>
</template>

<script>
import { listAdminCommunityPosts, moderateCommunityPost } from '@/api/community'

export default {
  name: 'AdminCommunityView',
  data() {
    return { posts: [], filters: { keyword: undefined, status: undefined }, page: { page: 1, size: 50 } }
  },
  created() {
    this.loadPosts()
  },
  methods: {
    async loadPosts() {
      try {
        const response = await listAdminCommunityPosts({ keyword: this.filters.keyword || undefined, status: this.filters.status || undefined, page: this.page.page, size: this.page.size })
        this.posts = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async moderate(row, status) {
      const reason = status === 'HIDDEN' ? '社区规范处理' : '恢复展示'
      try {
        await moderateCommunityPost(row.id, { status, reason, version: row.version })
        this.$message && this.$message.success('状态已更新')
        await this.loadPosts()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    }
  }
}
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
</style>

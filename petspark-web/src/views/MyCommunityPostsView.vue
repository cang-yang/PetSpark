<template>
  <section class="my-community-page">
    <el-card>
      <div class="toolbar">
        <el-select v-model="filters.status" placeholder="状态" clearable @change="loadPosts">
          <el-option label="已发布" value="PUBLISHED" />
          <el-option label="已隐藏" value="HIDDEN" />
        </el-select>
      </div>
      <el-table :data="posts" data-testid="my-community-posts">
        <el-table-column prop="title" label="标题" min-width="220">
          <template slot-scope="scope"><router-link :to="`/community/posts/${scope.row.id}`">{{ scope.row.title }}</router-link></template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="likeCount" label="赞" width="80" />
        <el-table-column prop="favoriteCount" label="收藏" width="80" />
        <el-table-column prop="commentCount" label="评论" width="80" />
        <el-table-column prop="moderationReason" label="处理原因" min-width="180" />
      </el-table>
    </el-card>
  </section>
</template>

<script>
import { listMyCommunityPosts } from '@/api/community'

export default {
  name: 'MyCommunityPostsView',
  data() {
    return { posts: [], filters: { status: undefined }, page: { page: 1, size: 50 } }
  },
  created() {
    this.loadPosts()
  },
  methods: {
    async loadPosts() {
      try {
        const response = await listMyCommunityPosts({ status: this.filters.status || undefined, page: this.page.page, size: this.page.size })
        this.posts = response.data.items || []
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

<template>
  <section class="community-page">
    <el-card>
      <div class="toolbar">
        <el-input v-model="filters.keyword" placeholder="搜索社区内容" clearable @keyup.enter.native="loadPosts" />
        <el-button type="primary" @click="loadPosts">搜索</el-button>
        <el-button @click="showCreate = !showCreate">发布帖子</el-button>
      </div>
      <el-form v-if="showCreate" class="create-form" :model="form" label-width="80px" data-testid="community-create-form">
        <el-form-item label="标题"><el-input v-model="form.title" maxlength="120" show-word-limit /></el-form-item>
        <el-form-item label="内容"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="10000" /></el-form-item>
        <el-form-item><el-button type="primary" :loading="submitting" @click="submitPost">提交</el-button></el-form-item>
      </el-form>
      <div v-if="posts.length" class="post-list" data-testid="community-list">
        <article v-for="post in posts" :key="post.id" class="post-card" :data-testid="`community-post-${post.id}`">
          <router-link :to="`/community/posts/${post.id}`"><h3>{{ post.title }}</h3></router-link>
          <p class="meta">{{ post.authorName }} · {{ formatTime(post.createdAt) }}</p>
          <p class="excerpt">{{ post.content }}</p>
          <div class="stats">赞 {{ post.likeCount }} · 收藏 {{ post.favoriteCount }} · 评论 {{ post.commentCount }}</div>
        </article>
      </div>
      <p v-else-if="!loading" data-testid="community-empty">暂无社区内容</p>
      <el-pagination v-if="total > page.size" :current-page="page.page" :page-size="page.size" :total="total" layout="prev, pager, next" @current-change="changePage" />
    </el-card>
  </section>
</template>

<script>
import { listCommunityPosts, createCommunityPost } from '@/api/community'

export default {
  name: 'CommunityListView',
  data() {
    return {
      loading: false,
      submitting: false,
      showCreate: false,
      posts: [],
      total: 0,
      filters: { keyword: undefined },
      page: { page: 1, size: 10 },
      form: { title: '', content: '' }
    }
  },
  created() {
    this.loadPosts()
  },
  methods: {
    async loadPosts() {
      this.loading = true
      try {
        const response = await listCommunityPosts({ keyword: this.filters.keyword || undefined, page: this.page.page, size: this.page.size })
        this.posts = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.loading = false
      }
    },
    async submitPost() {
      if (!this.form.title || !this.form.content) {
        this.$message && this.$message.warning('请填写标题和内容')
        return
      }
      this.submitting = true
      try {
        const response = await createCommunityPost({ title: this.form.title, content: this.form.content })
        this.$message && this.$message.success('帖子已发布')
        this.$router && this.$router.push(`/community/posts/${response.data.id}`)
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
    },
    changePage(page) {
      this.page.page = page
      this.loadPosts()
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      return Number.isNaN(date.getTime()) ? String(value) : date.toLocaleString()
    }
  }
}
</script>

<style scoped>
.toolbar { display: flex; gap: 12px; margin-bottom: 16px; }
.create-form { margin-bottom: 20px; padding: 16px; background: #f5f7fa; border-radius: 8px; }
.post-list { display: grid; gap: 14px; }
.post-card { padding: 16px; border: 1px solid #ebeef5; border-radius: 8px; background: #fff; }
.meta, .stats { color: #909399; font-size: 13px; }
.excerpt { color: #606266; white-space: pre-wrap; }
</style>

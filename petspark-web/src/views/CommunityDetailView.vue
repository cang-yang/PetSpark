<template>
  <section class="community-detail-page">
    <el-card v-if="post">
      <h2>{{ post.title }}</h2>
      <p class="meta">{{ post.authorName }} · {{ formatTime(post.createdAt) }}</p>
      <p class="content">{{ post.content }}</p>
      <div class="actions">
        <el-button :type="post.liked ? 'primary' : 'default'" @click="toggleLike">赞 {{ post.likeCount }}</el-button>
        <el-button :type="post.favorited ? 'warning' : 'default'" @click="toggleFavorite">收藏 {{ post.favoriteCount }}</el-button>
      </div>
    </el-card>

    <el-card v-if="post" class="comments-card">
      <h3>评论</h3>
      <el-form :model="commentForm" class="comment-form">
        <el-input v-model="commentForm.content" type="textarea" :rows="3" maxlength="1000" placeholder="友善评论，分享养宠经验" />
        <el-button type="primary" :loading="submitting" @click="submitComment">发表评论</el-button>
      </el-form>
      <div v-if="comments.length" data-testid="community-comments">
        <article v-for="comment in comments" :key="comment.id" class="comment">
          <strong>{{ comment.authorName }}</strong>
          <span class="meta"> · {{ formatTime(comment.createdAt) }}</span>
          <p>{{ comment.content }}</p>
        </article>
      </div>
      <p v-else data-testid="community-comments-empty">暂无评论</p>
    </el-card>
  </section>
</template>

<script>
import {
  createCommunityComment,
  favoriteCommunityPost,
  getCommunityPost,
  likeCommunityPost,
  listCommunityComments,
  unfavoriteCommunityPost,
  unlikeCommunityPost
} from '@/api/community'

export default {
  name: 'CommunityDetailView',
  data() {
    return {
      post: null,
      comments: [],
      submitting: false,
      commentForm: { content: '' }
    }
  },
  created() {
    this.loadPost()
    this.loadComments()
  },
  methods: {
    async loadPost() {
      try {
        const response = await getCommunityPost(this.$route.params.id)
        this.post = response.data
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async loadComments() {
      try {
        const response = await listCommunityComments(this.$route.params.id, { page: 1, size: 100 })
        this.comments = response.data.items || []
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    async toggleLike() {
      const response = this.post.liked ? await unlikeCommunityPost(this.post.id) : await likeCommunityPost(this.post.id)
      this.post = response.data
    },
    async toggleFavorite() {
      const response = this.post.favorited ? await unfavoriteCommunityPost(this.post.id) : await favoriteCommunityPost(this.post.id)
      this.post = response.data
    },
    async submitComment() {
      if (!this.commentForm.content) {
        this.$message && this.$message.warning('请填写评论内容')
        return
      }
      this.submitting = true
      try {
        await createCommunityComment(this.post.id, { content: this.commentForm.content })
        this.commentForm.content = ''
        await this.loadPost()
        await this.loadComments()
        this.$message && this.$message.success('评论已发布')
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.submitting = false
      }
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
.meta { color: #909399; font-size: 13px; }
.content { white-space: pre-wrap; color: #303133; }
.actions { display: flex; gap: 12px; margin-top: 16px; }
.comments-card { margin-top: 16px; }
.comment-form { display: grid; gap: 12px; margin-bottom: 16px; }
.comment { padding: 12px 0; border-top: 1px solid #ebeef5; }
.comment p { white-space: pre-wrap; }
</style>

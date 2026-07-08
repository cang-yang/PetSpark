<template>
  <section class="notifications-view">
    <header class="notifications-view__head">
      <h2>通知中心</h2>
      <div class="notifications-view__toolbar">
        <el-radio-group v-model="onlyUnread" size="small" @change="onFilterChange">
          <el-radio-button :label="false">全部</el-radio-button>
          <el-radio-button :label="true">仅未读</el-radio-button>
        </el-radio-group>
        <el-button
          size="small"
          type="primary"
          :disabled="!hasUnread || loading"
          :loading="markingAll"
          data-testid="mark-all-read"
          @click="markAll"
        >
          全部已读
          <span v-if="unreadCount">（{{ unreadCount }}）</span>
        </el-button>
      </div>
    </header>

    <!-- 区分骨架加载 / 空状态 / 错误 / 正常内容，不用一个空表覆盖所有情况（§11）。 -->
    <div v-if="loading" class="notifications-view__skeleton" data-testid="skeleton">
      <el-skeleton :rows="3" animated />
    </div>

    <status-panel
      v-else-if="error"
      status="error"
      status-label="加载失败"
      status-class="error"
      :reason="error"
      role="系统"
      next-step="请稍后重试，或联系管理员查看服务状态"
      test-id="error-panel"
    >
      <template #actions>
        <el-button size="small" @click="load">重试</el-button>
      </template>
    </status-panel>

    <status-panel
      v-else-if="!items.length"
      status="empty"
      :status-label="onlyUnread ? '暂无未读通知' : '暂无通知'"
      status-class="info"
      :reason="onlyUnread ? '当前筛选下没有未读通知' : '还没有任何通知'"
      role="系统"
      :next-step="onlyUnread ? '可切换到「全部」查看历史通知' : '稍后回来查看新通知'"
      test-id="empty-panel"
    />

    <ul v-else class="notification-list" data-testid="notification-list">
      <li
        v-for="item in items"
        :key="item.id"
        class="notification-item"
        :class="{ 'is-read': item.read }"
        :data-testid="`notification-${item.id}`"
      >
        <div class="notification-item__main">
          <div class="notification-item__title-line">
            <el-tag size="mini" :type="tagType(item.type)">{{ typeLabel(item.type) }}</el-tag>
            <span class="notification-item__title">{{ item.title }}</span>
            <span v-if="!item.read" class="notification-item__dot" data-testid="unread-dot" />
          </div>
          <p class="notification-item__content">{{ item.content }}</p>
          <div class="notification-item__meta">
            <span>{{ formatTime(item.createdAt) }}</span>
            <span v-if="item.read">已读 · {{ formatTime(item.readAt) }}</span>
          </div>
        </div>
        <div class="notification-item__action">
          <el-button
            v-if="!item.read"
            size="mini"
            :loading="markingId === item.id"
            data-testid="mark-read"
            @click="markOne(item)"
          >
            标记已读
          </el-button>
        </div>
      </li>
    </ul>

    <footer v-if="!loading && !error && total > size" class="notifications-view__pager">
      <el-pagination
        background
        layout="prev, pager, next"
        :current-page="page"
        :page-size="size"
        :total="total"
        @current-change="onPageChange"
      />
    </footer>
  </section>
</template>

<script>
import { listNotifications, markNotificationRead, markAllNotificationsRead } from '@/api/notifications'
import StatusPanel from '@/components/StatusPanel.vue'

const DEFAULT_SIZE = 10

/**
 * 通知中心（API-NOTIFY-001~003，§10.1 路由 /notifications，权限=登录）。
 * 列表、未读筛选、分页与 URL 查询参数同步（§11）；首次进入区分加载/空/错误/正常状态（§11）；
 * 空与错误状态用 StatusPanel 表达当前状态、原因、责任角色和下一步（NFR-UX-001）。
 */
export default {
  name: 'NotificationsView',
  components: { StatusPanel },
  data() {
    return {
      loading: false,
      error: '',
      items: [],
      page: 1,
      size: DEFAULT_SIZE,
      total: 0,
      unreadCount: 0,
      onlyUnread: false,
      markingId: '',
      markingAll: false
    }
  },
  computed: {
    hasUnread() {
      return this.unreadCount > 0
    }
  },
  created() {
    this.syncFromRoute()
    this.load()
  },
  methods: {
    syncFromRoute() {
      const query = this.$route.query || {}
      const page = Number(query.page)
      if (Number.isFinite(page) && page >= 1) {
        this.page = page
      }
      this.onlyUnread = query.onlyUnread === 'true'
    },
    syncToRoute() {
      const query = { page: String(this.page) }
      if (this.onlyUnread) {
        query.onlyUnread = 'true'
      }
      // 只在变化时 replace，避免无谓的导航历史堆叠。
      const current = this.$route.query || {}
      if (current.page !== query.page || !!current.onlyUnread !== !!query.onlyUnread) {
        this.$router.replace({ query }).catch(() => {})
      }
    },
    async load() {
      this.loading = true
      this.error = ''
      try {
        const res = await listNotifications({ page: this.page, size: this.size, onlyUnread: this.onlyUnread })
        const view = res.data
        this.items = view.items || []
        this.page = view.page
        this.size = view.size
        this.total = view.total
        this.unreadCount = view.unreadCount
      } catch (err) {
        this.error = err.message
      } finally {
        this.loading = false
      }
    },
    onFilterChange() {
      this.page = 1
      this.syncToRoute()
      this.load()
    },
    onPageChange(next) {
      this.page = next
      this.syncToRoute()
      this.load()
    },
    async markOne(item) {
      this.markingId = item.id
      try {
        await markNotificationRead(item.id)
        item.read = true
        item.readAt = new Date().toISOString()
        this.unreadCount = Math.max(0, this.unreadCount - 1)
        this.$message.success('已标记已读')
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.markingId = ''
      }
    },
    async markAll() {
      this.markingAll = true
      try {
        await markAllNotificationsRead()
        this.items.forEach((item) => {
          if (!item.read) {
            item.read = true
            item.readAt = item.readAt || new Date().toISOString()
          }
        })
        this.unreadCount = 0
        this.$message.success('已全部标记已读')
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.markingAll = false
      }
    },
    typeLabel(type) {
      const labels = { SYSTEM: '系统', ADOPTION: '领养', BOARDING: '寄养', ORDER: '订单' }
      return labels[type] || type || '通知'
    },
    tagType(type) {
      return type === 'SYSTEM' ? 'info' : 'success'
    },
    formatTime(value) {
      if (!value) return ''
      const date = typeof value === 'string' ? new Date(value) : value
      if (Number.isNaN(date.getTime())) return String(value)
      const pad = (n) => String(n).padStart(2, '0')
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
        + `${pad(date.getHours())}:${pad(date.getMinutes())}`
    }
  }
}
</script>

<style scoped>
.notifications-view {
  max-width: 860px;
  margin: 24px auto;
  padding: 24px;
  background: #fff;
  border-radius: 10px;
  box-shadow: 0 10px 30px rgba(36, 49, 61, 0.06);
}

.notifications-view__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
}

.notifications-view__head h2 {
  margin: 0;
}

.notifications-view__toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.notification-list {
  list-style: none;
  margin: 0;
  padding: 0;
}

.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 14px 12px;
  border-bottom: 1px solid #f0f2f5;
}

.notification-item:last-child {
  border-bottom: 0;
}

.notification-item.is-read {
  opacity: 0.7;
}

.notification-item__main {
  flex: 1;
  min-width: 0;
}

.notification-item__title-line {
  display: flex;
  align-items: center;
  gap: 8px;
}

.notification-item__title {
  font-weight: 600;
  color: #24313d;
}

.notification-item__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f56c6c;
}

.notification-item__content {
  margin: 6px 0 0;
  color: #606266;
  font-size: 14px;
  line-height: 1.5;
}

.notification-item__meta {
  display: flex;
  gap: 12px;
  margin-top: 6px;
  color: #909399;
  font-size: 12px;
}

.notification-item__action {
  flex: 0 0 auto;
}

.notifications-view__pager {
  margin-top: 16px;
  text-align: right;
}
</style>

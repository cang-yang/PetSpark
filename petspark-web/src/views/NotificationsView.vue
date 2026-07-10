<template>
  <section class="notifications-view">
    <PageHeader title="通知中心" description="集中查看预约、申请、订单和系统消息。">
      <template #actions>
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
      </template>
    </PageHeader>

    <!-- 区分骨架加载 / 空状态 / 错误 / 正常内容，不用一个空表覆盖所有情况（§11）。 -->
    <LoadingState v-if="loading" data-testid="skeleton" text="正在整理通知…" />

    <ErrorState
      v-else-if="error"
      data-testid="error-panel"
      title="通知暂时无法加载"
      :description="error"
      @retry="load"
    />

    <EmptyState
      v-else-if="!items.length"
      data-testid="empty-panel"
      :title="onlyUnread ? '暂无未读通知' : '暂无通知'"
      :description="onlyUnread ? '当前筛选下没有未读通知，可以查看全部历史消息。' : '新的预约、申请和订单消息会显示在这里。'"
      :action-text="onlyUnread ? '查看全部通知' : ''"
      :image="emptyNotification"
      image-alt="小猫在安静的信箱旁等待新消息"
      @action="showAll"
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
            <span>{{ sceneLabel(item.businessType) }} · {{ eventLabel(item.type) }}</span>
            <span>{{ formatTime(item.createdAt) }}</span>
            <span v-if="item.read">已读 · {{ formatTime(item.readAt) }}</span>
          </div>
        </div>
        <div class="notification-item__action">
          <el-button
            v-if="targetRoute(item)"
            size="mini"
            data-testid="notification-open"
            @click="openTarget(item)"
          >
            查看
          </el-button>
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
import PageHeader from '@/components/ui/PageHeader.vue'
import LoadingState from '@/components/ui/LoadingState.vue'
import ErrorState from '@/components/ui/ErrorState.vue'
import EmptyState from '@/components/ui/EmptyState.vue'
import emptyNotification from '@/assets/illustrations/empty-notification.jpg'

const DEFAULT_SIZE = 10

const SCENE_LABELS = {
  ADOPTION: '领养',
  BOARDING: '寄养',
  SERVICE: '服务',
  ORDER: '订单',
  SYSTEM: '系统'
}

const EVENT_LABELS = {
  SYSTEM: '系统通知',
  ADOPTION_WITHDRAWN: '申请撤回',
  ADOPTION_APPROVED: '申请通过',
  ADOPTION_REJECTED: '申请拒绝',
  ADOPTION_COMPLETED: '领养完成',
  ADOPTION_HANDOVER_FAILED: '交接失败',
  BOARDING_CREATED: '预约提交',
  BOARDING_CANCELLED: '预约取消',
  BOARDING_CONFIRMED: '预约确认',
  BOARDING_REJECTED: '预约拒绝',
  BOARDING_STARTED: '开始寄养',
  BOARDING_COMPLETED: '寄养完成',
  BOARDING_TERMINATED: '寄养终止',
  ORDER_CREATED: '订单创建',
  ORDER_CANCELLED: '订单取消',
  ORDER_TRANSITION: '订单状态更新',
  SERVICE_BOOKING_CREATED: '服务预约提交',
  SERVICE_BOOKING_CANCELLED: '服务预约取消',
  SERVICE_BOOKING_CONFIRMED: '服务预约确认',
  SERVICE_BOOKING_REJECTED: '服务预约拒绝',
  SERVICE_BOOKING_STARTED: '服务开始',
  SERVICE_BOOKING_COMPLETED: '服务完成'
}

const TARGET_ROUTES = {
  ADOPTION: { name: 'my-adoptions' },
  BOARDING: { name: 'my-boarding' },
  SERVICE: { name: 'my-service-bookings' },
  ORDER: { name: 'my-orders' }
}

/**
 * 通知中心（API-NOTIFY-001~003，§10.1 路由 /notifications，权限=登录）。
 * 列表、未读筛选、分页与 URL 查询参数同步（§11）；首次进入区分加载/空/错误/正常状态（§11）；
 * 空与错误状态使用统一状态组件说明当前情况并提供下一步动作（NFR-UX-001）。
 */
export default {
  name: 'NotificationsView',
  components: { PageHeader, LoadingState, ErrorState, EmptyState },
  data() {
    return {
      emptyNotification,
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
        this.publishUnreadCount()
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
    showAll() {
      this.onlyUnread = false
      this.onFilterChange()
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
        if (this.onlyUnread) {
          this.items = this.items.filter((current) => current.id !== item.id)
          this.total = Math.max(0, this.total - 1)
          if (!this.items.length && this.total > 0 && this.page > 1) {
            this.page -= 1
            this.syncToRoute()
          }
          await this.load()
        } else {
          this.publishUnreadCount()
        }
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
        if (this.onlyUnread) {
          this.items = []
          this.total = 0
        }
        this.publishUnreadCount()
        this.$message.success('已全部标记已读')
      } catch (err) {
        this.$message.error(err.message)
      } finally {
        this.markingAll = false
      }
    },
    typeLabel(type) {
      if (EVENT_LABELS[type]) return EVENT_LABELS[type]
      if (!type) return '通知'
      return type.split('_').map((part) => part.charAt(0) + part.slice(1).toLowerCase()).join(' ')
    },
    sceneLabel(scene) {
      return SCENE_LABELS[scene] || SCENE_LABELS.SYSTEM
    },
    eventLabel(type) {
      return EVENT_LABELS[type] || this.typeLabel(type)
    },
    targetRoute(item) {
      if (!item || !item.businessType || !TARGET_ROUTES[item.businessType]) return null
      return { ...TARGET_ROUTES[item.businessType], query: item.businessId ? { highlight: item.businessId } : {} }
    },
    async openTarget(item) {
      const route = this.targetRoute(item)
      if (!route) return
      if (!item.read) {
        await this.markOne(item)
      }
      this.$router.push(route).catch(() => {})
    },
    publishUnreadCount() {
      if (this.$store && this.$store.dispatch) {
        this.$store.dispatch('setNotificationUnreadCount', this.unreadCount)
      }
    },
    tagType(type) {
      if (type && type.startsWith('ADOPTION')) return 'success'
      if (type && type.startsWith('BOARDING')) return 'warning'
      if (type && type.startsWith('SERVICE')) return 'primary'
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
  margin: 0 auto;
  padding: 24px;
  background: #fff;
  border-radius: var(--ps-radius-lg);
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
@media (max-width: 680px) {
  .notifications-view { padding: 20px 16px; }
  .notifications-view__toolbar { align-items: stretch; flex-direction: column; }
  .notification-item { flex-direction: column; }
  .notification-item__meta { flex-wrap: wrap; }
  .notification-item__action { width: 100%; }
}
</style>

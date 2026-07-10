<template>
  <section class="admin-console-page admin-boardings">
    <AdminPageHeader eyebrow="寄养履约" title="寄养预约管理" description="分配房间并跟进寄养预约的确认与履约状态。" />
    <AdminTableShell title="寄养预约" :total="total">
      <template #filters>
      <el-input v-model="filters.keyword" placeholder="预约号" clearable />
      <el-select v-model="filters.status" placeholder="状态" clearable>
        <el-option label="待确认" value="PENDING_CONFIRMATION" />
        <el-option label="已确认" value="CONFIRMED" />
        <el-option label="履约中" value="IN_SERVICE" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="已取消" value="CANCELLED" />
        <el-option label="已拒绝" value="REJECTED" />
        <el-option label="已终止" value="TERMINATED" />
      </el-select>
      <el-button type="primary" @click="search">查询</el-button>
      </template>

    <el-table :data="bookings" data-testid="admin-boarding-table">
      <el-table-column prop="bookingNo" label="预约号" min-width="190" />
      <el-table-column prop="petName" label="宠物" min-width="80" />
      <el-table-column prop="roomName" label="房间" min-width="130" />
      <el-table-column label="日期" min-width="190">
        <template slot-scope="{ row }"><span class="date-range">{{ row.startDate }} ~ {{ row.endDate }}</span></template>
      </el-table-column>
      <el-table-column label="状态">
        <template slot-scope="{ row }">
          <StatusTag :status="row.status" :label="statusLabel(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" width="320">
        <template slot-scope="{ row }">
          <el-button size="mini" type="primary" :disabled="row.status !== 'PENDING_CONFIRMATION'" @click="openAssign(row)">分配房间</el-button>
          <el-button size="mini" type="success" :disabled="row.status !== 'CONFIRMED'" @click="transition(row, 'IN_SERVICE', '开始履约')">开始</el-button>
          <el-button size="mini" type="warning" :disabled="row.status !== 'IN_SERVICE'" @click="transition(row, 'COMPLETED', '完成')">完成</el-button>
          <el-button size="mini" type="danger" :disabled="row.status !== 'PENDING_CONFIRMATION'" @click="reject(row)">拒绝</el-button>
        </template>
      </el-table-column>
    </el-table>
      <template #pagination><el-pagination background layout="prev, pager, next" :current-page="page.page" :page-size="page.size" :total="total" @current-change="changePage" /></template>
    </AdminTableShell>

    <el-dialog title="分配房间" :visible.sync="showAssign" width="480px">
      <el-form label-width="80px">
        <el-form-item label="房间">
          <el-select v-model="assignForm.roomId" placeholder="选择房间">
            <el-option v-for="room in rooms" :key="room.id" :label="room.name" :value="room.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="assignForm.note" maxlength="255" /></el-form-item>
      </el-form>
      <span slot="footer">
        <el-button @click="showAssign = false">取消</el-button>
        <el-button type="primary" :loading="assigning" @click="submitAssign">确认分配</el-button>
      </span>
    </el-dialog>

    <ConfirmActionDialog
      :visible.sync="rejectDialogVisible"
      title="拒绝寄养预约"
      description="确认拒绝当前寄养预约吗？"
      warning="拒绝后预约将无法继续分配房间。"
      confirm-text="确认拒绝"
      require-reason
      reason-placeholder="请填写拒绝原因"
      :loading="rejecting"
      @confirm="confirmReject"
    />
  </section>
</template>

<script>
import { listAdminBookings, assignRoom, transitionBooking, listRooms } from '@/api/boarding'
import AdminPageHeader from '@/components/ui/AdminPageHeader.vue'
import AdminTableShell from '@/components/ui/AdminTableShell.vue'
import ConfirmActionDialog from '@/components/ui/ConfirmActionDialog.vue'
import StatusTag from '@/components/ui/StatusTag.vue'

export default {
  name: 'AdminBoardingsView',
  components: { AdminPageHeader, AdminTableShell, ConfirmActionDialog, StatusTag },
  data() {
    return {
      bookings: [],
      total: 0,
      filters: { keyword: undefined, status: undefined },
      page: { page: 1, size: 20 },
      rooms: [],
      showAssign: false,
      assigning: false,
      assignForm: { bookingId: null, roomId: undefined, note: '', version: 0 },
      rejectDialogVisible: false,
      rejecting: false,
      pendingRejectBooking: null
    }
  },
  created() {
    this.loadBookings()
    this.loadRooms()
  },
  methods: {
    async loadBookings() {
      try {
        const response = await listAdminBookings({
          keyword: this.filters.keyword || undefined,
          status: this.filters.status || undefined,
          page: this.page.page,
          size: this.page.size
        })
        this.bookings = response.data.items || []
        this.total = response.data.total || 0
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    search() { this.page.page = 1; this.loadBookings() },
    changePage(page) { this.page.page = page; this.loadBookings() },
    async loadRooms() {
      try {
        const response = await listRooms({ page: 1, size: 100, status: 'ACTIVE' })
        this.rooms = response.data.items || []
      } catch (error) {
        // 房间加载失败不阻塞主流程
      }
    },
    openAssign(row) {
      this.assignForm = { bookingId: row.id, roomId: undefined, note: '', version: row.version }
      this.showAssign = true
    },
    async submitAssign() {
      if (!this.assignForm.roomId) {
        this.$message && this.$message.warning('请选择房间')
        return
      }
      this.assigning = true
      try {
        await assignRoom(this.assignForm.bookingId, {
          roomId: this.assignForm.roomId,
          note: this.assignForm.note,
          version: this.assignForm.version
        })
        this.$message && this.$message.success('房间已分配')
        this.showAssign = false
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.assigning = false
      }
    },
    async transition(row, status, note) {
      try {
        await transitionBooking(row.id, { status, note, version: row.version })
        this.$message && this.$message.success('状态已更新')
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      }
    },
    reject(row) {
      this.pendingRejectBooking = row
      this.rejectDialogVisible = true
    },
    async confirmReject(reason) {
      const row = this.pendingRejectBooking
      if (!row || !reason) return
      this.rejecting = true
      try {
        await transitionBooking(row.id, { status: 'REJECTED', reason, version: row.version })
        this.$message && this.$message.success('已拒绝预约')
        this.rejectDialogVisible = false
        this.pendingRejectBooking = null
        await this.loadBookings()
      } catch (error) {
        this.$message && this.$message.error(error.message)
      } finally {
        this.rejecting = false
      }
    },
    statusLabel(status) {
      const labels = {
        PENDING_CONFIRMATION: '待确认', CONFIRMED: '已确认', IN_SERVICE: '履约中',
        COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝', TERMINATED: '已终止'
      }
      return labels[status] || status
    },
    statusTagType(status) {
      if (status === 'COMPLETED') return 'success'
      if (status === 'CANCELLED' || status === 'REJECTED' || status === 'TERMINATED') return 'info'
      if (status === 'IN_SERVICE') return 'warning'
      return ''
    }
  }
}
</script>

<style scoped>
.admin-console-page { display: grid; gap: 20px; }
.date-range { white-space: nowrap; }
</style>

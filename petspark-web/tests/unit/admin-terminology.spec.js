import {
  getAuditActionLabel,
  getPermissionLabel,
  getRoleMeta
} from '@/utils/adminTerminology'

describe('adminTerminology', () => {
  it.each([
    ['adoption:handover', '记录领养交接'],
    ['adoption:review', '审核领养申请'],
    ['audit:read', '查看审计记录'],
    ['banner:manage', '管理横幅运营'],
    ['beauty:fulfill', '处理美容预约'],
    ['beauty:manage', '管理美容服务'],
    ['boarding:fulfill', '处理寄养预约'],
    ['boarding:manage', '管理寄养服务'],
    ['breed:manage', '管理宠物品种'],
    ['community:manage', '管理社区内容'],
    ['community:moderate', '审核社区内容'],
    ['config:read', '查看系统设置'],
    ['config:update', '修改系统设置'],
    ['dashboard:read', '查看统计仪表盘'],
    ['dict:read', '查看业务选项'],
    ['dict:update', '修改业务选项'],
    ['file:upload', '上传图片文件'],
    ['goods:manage', '管理商品'],
    ['health:correct', '更正健康档案'],
    ['health:manage', '管理健康档案'],
    ['medical:fulfill', '处理医疗预约'],
    ['medical:manage', '管理医疗服务'],
    ['order:manage', '管理订单'],
    ['pet:manage', '管理宠物'],
    ['pet:read', '查看宠物'],
    ['pet:status', '变更宠物状态'],
    ['privacy:manage', '管理隐私合规'],
    ['role:read', '查看角色权限'],
    ['role:update', '修改角色权限'],
    ['room:manage', '管理寄养房间'],
    ['room:read', '查看寄养房间'],
    ['service:fulfill', '处理通用服务预约'],
    ['service:manage', '管理通用服务'],
    ['stray:manage', '管理流浪救助'],
    ['stray:read', '查看流浪救助'],
    ['system:observe', '查看系统运行状态'],
    ['user:profile', '维护个人资料'],
    ['user:read', '查看用户账号'],
    ['user:update', '修改用户账号']
  ])('renders %s as a fully readable Chinese permission label', (code, expected) => {
    const [resource, action] = code.split(':')
    expect(getPermissionLabel({ code, resource, action, description: '' })).toBe(expected)
  })

  it.each([
    ['create_adoption_application', '创建领养申请'],
    ['update_banner_status', '更新横幅状态'],
    ['complete_adoption_handover', '完成领养交接'],
    ['adjust_stock', '调整库存']
  ])('renders compound audit action %s as Chinese', (action, expected) => {
    expect(getAuditActionLabel(action)).toBe(expected)
  })

  it.each([
    ['operator', '运营人员'],
    ['user', '注册用户'],
    ['service', '服务人员'],
    ['auditor', '审计人员']
  ])('renders audit actor role %s as Chinese', (role, expected) => {
    expect(getRoleMeta(role).label).toBe(expected)
  })
})

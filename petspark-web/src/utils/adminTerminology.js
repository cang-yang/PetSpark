const ROLE_META = {
  ADMIN: { label: '平台管理员', description: '拥有平台全部管理权限，适合系统负责人。', tone: 'danger' },
  USER: { label: '注册用户', description: '使用用户端功能并管理自己的宠物与订单。', tone: 'success' },
  OP: { label: '运营人员', description: '负责内容、商品、活动和业务流程运营。', tone: 'warning' },
  SERVICE: { label: '服务人员', description: '处理寄养、美容、医疗等服务履约。', tone: '' },
  AUDITOR: { label: '审计人员', description: '只读查看关键记录与审计信息。', tone: 'info' }
}

const ROLE_ALIASES = {
  OPERATOR: 'OP',
  MEMBER: 'USER'
}

const RESOURCE_LABELS = {
  adoption: '领养管理',
  audit: '审计记录',
  banner: '横幅运营',
  beauty: '美容服务',
  boarding: '寄养服务',
  breed: '宠物品种',
  community: '社区内容',
  config: '系统设置',
  dashboard: '统计仪表盘',
  dictionary: '业务选项',
  dict: '业务选项',
  file: '图片文件',
  goods: '商品管理',
  health: '健康档案',
  medical: '医疗服务',
  notification: '通知消息',
  order: '订单管理',
  outbox: '消息投递',
  pet: '宠物管理',
  privacy: '隐私合规',
  profile: '个人资料',
  role: '角色权限',
  room: '寄养房间',
  service: '服务项目',
  stray: '流浪救助',
  system: '系统运行状态',
  training: '训练服务',
  user: '用户账号'
}

const ACTION_LABELS = {
  approve: '审核通过',
  assign: '分配',
  create: '新建',
  correct: '更正',
  delete: '删除',
  erase: '合规删除',
  fulfill: '履约处理',
  handover: '记录交接',
  manage: '管理',
  moderate: '内容审核',
  observe: '查看运行状态',
  profile: '维护',
  read: '查看',
  review: '审核',
  status: '变更状态',
  update: '修改',
  upload: '上传'
}

const PERMISSION_LABELS = {
  'adoption:handover': '记录领养交接',
  'adoption:review': '审核领养申请',
  'audit:read': '查看审计记录',
  'banner:manage': '管理横幅运营',
  'beauty:fulfill': '处理美容预约',
  'beauty:manage': '管理美容服务',
  'boarding:fulfill': '处理寄养预约',
  'boarding:manage': '管理寄养服务',
  'breed:manage': '管理宠物品种',
  'community:manage': '管理社区内容',
  'community:moderate': '审核社区内容',
  'config:read': '查看系统设置',
  'config:update': '修改系统设置',
  'dashboard:read': '查看统计仪表盘',
  'dict:read': '查看业务选项',
  'dict:update': '修改业务选项',
  'file:upload': '上传图片文件',
  'goods:manage': '管理商品',
  'health:correct': '更正健康档案',
  'health:manage': '管理健康档案',
  'medical:fulfill': '处理医疗预约',
  'medical:manage': '管理医疗服务',
  'order:manage': '管理订单',
  'pet:manage': '管理宠物',
  'pet:read': '查看宠物',
  'pet:status': '变更宠物状态',
  'privacy:manage': '管理隐私合规',
  'role:read': '查看角色权限',
  'role:update': '修改角色权限',
  'room:manage': '管理寄养房间',
  'room:read': '查看寄养房间',
  'service:fulfill': '处理通用服务预约',
  'service:manage': '管理通用服务',
  'stray:manage': '管理流浪救助',
  'stray:read': '查看流浪救助',
  'system:observe': '查看系统运行状态',
  'user:profile': '维护个人资料',
  'user:read': '查看用户账号',
  'user:update': '修改用户账号'
}

const AUDIT_TOKEN_LABELS = {
  adjust: '调整',
  adoption: '领养',
  application: '申请',
  assign: '分配',
  banner: '横幅',
  boarding: '寄养预约',
  booking: '服务预约',
  cancel: '取消',
  category: '商品分类',
  clue: '线索',
  comment: '评论',
  complete: '完成',
  create: '创建',
  delete: '删除',
  erase: '合规删除',
  fail: '标记失败',
  goods: '商品',
  handover: '交接',
  health: '健康',
  moderate: '审核',
  order: '订单',
  post: '帖子',
  record: '记录',
  revise: '修订',
  review: '审核',
  room: '房间',
  status: '状态',
  stock: '库存',
  stray: '流浪救助',
  update: '更新',
  withdraw: '撤回'
}

const CONFIG_META = {
  'feature.registration.enabled': {
    label: '开放用户注册',
    description: '关闭后，新访客将无法创建账号，已有用户不受影响。'
  },
  'site.notice': {
    label: '站点公告',
    description: '用于向用户展示平台运营通知；留空表示暂不发布公告。'
  }
}

const DICT_TYPE_LABELS = {
  PET_GENDER: '宠物性别',
  PetGender: '宠物性别',
  pet_gender: '宠物性别'
}

const DICT_ITEM_LABELS = {
  MALE: '雄性',
  FEMALE: '雌性',
  UNKNOWN: '暂不确定',
  ENABLED: '启用',
  DISABLED: '停用'
}

const AUDIT_MODULE_LABELS = {
  adoption: '领养管理',
  auth: '登录与认证',
  banner: '横幅运营',
  config: '系统设置',
  dictionary: '业务选项',
  order: '订单管理',
  pet: '宠物管理',
  role: '角色权限',
  system: '系统设置',
  user: '用户账号'
}

export function getRoleMeta(code, fallbackName = '') {
  const normalized = String(code || '').toUpperCase()
  const canonical = ROLE_ALIASES[normalized] || normalized
  return ROLE_META[canonical] || {
    label: fallbackName || code || '未命名角色',
    description: '自定义角色，请根据权限范围确认其职责。',
    tone: 'info'
  }
}

export function getResourceLabel(resource) {
  return RESOURCE_LABELS[resource] || resource || '其他权限'
}

export function getActionLabel(action) {
  return ACTION_LABELS[action] || action || '操作'
}

export function getPermissionLabel(permission) {
  if (!permission) return '未知权限'
  const description = permission.description || ''
  if (description && /[\u4e00-\u9fff]/.test(description)) return description
  if (PERMISSION_LABELS[permission.code]) return PERMISSION_LABELS[permission.code]
  return `${getActionLabel(permission.action)}${getResourceLabel(permission.resource)}`
}

export function getConfigMeta(config) {
  const key = config && config.configKey
  return CONFIG_META[key] || {
    label: (config && config.description) || key || '未命名设置',
    description: '非敏感运行设置；修改前请确认业务影响。'
  }
}

export function getDictTypeLabel(type) {
  if (!type) return '未命名选项'
  return DICT_TYPE_LABELS[type.code] || (/^[\x00-\x7F]+$/.test(type.name || '') ? type.code : type.name) || type.code
}

export function getDictItemLabel(item) {
  if (!item) return '未命名选项'
  return DICT_ITEM_LABELS[item.itemKey] || (/^[\x00-\x7F]+$/.test(item.itemLabel || '') ? item.itemKey : item.itemLabel) || item.itemKey
}

export function getAuditModuleLabel(module) {
  return AUDIT_MODULE_LABELS[(module || '').toLowerCase()] || getResourceLabel((module || '').toLowerCase())
}

export function getAuditActionLabel(action) {
  const auditAction = (action || '').toLowerCase()
  if (!auditAction) return '未知操作'
  if (auditAction === 'update') return '更新'
  if (!auditAction.includes('_')) return getActionLabel(auditAction)
  const tokens = auditAction.split('_')
  if (tokens.every(token => AUDIT_TOKEN_LABELS[token])) {
    return tokens.map(token => AUDIT_TOKEN_LABELS[token]).join('')
  }
  return '未识别操作'
}

/**
 * 导航注册收集器：汇总各 navigation/modules/* 模块为统一配置。
 *
 * 各模块导出形如 { public: [...], member: [...], admin: [...] } 的对象，
 * 在此处 import 并按组（group）合并。App.vue 只读这个聚合后的配置，
 * 不再硬编码每条入口。后续业务 PR 只新增 navigation/modules/xxx.js 并在
 * 下方合并表中追加，即可把新入口加入导航。
 */
import commonNav from './modules/common'
import adoptionNav from './modules/adoption'
import boardingNav from './modules/boarding'
import serviceNav from './modules/service'
import trainingNav from './modules/training'
import beautyNav from './modules/beauty'
import medicalNav from './modules/medical'
import communityNav from './modules/community'

/**
 * 取一组的聚合列表：把多个模块里同名组的数组顺序拼接。
 * @param {Array<{public?:*, member?:*, admin?:*>} modules
 * @param {string} group
 */
function collect(modules, group) {
  const items = []
  for (const mod of modules) {
    const entries = mod && mod[group]
    if (Array.isArray(entries)) {
      items.push(...entries)
    }
  }
  return items
}

const modules = [commonNav, adoptionNav, boardingNav, serviceNav, trainingNav, beautyNav, medicalNav, communityNav]

export const publicNav = collect(modules, 'public')
export const memberNav = collect(modules, 'member')
export const adminNav = collect(modules, 'admin')

export default {
  publicNav,
  memberNav,
  adminNav
}

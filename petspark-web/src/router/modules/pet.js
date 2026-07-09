/**
 * 宠物路由模块。宠物目录、我的宠物、宠物健康记录、以及后台宠物管理入口。
 */
export default [
  { path: '/pets', name: 'pets', component: () => import('../../views/PetsView.vue') },
  { path: '/my/pets', name: 'my-pets', component: () => import('../../views/MyPetsView.vue') },
  { path: '/my/pets/:id/health', name: 'pet-health', component: () => import('../../views/PetHealthView.vue') },
  { path: '/admin/pets', name: 'admin-pets', component: () => import('../../views/AdminPetsView.vue') }
]

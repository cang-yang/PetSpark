/**
 * 商品与订单路由模块。商品列表/详情、我的订单、以及后台商品/订单管理入口。
 * 后续新增商品类页面只在本文件追加路由，不修改 router/index.js。
 */
export default [
  { path: '/goods', name: 'goods', component: () => import('../../views/GoodsListView.vue') },
  { path: '/goods/:id', name: 'goods-detail', component: () => import('../../views/GoodsDetailView.vue') },
  { path: '/my/orders', name: 'my-orders', component: () => import('../../views/MyOrdersView.vue') },
  { path: '/admin/goods', name: 'admin-goods', component: () => import('../../views/AdminGoodsView.vue') },
  { path: '/admin/orders', name: 'admin-orders', component: () => import('../../views/AdminOrdersView.vue') }
]

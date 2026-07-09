export default [
  { path: '/community', name: 'community', component: () => import('../../views/CommunityListView.vue') },
  { path: '/community/posts/:id', name: 'community-detail', component: () => import('../../views/CommunityDetailView.vue') },
  { path: '/my/community/posts', name: 'my-community-posts', component: () => import('../../views/MyCommunityPostsView.vue') },
  { path: '/admin/community', name: 'admin-community', component: () => import('../../views/AdminCommunityView.vue') }
]

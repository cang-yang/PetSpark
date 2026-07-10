/**
 * AI 对话路由模块。同意网关、安全网关与宠物对话（PR-AI-02 已落地）。
 * PR-AI-03 新增 /ai/recommend 真实候选智能推荐。
 */
export default [
  { path: '/ai/chat', name: 'ai-chat', component: () => import('../../views/AiChatView.vue') },
  { path: '/ai/recommend', name: 'ai-recommend', component: () => import('../../views/AiRecommendView.vue') }
]

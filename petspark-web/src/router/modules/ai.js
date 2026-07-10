/**
 * AI 对话路由模块。同意网关、安全网关与宠物对话（PR-AI-02 已落地），
 * 真实候选智能推荐（PR-AI-03），以及护理问答探索（PR-AI-04，独立开关 + 硬安全门禁）。
 */
export default [
  { path: '/ai/chat', name: 'ai-chat', component: () => import('../../views/AiChatView.vue') },
  { path: '/ai/recommend', name: 'ai-recommend', component: () => import('../../views/AiRecommendView.vue') },
  { path: '/ai/care-qa', name: 'ai-care-qa', component: () => import('../../views/AiCareQaView.vue') }
]

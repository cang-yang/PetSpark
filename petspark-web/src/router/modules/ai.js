/**
 * AI 对话路由模块。同意网关、安全网关与宠物对话（PR-AI-02 已落地）。
 */
export default [
  { path: '/ai/chat', name: 'ai-chat', component: () => import('../../views/AiChatView.vue') }
]

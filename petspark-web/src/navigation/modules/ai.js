/**
 * AI 导航注册项模块（PR-AI-02 对话 + PR-AI-03 推荐 + PR-AI-04 护理问答）。
 *
 * 将 AI 相关入口集中在此模块，避免散落在 common.js。
 * navigation/index.js 通过 import + collect 聚合，App.vue 只读聚合结果。
 */
export default {
  member: [
    { to: 'ai-chat', text: 'AI 对话', dataTestId: 'nav-ai-chat' },
    { to: 'ai-recommend', text: 'AI 推荐', dataTestId: 'nav-ai-recommend' },
    { to: 'ai-care-qa', text: '护理问答', dataTestId: 'nav-ai-care-qa' }
  ]
}

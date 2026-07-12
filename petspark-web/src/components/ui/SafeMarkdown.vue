<template>
  <div class="safe-markdown" v-html="rendered" />
</template>

<script>
function escapeHtml(value) {
  return String(value || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;')
}

function renderInline(value) {
  return escapeHtml(value)
    .replace(/`([^`\n]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*\n]+)\*\*/g, '<strong>$1</strong>')
}

export function renderSafeMarkdown(content) {
  const lines = String(content || '').replace(/\r\n/g, '\n').split('\n')
  const output = []
  let paragraph = []
  let list = []

  const flushParagraph = () => {
    if (!paragraph.length) return
    output.push(`<p>${paragraph.map(renderInline).join('<br>')}</p>`)
    paragraph = []
  }
  const flushList = () => {
    if (!list.length) return
    output.push(`<ul>${list.map((item) => `<li>${renderInline(item)}</li>`).join('')}</ul>`)
    list = []
  }

  lines.forEach((line) => {
    const item = line.match(/^\s*[-*]\s+(.+)$/)
    if (item) {
      flushParagraph()
      list.push(item[1])
      return
    }
    flushList()
    if (!line.trim()) flushParagraph()
    else paragraph.push(line)
  })
  flushParagraph()
  flushList()
  return output.join('')
}

export default {
  name: 'SafeMarkdown',
  props: { content: { type: String, default: '' } },
  computed: {
    rendered() {
      return renderSafeMarkdown(this.content)
    }
  }
}
</script>

<style scoped>
.safe-markdown { line-height: 1.8; overflow-wrap: anywhere; }
.safe-markdown ::v-deep p { margin: 0 0 10px; }
.safe-markdown ::v-deep p:last-child { margin-bottom: 0; }
.safe-markdown ::v-deep ul { margin: 6px 0 10px; padding-left: 1.35em; }
.safe-markdown ::v-deep li + li { margin-top: 4px; }
.safe-markdown ::v-deep strong { color: #36304e; font-weight: 700; }
.safe-markdown ::v-deep code { padding: 2px 6px; color: #6251a6; background: #f2effc; border-radius: 6px; font-family: Consolas, monospace; font-size: .92em; }
</style>

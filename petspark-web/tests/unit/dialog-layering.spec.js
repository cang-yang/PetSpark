import fs from 'fs'
import path from 'path'

function listVueFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const resolved = path.join(directory, entry.name)
    return entry.isDirectory() ? listVueFiles(resolved) : (entry.name.endsWith('.vue') ? [resolved] : [])
  })
}

describe('Element UI dialog layering', () => {
  it('mounts every dialog at body level so the global mask cannot cover it', () => {
    const sourceRoot = path.resolve(__dirname, '../../src')
    const missing = []
    let dialogCount = 0

    listVueFiles(sourceRoot).forEach(file => {
      const source = fs.readFileSync(file, 'utf8')
      const tags = source.match(/<el-dialog\b[^>]*>/gs) || []
      dialogCount += tags.length
      tags.forEach(tag => {
        if (!/\bappend-to-body\b/.test(tag)) missing.push(path.relative(sourceRoot, file))
      })
    })

    expect(dialogCount).toBe(25)
    expect(missing).toEqual([])
  })
})

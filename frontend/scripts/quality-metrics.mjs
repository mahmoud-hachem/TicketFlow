import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { ESLint } from 'eslint'

const frontend = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const root = path.dirname(frontend)
function files(directory, extensions) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
    const name = path.join(directory, entry.name)
    return entry.isDirectory() ? files(name, extensions)
      : extensions.includes(path.extname(name)) ? [name] : []
  })
}
function lines(name) {
  const source = fs.readFileSync(name, 'utf8')
  return source.split(/\r?\n/).length - Number(source.endsWith('\n'))
}
const scopes = {
  java: files(path.join(root, 'backend/src/main/java'), ['.java']),
  frontend: files(path.join(frontend, 'src'), ['.jsx', '.js', '.css']),
}
const source = Object.fromEntries(Object.entries(scopes).map(([scope, names]) => [scope,
  { files: names.length, physicalLines: names.reduce((sum, name) => sum + lines(name), 0) }]))
const lint = await new ESLint({ cwd: frontend, cache: false }).lintFiles(['.'])
const analysis = await new ESLint({ cwd: frontend, cache: false,
  overrideConfig: { rules: { complexity: ['warn', { max: 0, variant: 'classic' }] } },
}).lintFiles(['src/**/*.{js,jsx}'])
const methods = analysis.flatMap(result => result.messages.filter(message => message.ruleId === 'complexity')
  .map(message => ({ file: path.relative(root, result.filePath), line: message.line,
    complexity: Number(message.message.match(/complexity of (\d+)/)[1]), description: message.message })))
console.log(JSON.stringify({ source,
  app: { physicalLines: lines(path.join(frontend, 'src/App.jsx')),
    stateHooks: (fs.readFileSync(path.join(frontend, 'src/App.jsx'), 'utf8').match(/useState\(/g) || []).length },
  lint: { errors: lint.reduce((sum, result) => sum + result.errorCount, 0),
    warnings: lint.reduce((sum, result) => sum + result.warningCount, 0) },
  complexity: { functions: methods.length, total: methods.reduce((sum, method) => sum + method.complexity, 0),
    max: Math.max(...methods.map(method => method.complexity)), methods },
}, null, 2))

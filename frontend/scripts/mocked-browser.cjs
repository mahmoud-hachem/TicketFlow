/* global require, process */
// Uses an externally available Playwright installation; adds no project dependency.
const assert = require('node:assert/strict')
const http = require('node:http')
const fs = require('node:fs')
const path = require('node:path')
const { chromium } = require(process.env.TICKETFLOW_PLAYWRIGHT_PATH || 'playwright')
const agents = [
  { id: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa', name: 'Agent One' },
  { id: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb', name: 'Agent Two' },
]
const timestamp = '2026-11-26T12:00:00Z'
const makeTicket = (id, title) => ({ id, title, description: 'A support issue', requesterName: 'Student',
  priority: 'MEDIUM', status: 'OPEN', assignedAgent: null, comments: [], version: 0,
  createdAt: timestamp, updatedAt: timestamp })
const tickets = [makeTicket('11111111-1111-4111-8111-111111111111', 'First issue'),
  makeTicket('22222222-2222-4222-8222-222222222222', 'Second issue')]
const createdId = '33333333-3333-4333-8333-333333333333'
let nextFault = null
let holdMutation = false
let holdDetailId = null
let releasePending = null
const writes = []
const checks = []
const record = name => checks.push(name)

async function main() {
  const browser = await chromium.launch({ channel: process.env.TICKETFLOW_BROWSER_CHANNEL || 'msedge', headless: true })
  let server
  try {
    // Serve the production build in the same process boundary as the headless browser.
    let url = process.env.TICKETFLOW_FRONTEND_URL
    if (!url) {
      const dist = path.resolve('dist')
      server = http.createServer((request, response) => {
        const pathname = new URL(request.url, 'http://localhost').pathname
        const file = path.resolve(dist, `.${pathname === '/' ? '/index.html' : pathname}`)
        if (!file.startsWith(dist + path.sep) || !fs.existsSync(file)) {
          response.writeHead(404).end()
          return
        }
        const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.svg': 'image/svg+xml' }
        response.setHeader('Content-Type', types[path.extname(file)] || 'application/octet-stream')
        fs.createReadStream(file).pipe(response)
      })
      await new Promise(resolve => server.listen(0, '127.0.0.1', resolve))
      url = `http://127.0.0.1:${server.address().port}`
    }
    const context = await browser.newContext({ viewport: { width: 375, height: 812 } })
    const page = await context.newPage()
    const errors = []
    page.on('pageerror', error => errors.push(error.message))
    await page.route('**/api/**', async route => {
      const request = route.request()
      const pathname = new URL(request.url()).pathname
      const reply = (status, data) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(data) })
      if (pathname === '/api/agents') return reply(200, agents)
      if (pathname === '/api/tickets' && request.method() === 'GET') return reply(200, tickets)
      if (pathname === '/api/tickets' && request.method() === 'POST') {
        const ticket = { ...makeTicket(createdId, ''), ...request.postDataJSON() }
        tickets.unshift(ticket)
        return reply(201, ticket)
      }
      const [, id, operation] = pathname.match(/^\/api\/tickets\/([^/]+)(?:\/(.*))?$/) || []
      const ticket = tickets.find(item => item.id === id)
      if (!ticket) return reply(404, { errorCode: 'TICKET_NOT_FOUND', detail: 'Ticket not found' })
      if (request.method() === 'GET') {
        const snapshot = structuredClone(ticket)
        if (holdDetailId === id) {
          holdDetailId = null
          await new Promise(resolve => { releasePending = resolve })
        }
        return reply(200, snapshot)
      }
      const body = request.postDataJSON()
      writes.push({ id, operation, body })
      assert.equal(body.expectedVersion, ticket.version, 'UI must send the latest persisted version')
      if (holdMutation) {
        holdMutation = false
        await new Promise(resolve => { releasePending = resolve })
      }
      if (nextFault) {
        const fault = nextFault
        nextFault = null
        if (fault === 'network') return route.abort('failed')
        if (fault === 'malformed') return reply(200, { id: ticket.id, version: ticket.version })
        if (fault === 'validation') return reply(400, { errorCode: 'VALIDATION_ERROR',
          fieldErrors: { content: 'Server content validation' }, detail: 'Invalid input' })
        ticket.version++
        return reply(409, { errorCode: 'VERSION_CONFLICT', detail: 'Reload required' })
      }
      assert.notEqual(ticket.status, 'CLOSED', 'CLOSED controls must prevent requests')
      ticket.version++
      ticket.updatedAt = new Date(Date.parse(timestamp) + ticket.version * 1000).toISOString()
      if (operation === 'assignment') ticket.assignedAgent = agents.find(agent => agent.id === body.agentId)
      if (operation === 'status') ticket.status = body.status
      if (operation === 'comments') {
        const comment = { id: `comment-${ticket.version}`, authorName: body.authorName,
          content: body.content, createdAt: ticket.updatedAt }
        ticket.comments.push(comment)
        return reply(201, { ticketId: id, comment, ticketVersion: ticket.version, updatedAt: ticket.updatedAt })
      }
      return reply(200, ticket)
    })
    const row = title => page.locator('.ticket-list > article').filter({
      has: page.getByRole('heading', { name: title, exact: true }),
    })
    async function view(title) {
      await row(title).getByRole('button', { name: 'View Details', exact: true }).click()
      await page.getByLabel('Comment author').waitFor()
    }
    async function version(value) {
      await page.getByText(`Version: ${value}`, { exact: true }).waitFor()
    }
    async function reload() {
      await page.getByRole('button', { name: 'Reload Ticket', exact: true }).click()
      await page.getByLabel('Comment author').waitFor()
    }
    async function waitForPending() {
      await page.waitForFunction(() => document.querySelector('form[aria-busy="true"]'))
      await waitForHeldResponse()
    }
    async function waitForHeldResponse() {
      const deadline = Date.now() + 5000
      while (!releasePending) {
        assert.ok(Date.now() < deadline, 'Expected a held mock response within five seconds')
        await new Promise(resolve => setTimeout(resolve, 10))
      }
    }
    async function release() {
      const resolve = releasePending
      releasePending = null
      resolve()
    }
    const add = () => page.getByRole('button', { name: 'Add Comment', exact: true })
    const assign = () => page.getByRole('button', { name: /^(Assign|Reassign) Agent$/ })
    const update = () => page.getByRole('button', { name: 'Update Status', exact: true })
    async function commentDraft(content = 'First comment') {
      await page.getByLabel('Comment author').fill('Student')
      await page.getByLabel('Comment content').fill(content)
    }
    await page.goto(url)
    await row('First issue').waitFor()
    record('ticket list')
    await page.getByLabel('Title', { exact: true }).fill('Created issue')
    await page.getByLabel('Description', { exact: true }).fill('Created description')
    await page.getByLabel('Requester name', { exact: true }).fill('Student')
    await page.getByRole('button', { name: 'Create Ticket', exact: true }).click()
    await row('Created issue').waitFor()
    record('creation and list refresh')
    await view('Created issue')
    await version(0)
    assert.equal(await update().isDisabled(), true)
    record('details and assignee-required status control')
    await page.getByLabel('Support agent', { exact: true }).selectOption(agents[0].id)
    await assign().click()
    await version(1)
    assert.match(await row('Created issue').innerText(), /Agent One/)
    await page.getByLabel('Support agent', { exact: true }).selectOption(agents[1].id)
    await assign().click()
    await version(2)
    assert.match(await row('Created issue').innerText(), /Agent Two/)
    record('assignment, reassignment and list/detail synchronization')
    await update().click()
    await version(3)
    assert.equal(tickets[0].status, 'IN_PROGRESS')
    record('status transition')
    await commentDraft()
    await add().click()
    await version(4)
    await page.getByText('First comment', { exact: true }).waitFor()
    assert.equal(await page.getByLabel('Comment content').inputValue(), '')
    record('comment creation and draft clearing')
    await commentDraft('Preserved draft')
    nextFault = 'validation'
    await add().click()
    await page.getByText('Server content validation', { exact: true }).waitFor()
    assert.equal(await add().isEnabled(), true)
    record('API validation feedback and retry')
    nextFault = 'conflict'
    await add().click()
    await page.getByText(/This ticket changed.*review the comments/).waitFor()
    assert.equal(await add().isDisabled(), true)
    assert.equal(await assign().isDisabled(), true)
    const countBeforeReload = writes.length
    await reload()
    await version(5)
    assert.equal(await page.getByLabel('Comment content').inputValue(), 'Preserved draft')
    assert.equal(writes.length, countBeforeReload, 'Reload must not automatically retry')
    await add().click()
    await version(6)
    assert.equal(tickets[0].comments.length, 2)
    record('conflict reload, draft preservation and manual retry')
    await commentDraft('Network draft')
    nextFault = 'network'
    await add().click()
    await page.getByText(/comment outcome could not be confirmed/).waitFor()
    assert.equal(await add().isDisabled(), true)
    await reload()
    assert.equal(await page.getByLabel('Comment content').inputValue(), 'Network draft')
    record('network ambiguity requires reload and preserves draft')
    nextFault = 'malformed'
    await add().click()
    await page.getByText(/comment outcome could not be confirmed/).waitFor()
    await reload()
    record('malformed success response rejected')
    holdMutation = true
    await add().click()
    await waitForPending()
    assert.equal(await assign().isDisabled(), true)
    assert.equal(await update().isDisabled(), true)
    await view('Second issue')
    await release()
    await version(0)
    assert.equal(await page.getByLabel('Comment content').inputValue(), '')
    assert.equal(await page.getByText('Network draft', { exact: true }).count(), 0)
    record('shared lock and late mutation response selection race')
    await page.getByRole('button', { name: 'Close Details', exact: true }).click()
    holdDetailId = tickets[1].id
    await row('First issue').getByRole('button', { name: 'View Details', exact: true }).click()
    await page.getByText('Loading ticket details...', { exact: true }).waitFor()
    await waitForHeldResponse()
    await view('Second issue')
    await release()
    await version(0)
    record('late detail response selection race')
    await view('Created issue')
    await version(tickets[0].version)
    const resolvedVersion = tickets[0].version + 1
    await update().click()
    await version(resolvedVersion)
    await page.getByLabel('Next status').selectOption('IN_PROGRESS')
    await update().click()
    await page.getByText('Status updated to IN_PROGRESS.', { exact: true }).waitFor()
    await update().click()
    await page.getByText('Status updated to RESOLVED.', { exact: true }).waitFor()
    await page.getByLabel('Next status').selectOption('CLOSED')
    await update().click()
    await page.getByText('Status updated to CLOSED.', { exact: true }).waitFor()
    assert.equal(await assign().isDisabled(), true)
    assert.equal(await update().isDisabled(), true)
    assert.equal(await add().isDisabled(), true)
    assert.equal(tickets[0].comments.length, 3)
    record('reopening, comment preservation and CLOSED immutability')
    assert.equal(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth), true)
    assert.deepEqual(errors, [])
    record('375px overflow and browser runtime errors')
    await view('First issue')
    await page.getByLabel('Support agent', { exact: true }).selectOption(agents[0].id)
    nextFault = 'conflict'
    await assign().click()
    await page.getByText(/This ticket changed.*latest assignment/).waitFor()
    assert.equal(await assign().isDisabled(), true)
    await reload()
    await version(1)
    await page.getByLabel('Support agent', { exact: true }).selectOption(agents[0].id)
    await assign().click()
    await version(2)
    record('assignment conflict recovery and manual retry')
    nextFault = 'conflict'
    await update().click()
    await page.getByText(/This ticket changed.*latest status/).waitFor()
    assert.equal(await update().isDisabled(), true)
    await reload()
    await version(3)
    await update().click()
    await version(4)
    record('status conflict recovery and manual retry')
    assert.deepEqual(errors, [])
    console.log(JSON.stringify({ passed: checks.length, checks, mutationRequests: writes.length }, null, 2))
    await context.close()
  } finally {
    await browser.close()
    if (server) await new Promise(resolve => server.close(resolve))
  }
}
main().catch(error => { console.error(error); process.exitCode = 1 })

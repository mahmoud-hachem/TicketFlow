import { useEffect, useRef, useState } from 'react'

const transitions = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'IN_PROGRESS'],
  CLOSED: []
}

function TicketStatus({ ticket, workflow, onBegin, onEnd, onUpdated }) {
  const [choice, setChoice] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [success, setSuccess] = useState('')
  const mutationRef = useRef(null)
  useEffect(() => () => mutationRef.current?.abort(), [])

  const options = (transitions[ticket.status] ?? [])
    .filter(status => status !== 'IN_PROGRESS' || Boolean(ticket.assignedAgent))
  const next = options.includes(choice) ? choice : options[0] ?? ''
  const validVersion = Number.isSafeInteger(ticket.version) && ticket.version >= 0
  const blocked = workflow.busy || workflow.reload || !validVersion || !next

  async function update(event) {
    event.preventDefault()
    if (mutationRef.current || blocked || !onBegin()) return
    const controller = new AbortController()
    mutationRef.current = controller
    let reloadNeeded = false
    setSubmitting(true)
    setError('')
    setSuccess('')
    try {
      const response = await fetch(`/api/tickets/${encodeURIComponent(ticket.id)}/status`, {
        method: 'PATCH', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ status: next, expectedVersion: ticket.version }),
        signal: controller.signal
      })
      const data = await response.json().catch(() => null)
      if (controller.signal.aborted || mutationRef.current !== controller) return
      if (!response.ok) {
        reloadNeeded = response.status >= 500 || response.status === 409 ||
          data?.errorCode === 'TICKET_NOT_FOUND' || Boolean(data?.fieldErrors?.expectedVersion)
        const fields = data?.fieldErrors && typeof data.fieldErrors === 'object'
          ? Object.values(data.fieldErrors).filter(value => typeof value === 'string').join(' ') : ''
        setError(data?.errorCode === 'VERSION_CONFLICT'
          ? 'This ticket changed. Reload the ticket, review its latest status, then submit again.'
          : fields || (typeof data?.detail === 'string' ? data.detail : 'Unable to update ticket status.'))
        return
      }
      if (data?.id !== ticket.id || data.status !== next || !Number.isSafeInteger(data.version) ||
        data.version <= ticket.version || !Array.isArray(data.comments)) throw new Error('Unconfirmed status response')
      onUpdated(data)
      setSuccess(`Status updated to ${data.status}.`)
    } catch {
      if (!controller.signal.aborted) {
        reloadNeeded = true
        setError('The status update outcome could not be confirmed. Reload this ticket before trying again.')
      }
    } finally {
      if (mutationRef.current === controller) {
        mutationRef.current = null
        if (!controller.signal.aborted) setSubmitting(false)
        onEnd(reloadNeeded)
      }
    }
  }

  return (
    <section className="assignment-panel" aria-label="Ticket status update">
      <h3>Update Status</h3>
      {ticket.status === 'CLOSED' && <p>Closed tickets cannot change status.</p>}
      {ticket.status === 'OPEN' && !ticket.assignedAgent && <p>Assign a support agent before starting work.</p>}
      {error && <p className="error-message" role="alert">{error}</p>}
      {success && <p className="success-message" role="status">{success}</p>}
      {workflow.reload && <p>Reload the ticket before another status update.</p>}
      <form className="assignment-form" onSubmit={update} aria-busy={submitting}>
        <label htmlFor={`next-status-${ticket.id}`}>Next status</label>
        <select id={`next-status-${ticket.id}`} value={next} disabled={blocked}
          onChange={event => { setChoice(event.target.value); setError(''); setSuccess('') }}>
          {!options.length && <option value="">No status changes available</option>}
          {options.map(status => <option key={status} value={status}>{status}</option>)}
        </select>
        <div className="assignment-controls">
          <button type="submit" disabled={blocked}>{submitting ? 'Updating status...' : 'Update Status'}</button>
        </div>
      </form>
    </section>
  )
}

export default TicketStatus

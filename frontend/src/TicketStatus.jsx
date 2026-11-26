import { useState } from 'react'
import useTicketMutation from './useTicketMutation.js'

const transitions = {
  OPEN: ['IN_PROGRESS'],
  IN_PROGRESS: ['RESOLVED'],
  RESOLVED: ['CLOSED', 'IN_PROGRESS'],
  CLOSED: []
}

function TicketStatus({ ticket, workflow, onBegin, onEnd, onUpdated }) {
  const [choice, setChoice] = useState('')
  const { submitting, error, success, clearFeedback, run } = useTicketMutation({ onBegin, onEnd })

  const options = (transitions[ticket.status] ?? [])
    .filter(status => status !== 'IN_PROGRESS' || Boolean(ticket.assignedAgent))
  const next = options.includes(choice) ? choice : options[0] ?? ''
  const validVersion = Number.isSafeInteger(ticket.version) && ticket.version >= 0
  const blocked = workflow.busy || workflow.reload || !validVersion || !next

  async function update(event) {
    event.preventDefault()
    if (blocked) return
    await run({
      url: `/api/tickets/${encodeURIComponent(ticket.id)}/status`, method: 'PATCH',
      body: { status: next, expectedVersion: ticket.version }, showFieldErrors: true,
      validateResponse: data => data?.id === ticket.id && data.status === next &&
        Number.isSafeInteger(data.version) && data.version > ticket.version && Array.isArray(data.comments),
      onSuccess: data => {
        onUpdated(data)
        return `Status updated to ${data.status}.`
      },
      messages: {
        conflict: 'This ticket changed. Reload the ticket, review its latest status, then submit again.',
        failure: 'Unable to update ticket status.',
        unconfirmed: 'The status update outcome could not be confirmed. Reload this ticket before trying again.',
      },
    })
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
          onChange={event => { setChoice(event.target.value); clearFeedback() }}>
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

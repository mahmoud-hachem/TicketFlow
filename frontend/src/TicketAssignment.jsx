import { useEffect, useState } from 'react'
import useTicketMutation from './useTicketMutation.js'

function TicketAssignment({ ticket, onAssigned, onReload, workflow, onBegin, onEnd }) {
  const [agents, setAgents] = useState([])
  const [agentsLoading, setAgentsLoading] = useState(true)
  const [agentsError, setAgentsError] = useState('')
  const [agentsRefresh, setAgentsRefresh] = useState(0)
  const [agentId, setAgentId] = useState(ticket.assignedAgent?.id ?? '')
  const { submitting, error, fieldErrors, requiresReload, success,
    clearFeedback, setFieldErrors, run } = useTicketMutation({ onBegin, onEnd })

  useEffect(() => {
    const controller = new AbortController()

    async function loadAgents() {
      setAgentsLoading(true)
      setAgentsError('')
      try {
        const response = await fetch('/api/agents', { signal: controller.signal })
        const data = await response.json().catch(() => null)
        if (!response.ok) {
          throw new Error(typeof data?.detail === 'string'
            ? data.detail : 'Unable to load available agents.')
        }
        if (!Array.isArray(data) || data.some(agent => !agent ||
          typeof agent.id !== 'string' || typeof agent.name !== 'string')) {
          throw new Error('Unable to read the available agents.')
        }
        if (!controller.signal.aborted) setAgents(data)
      } catch (err) {
        if (!controller.signal.aborted) setAgentsError(err.message)
      } finally {
        if (!controller.signal.aborted) setAgentsLoading(false)
      }
    }

    loadAgents()
    return () => controller.abort()
  }, [agentsRefresh])

  const closed = ticket.status === 'CLOSED'
  const validVersion = Number.isSafeInteger(ticket.version) && ticket.version >= 0
  const availableSelection = agents.some(agent => agent.id === agentId)
  const blocked = closed || submitting || workflow.busy || workflow.reload || agentsLoading || Boolean(agentsError) ||
    requiresReload || !validVersion

  async function assign(event) {
    event.preventDefault()
    if (blocked) return
    if (!availableSelection) {
      setFieldErrors({ agentId: 'Select an available support agent.' })
      return
    }
    await run({
      url: `/api/tickets/${encodeURIComponent(ticket.id)}/assignment`, method: 'PATCH',
      body: { agentId, expectedVersion: ticket.version },
      reloadOnError: (response, data) => response.status >= 500 ||
        (response.status === 409 && data?.errorCode === 'VERSION_CONFLICT') ||
        ['TICKET_CLOSED', 'TICKET_NOT_FOUND'].includes(data?.errorCode) || Boolean(data?.fieldErrors?.expectedVersion),
      validateResponse: data => data?.id === ticket.id && Number.isSafeInteger(data.version) &&
        data.version > ticket.version && data.assignedAgent?.id === agentId &&
        typeof data.assignedAgent?.name === 'string' && Array.isArray(data.comments),
      onSuccess: data => {
        onAssigned(data)
        return `Assigned to ${data.assignedAgent.name}.`
      },
      messages: {
        conflict: 'This ticket changed. Reload the ticket, review the latest assignment, then submit again.',
        failure: 'Unable to update the assignment.',
        unconfirmed: 'The assignment outcome could not be confirmed. Reload this ticket before trying again.',
      },
    })
  }

  const fieldId = `assignment-agent-${ticket.id}`

  return (
    <section className="assignment-panel" aria-label="Agent assignment">
      <h3>Agent Assignment</h3>
      {closed && <p>Closed tickets cannot be assigned or reassigned.</p>}
      {agentsLoading && <p role="status">Loading support agents...</p>}
      {agentsError && <p className="error-message" role="alert">{agentsError}</p>}
      {!agentsLoading && !agentsError && agents.length === 0 && (
        <p>No support agents are available.</p>
      )}
      {error && <p className="error-message" role="alert">{error}</p>}
      {success && <p className="success-message" role="status">{success}</p>}
      {!validVersion && <p role="alert">Reload this ticket to obtain a valid version before assigning.</p>}

      <form className="assignment-form" onSubmit={assign} aria-busy={submitting}>
        <label htmlFor={fieldId}>Support agent</label>
        <select
          id={fieldId}
          value={agentId}
          disabled={blocked || agents.length === 0}
          aria-invalid={Boolean(fieldErrors.agentId)}
          aria-describedby={fieldErrors.agentId ? `${fieldId}-error` : undefined}
          onChange={event => {
            setAgentId(event.target.value)
            clearFeedback()
          }}
        >
          <option value="">Select an agent</option>
          {agentId && !availableSelection && <option value={agentId} disabled>Current selection unavailable</option>}
          {agents.map(agent => <option key={agent.id} value={agent.id}>{agent.name}</option>)}
        </select>
        {fieldErrors.agentId && <small id={`${fieldId}-error`} className="field-error">{fieldErrors.agentId}</small>}
        {fieldErrors.expectedVersion && <small className="field-error">{fieldErrors.expectedVersion}</small>}
        <div className="assignment-controls">
          <button type="submit" disabled={blocked || !availableSelection}>
            {submitting ? 'Saving assignment...' : ticket.assignedAgent ? 'Reassign Agent' : 'Assign Agent'}
          </button>
          <button type="button" disabled={submitting || agentsLoading}
            onClick={() => setAgentsRefresh(previous => previous + 1)}>
            Reload Agents
          </button>
          {(requiresReload || workflow.reload || !validVersion) && (
            <button type="button" disabled={submitting} onClick={onReload}>Reload Ticket</button>
          )}
        </div>
      </form>
    </section>
  )
}

export default TicketAssignment

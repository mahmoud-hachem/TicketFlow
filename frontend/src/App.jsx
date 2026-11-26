
import { useEffect, useRef, useState } from 'react'
import TicketAssignment from './TicketAssignment.jsx'
import TicketStatus from './TicketStatus.jsx'
import TicketComments from './TicketComments.jsx'
import './App.css'

const initialForm = {
  title: '',
  description: '',
  requesterName: '',
  priority: 'MEDIUM'
}

function App() {
  const [form, setForm] = useState(initialForm)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [createdTicket, setCreatedTicket] = useState(null)

  const [tickets, setTickets] = useState([])
  const [listLoading, setListLoading] = useState(true)
  const [listError, setListError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)

  const [selection, setSelection] = useState(null)
  const selectedId = selection?.id ?? null
  const selectionRef = useRef(null)
  const listRequestRef = useRef(null)
  const detailRequestRef = useRef(null)
  const workflowRef = useRef({ busy: false, reload: false })
  const [workflow, setWorkflow] = useState({ busy: false, reload: false })
  const [commentDraft, setCommentDraft] = useState({ ticketId: null, authorName: '', content: '' })
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')

  useEffect(() => {
    const controller = new AbortController()
    listRequestRef.current = controller

    async function loadTickets() {
      setListLoading(true)
      setListError('')

      try {
        const response = await fetch('/api/tickets', {
          signal: controller.signal
        })

        if (!response.ok) {
          throw new Error('Unable to load tickets.')
        }

        const data = await response.json()
        if (!controller.signal.aborted) {
          setTickets(data)
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setListError(err.message)
        }
      } finally {
        if (!controller.signal.aborted) {
          setListLoading(false)
        }
      }
    }

    loadTickets()

    return () => controller.abort()
  }, [refreshKey])

  useEffect(() => {
    if (!selection) return

    const controller = new AbortController()
    detailRequestRef.current = controller
    const isCurrent = () => !controller.signal.aborted && selectionRef.current === selection

    async function loadDetails() {
      setDetailLoading(true)
      setDetailError('')
      setSelectedTicket(null)

      try {
        const response = await fetch(
          `/api/tickets/${encodeURIComponent(selection.id)}`,
          { signal: controller.signal }
        )

        if (response.status === 404) {
          throw new Error('Ticket not found.')
        }

        if (!response.ok) {
          throw new Error('Unable to load ticket details.')
        }

        const data = await response.json()
        if (isCurrent()) {
          setSelectedTicket(data)
        }

      } catch (err) {
        if (isCurrent()) {
          setDetailError(err.message)
        }
      } finally {
        if (isCurrent()) {
          setDetailLoading(false)
        }
      }
    }

    loadDetails()

    return () => controller.abort()
  }, [selection])

  function updateWorkflow(next) {
    workflowRef.current = next
    setWorkflow(next)
  }

  function viewDetails(id) {
    setCommentDraft(previous => previous.ticketId === id
      ? previous : { ticketId: id, authorName: '', content: '' })
    const nextSelection = { id }
    selectionRef.current = nextSelection
    updateWorkflow({ busy: false, reload: false })
    detailRequestRef.current?.abort()
    setSelectedTicket(null)
    setDetailError('')
    setDetailLoading(true)
    setSelection(nextSelection)
  }

  function closeDetails() {
    selectionRef.current = null
    updateWorkflow({ busy: false, reload: false })
    detailRequestRef.current?.abort()
    setSelection(null)
    setSelectedTicket(null)
    setDetailError('')
    setDetailLoading(false)
  }

  function beginMutation(sourceSelection) {
    if (selectionRef.current !== sourceSelection || workflowRef.current.busy || workflowRef.current.reload) return false
    updateWorkflow({ ...workflowRef.current, busy: true })
    return true
  }

  function endMutation(sourceSelection, reload) {
    if (selectionRef.current !== sourceSelection) return
    updateWorkflow({ busy: false, reload: workflowRef.current.reload || reload })
  }

  function handleTicketUpdated(updated, sourceSelection) {
    if (selectionRef.current !== sourceSelection) return

    // A read started before this successful mutation must not restore older data.
    listRequestRef.current?.abort()
    detailRequestRef.current?.abort()
    setSelectedTicket(updated)
    setDetailLoading(false)
    setDetailError('')
    setListLoading(false)
    setListError('')
    setTickets(previous => previous.map(ticket => ticket.id === updated.id
      ? {
          ...ticket,
          title: updated.title,
          requesterName: updated.requesterName,
          status: updated.status,
          priority: updated.priority,
          assignedAgent: updated.assignedAgent,
          createdAt: updated.createdAt,
          updatedAt: updated.updatedAt,
          version: updated.version
        }
      : ticket))
  }

  function handleCommentCreated(response, sourceSelection) {
    if (selectionRef.current !== sourceSelection) return
    const comments = [...selectedTicket.comments.filter(comment => comment.id !== response.comment.id), response.comment]
      .sort((left, right) => Date.parse(left.createdAt) - Date.parse(right.createdAt) ||
        (left.id < right.id ? -1 : left.id > right.id ? 1 : 0))
    handleTicketUpdated({ ...selectedTicket, comments, version: response.ticketVersion,
      updatedAt: response.updatedAt }, sourceSelection)
  }

  function handleChange(event) {
    const { name, value } = event.target

    setForm(previous => ({
      ...previous,
      [name]: value
    }))
  }

  async function handleSubmit(event) {
    event.preventDefault()

    if (loading) return

    setLoading(true)
    setError('')
    setFieldErrors({})
    setCreatedTicket(null)

    try {
      const response = await fetch('/api/tickets', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          ...form,
          title: form.title.trim(),
          description: form.description.trim(),
          requesterName: form.requesterName.trim()
        })
      })

      const data = await response.json()

      if (!response.ok) {
        setFieldErrors(data.fieldErrors || {})
        throw new Error(
          data.detail || 'Unable to create ticket'
        )
      }

      setCreatedTicket(data)
      setForm(initialForm)
      setRefreshKey(previous => previous + 1)

    } catch (err) {
      setError(err.message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="app">
      <header className="header">
        <h1>TicketFlow</h1>
        <p>Support Ticket Management</p>
      </header>

      <main className="container">

        <section className="ticket-card">
          <h2>Create Support Ticket</h2>
          <p>Report an issue and request assistance.</p>

          {error && (
            <div className="error-message" role="alert">
              {error}
            </div>
          )}

          {createdTicket && (
            <div className="success-message" role="status">
              <strong>Ticket created successfully!</strong>
              <p>Ticket ID: {createdTicket.id}</p>
              <p>Status: {createdTicket.status}</p>
            </div>
          )}

          <form onSubmit={handleSubmit}>

            <label htmlFor="title">Title</label>
            <input
              id="title"
              name="title"
              value={form.title}
              onChange={handleChange}
              placeholder="Enter ticket title"
              required
            />

            {fieldErrors.title && (
              <small className="field-error">
                {fieldErrors.title}
              </small>
            )}

            <label htmlFor="description">Description</label>
            <textarea
              id="description"
              name="description"
              value={form.description}
              onChange={handleChange}
              placeholder="Describe your issue"
              required
            />

            {fieldErrors.description && (
              <small className="field-error">
                {fieldErrors.description}
              </small>
            )}

            <label htmlFor="requesterName">
              Requester name
            </label>

            <input
              id="requesterName"
              name="requesterName"
              value={form.requesterName}
              onChange={handleChange}
              placeholder="Enter your name"
              required
            />

            {fieldErrors.requesterName && (
              <small className="field-error">
                {fieldErrors.requesterName}
              </small>
            )}

            <label htmlFor="priority">Priority</label>

            <select
              id="priority"
              name="priority"
              value={form.priority}
              onChange={handleChange}
              required
            >
              <option value="LOW">Low</option>
              <option value="MEDIUM">Medium</option>
              <option value="HIGH">High</option>
            </select>

            {fieldErrors.priority && (
              <small className="field-error">
                {fieldErrors.priority}
              </small>
            )}

            <button type="submit" disabled={loading}>
              {loading ? 'Creating ticket...' : 'Create Ticket'}
            </button>

          </form>
        </section>

        <section className="ticket-card ticket-list">
          <h2>All Support Tickets</h2>
          <p>View and track submitted support tickets.</p>

          <button
            type="button"
            onClick={() =>
              setRefreshKey(previous => previous + 1)
            }
            disabled={listLoading}
          >
            Refresh Tickets
          </button>

          {listLoading && (
            <p role="status">Loading tickets...</p>
          )}

          {listError && (
            <div className="error-message" role="alert">
              {listError}
            </div>
          )}

          {!listLoading && !listError && tickets.length === 0 && (
            <p>No tickets yet. Create your first support ticket.</p>
          )}

          {tickets.map(ticket => (
            <article className="ticket-item" key={ticket.id}>

              <h3>{ticket.title}</h3>

              <div className="ticket-badges">
                <span className="ticket-badge">
                  {ticket.status}
                </span>

                <span className={`ticket-badge priority-${ticket.priority.toLowerCase()}`}>
                  {ticket.priority}
                </span>
              </div>

              <p>
                <strong>Requester:</strong>{' '}
                {ticket.requesterName}
              </p>

              <p>
                <strong>Assigned agent:</strong>{' '}
                {ticket.assignedAgent?.name || 'Unassigned'}
              </p>

              <p>
                <strong>Created:</strong>{' '}
                {new Date(ticket.createdAt).toLocaleString()}
              </p>

              <button
                type="button"
                onClick={() => viewDetails(ticket.id)}
                aria-expanded={selectedId === ticket.id}
              >
                View Details
              </button>

              {selectedId === ticket.id && (
                <section className="ticket-item">

                  <h3>Ticket Details</h3>

                  {detailLoading && (
                    <p role="status">Loading ticket details...</p>
                  )}

                  {detailError && (
                    <div className="error-message" role="alert">
                      {detailError}
                      <button type="button" onClick={() => viewDetails(selectedId)}>
                        Reload Ticket
                      </button>
                    </div>
                  )}

                  {selectedTicket && (
                    <>
                      <p>
                        <strong>Ticket ID:</strong>{' '}
                        {selectedTicket.id}
                      </p>

                      <p>
                        <strong>Title:</strong>{' '}
                        {selectedTicket.title}
                      </p>

                      <p><strong>Description:</strong></p>
                      <p style={{ whiteSpace: 'pre-wrap' }}>
                        {selectedTicket.description}
                      </p>

                      <p>
                        <strong>Requester:</strong>{' '}
                        {selectedTicket.requesterName}
                      </p>

                      <p>
                        <strong>Status:</strong>{' '}
                        {selectedTicket.status}
                      </p>

                      <p>
                        <strong>Priority:</strong>{' '}
                        {selectedTicket.priority}
                      </p>

                      <p>
                        <strong>Assigned agent:</strong>{' '}
                        {selectedTicket.assignedAgent?.name || 'Unassigned'}
                      </p>

                      <p>
                        <strong>Created:</strong>{' '}
                        {new Date(selectedTicket.createdAt).toLocaleString()}
                      </p>

                      <p>
                        <strong>Last updated:</strong>{' '}
                        {new Date(selectedTicket.updatedAt).toLocaleString()}
                      </p>

                      <p>
                        <strong>Version:</strong>{' '}
                        {selectedTicket.version}
                      </p>

                      <TicketAssignment
                        key={selectedTicket.id}
                        ticket={selectedTicket}
                        onAssigned={updated => handleTicketUpdated(updated, selection)}
                        onReload={() => viewDetails(selectedId)}
                        workflow={workflow}
                        onBegin={() => beginMutation(selection)}
                        onEnd={reload => endMutation(selection, reload)}
                      />

                      <TicketStatus
                        key={`status-${selectedTicket.id}`}
                        ticket={selectedTicket}
                        workflow={workflow}
                        onBegin={() => beginMutation(selection)}
                        onEnd={reload => endMutation(selection, reload)}
                        onUpdated={updated => handleTicketUpdated(updated, selection)}
                      />

                      <hr />

                      <h3>Comments</h3>

                      <TicketComments
                        key={`comments-${selectedTicket.id}`}
                        ticket={selectedTicket}
                        workflow={workflow}
                        draft={commentDraft}
                        onDraftChange={draft => {
                          if (selectionRef.current === selection) setCommentDraft({ ticketId: selectedId, ...draft })
                        }}
                        onBegin={() => beginMutation(selection)}
                        onEnd={reload => endMutation(selection, reload)}
                        onCreated={response => handleCommentCreated(response, selection)}
                      />

                      {!selectedTicket.comments?.length && (
                        <p>No comments yet.</p>
                      )}

                      {selectedTicket.comments?.map(comment => (
                        <article
                          className="ticket-item"
                          key={comment.id}
                        >
                          <strong>{comment.authorName}</strong>
                          <p style={{ whiteSpace: 'pre-wrap' }}>{comment.content}</p>
                          <small>
                            {new Date(comment.createdAt).toLocaleString()}
                          </small>
                        </article>
                      ))}
                    </>
                  )}

                  <button
                    type="button"
                    onClick={closeDetails}
                  >
                    Close Details
                  </button>

                </section>
              )}

            </article>
          ))}

        </section>

      </main>
    </div>
  )
}

export default App

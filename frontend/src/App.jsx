
import { useEffect, useState } from 'react'
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

  const [selectedId, setSelectedId] = useState(null)
  const [selectedTicket, setSelectedTicket] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detailError, setDetailError] = useState('')
  const [detailRefresh, setDetailRefresh] = useState(0)

  useEffect(() => {
    const controller = new AbortController()

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
        setTickets(data)
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
    if (!selectedId) return

    const controller = new AbortController()

    async function loadDetails() {
      setDetailLoading(true)
      setDetailError('')
      setSelectedTicket(null)

      try {
        const response = await fetch(
          `/api/tickets/${encodeURIComponent(selectedId)}`,
          { signal: controller.signal }
        )

        if (response.status === 404) {
          throw new Error('Ticket not found.')
        }

        if (!response.ok) {
          throw new Error('Unable to load ticket details.')
        }

        const data = await response.json()
        setSelectedTicket(data)

      } catch (err) {
        if (!controller.signal.aborted) {
          setDetailError(err.message)
        }
      } finally {
        if (!controller.signal.aborted) {
          setDetailLoading(false)
        }
      }
    }

    loadDetails()

    return () => controller.abort()
  }, [selectedId, detailRefresh])

  function viewDetails(id) {
    setSelectedTicket(null)
    setDetailError('')
    setDetailLoading(true)
    setSelectedId(id)
    setDetailRefresh(previous => previous + 1)
  }

  function closeDetails() {
    setSelectedId(null)
    setSelectedTicket(null)
    setDetailError('')
    setDetailLoading(false)
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

          {!listLoading && !listError && tickets.map(ticket => (
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

                      <hr />

                      <h3>Comments</h3>

                      {!selectedTicket.comments?.length && (
                        <p>No comments yet.</p>
                      )}

                      {selectedTicket.comments?.map(comment => (
                        <article
                          className="ticket-item"
                          key={comment.id}
                        >
                          <strong>{comment.authorName}</strong>
                          <p>{comment.content}</p>
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

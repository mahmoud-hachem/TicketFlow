import { useEffect, useRef, useState } from 'react'

function TicketComments({ ticket, workflow, draft, onDraftChange, onBegin, onEnd, onCreated }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [success, setSuccess] = useState('')
  const mutationRef = useRef(null)
  useEffect(() => () => mutationRef.current?.abort(), [])
  const blocked = ticket.status === 'CLOSED' || workflow.busy || workflow.reload ||
    !Number.isSafeInteger(ticket.version) || ticket.version < 0

  async function submit(event) {
    event.preventDefault()
    if (blocked || mutationRef.current) return
    const authorName = draft.authorName.trim()
    const content = draft.content.trim()
    const fields = {}
    if (!authorName || authorName.length > 100) fields.authorName = 'Enter an author name of 1 to 100 characters.'
    if (!content || content.length > 2000) fields.content = 'Enter a comment of 1 to 2000 characters.'
    setFieldErrors(fields)
    setError('')
    setSuccess('')
    if (Object.keys(fields).length || !onBegin()) return
    const controller = new AbortController()
    mutationRef.current = controller
    let reloadNeeded = false
    setSubmitting(true)
    try {
      const response = await fetch(`/api/tickets/${encodeURIComponent(ticket.id)}/comments`, {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ authorName, content, expectedVersion: ticket.version }),
        signal: controller.signal
      })
      const data = await response.json().catch(() => null)
      if (controller.signal.aborted || mutationRef.current !== controller) return
      if (!response.ok) {
        const errors = data?.fieldErrors
        setFieldErrors(errors && typeof errors === 'object'
          ? Object.fromEntries(Object.entries(errors).filter(([, value]) => typeof value === 'string')) : {})
        reloadNeeded = response.status >= 500 || response.status === 409 ||
          data?.errorCode === 'TICKET_NOT_FOUND' || Boolean(errors?.expectedVersion)
        setError(data?.errorCode === 'VERSION_CONFLICT'
          ? 'This ticket changed. Reload the ticket, review the comments, then submit your draft again.'
          : typeof data?.detail === 'string' ? data.detail : 'Unable to add the comment.')
        return
      }
      if (response.status !== 201 || data?.ticketId !== ticket.id ||
        !Number.isSafeInteger(data.ticketVersion) || data.ticketVersion <= ticket.version ||
        typeof data.comment?.id !== 'string' || !data.comment.id ||
        typeof data.comment.authorName !== 'string' || typeof data.comment.content !== 'string' ||
        !Number.isFinite(Date.parse(data.comment.createdAt)) || !Number.isFinite(Date.parse(data.updatedAt))) {
        throw new Error('Unconfirmed comment response')
      }
      onCreated(data)
      onDraftChange({ authorName: '', content: '' })
      setSuccess('Comment added successfully.')
    } catch {
      if (!controller.signal.aborted) {
        reloadNeeded = true
        setError('The comment outcome could not be confirmed. Reload this ticket and check its comments before retrying.')
      }
    } finally {
      if (mutationRef.current === controller) {
        mutationRef.current = null
        if (!controller.signal.aborted) setSubmitting(false)
        onEnd(reloadNeeded)
      }
    }
  }

  function change(field, value) {
    onDraftChange({ authorName: draft.authorName, content: draft.content, [field]: value })
    setFieldErrors({})
    setError('')
    setSuccess('')
  }

  return (
    <section aria-label="Add a comment">
      {ticket.status === 'CLOSED' && <p>Closed tickets cannot receive new comments.</p>}
      {error && <p className="error-message" role="alert">{error}</p>}
      {success && <p className="success-message" role="status">{success}</p>}
      {workflow.reload && <p>Reload the ticket before submitting your draft again.</p>}
      <form className="assignment-form" onSubmit={submit} aria-busy={submitting}>
        <label htmlFor={`comment-author-${ticket.id}`}>Comment author</label>
        <input id={`comment-author-${ticket.id}`} value={draft.authorName} disabled={blocked}
          onChange={event => change('authorName', event.target.value)} required
          aria-invalid={Boolean(fieldErrors.authorName)}
          aria-describedby={fieldErrors.authorName ? `author-error-${ticket.id}` : undefined} />
        {fieldErrors.authorName && <small id={`author-error-${ticket.id}`} className="field-error">{fieldErrors.authorName}</small>}
        <label htmlFor={`comment-content-${ticket.id}`}>Comment content</label>
        <textarea id={`comment-content-${ticket.id}`} value={draft.content} disabled={blocked}
          onChange={event => change('content', event.target.value)} required
          aria-invalid={Boolean(fieldErrors.content)}
          aria-describedby={fieldErrors.content ? `content-error-${ticket.id}` : undefined} />
        {fieldErrors.content && <small id={`content-error-${ticket.id}`} className="field-error">{fieldErrors.content}</small>}
        {fieldErrors.expectedVersion && <small className="field-error">{fieldErrors.expectedVersion}</small>}
        <button type="submit" disabled={blocked}>{submitting ? 'Adding comment...' : 'Add Comment'}</button>
      </form>
    </section>
  )
}

export default TicketComments

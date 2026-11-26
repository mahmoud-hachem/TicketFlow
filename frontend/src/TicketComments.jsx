import useTicketMutation from './useTicketMutation.js'

function TicketComments({ ticket, workflow, draft, onDraftChange, onBegin, onEnd, onCreated }) {
  const { submitting, error, fieldErrors, success, clearFeedback, setFieldErrors, run } =
    useTicketMutation({ onBegin, onEnd })
  const blocked = ticket.status === 'CLOSED' || workflow.busy || workflow.reload ||
    !Number.isSafeInteger(ticket.version) || ticket.version < 0

  async function submit(event) {
    event.preventDefault()
    if (blocked) return
    const authorName = draft.authorName.trim()
    const content = draft.content.trim()
    const fields = {}
    if (!authorName || authorName.length > 100) fields.authorName = 'Enter an author name of 1 to 100 characters.'
    if (!content || content.length > 2000) fields.content = 'Enter a comment of 1 to 2000 characters.'
    clearFeedback()
    setFieldErrors(fields)
    if (Object.keys(fields).length) return
    await run({
      url: `/api/tickets/${encodeURIComponent(ticket.id)}/comments`, method: 'POST',
      body: { authorName, content, expectedVersion: ticket.version },
      validateResponse: (data, response) => response.status === 201 && data?.ticketId === ticket.id &&
        Number.isSafeInteger(data.ticketVersion) && data.ticketVersion > ticket.version &&
        typeof data.comment?.id === 'string' && Boolean(data.comment.id) &&
        typeof data.comment.authorName === 'string' && typeof data.comment.content === 'string' &&
        Number.isFinite(Date.parse(data.comment.createdAt)) && Number.isFinite(Date.parse(data.updatedAt)),
      onSuccess: data => {
        onCreated(data)
        onDraftChange({ authorName: '', content: '' })
        return 'Comment added successfully.'
      },
      messages: {
        conflict: 'This ticket changed. Reload the ticket, review the comments, then submit your draft again.',
        failure: 'Unable to add the comment.',
        unconfirmed: 'The comment outcome could not be confirmed. Reload this ticket and check its comments before retrying.',
      },
    })
  }

  function change(field, value) {
    onDraftChange({ authorName: draft.authorName, content: draft.content, [field]: value })
    clearFeedback()
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

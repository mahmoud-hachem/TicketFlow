import { useEffect, useRef, useState } from 'react'

function stringFields(fields) {
  return fields && typeof fields === 'object'
    ? Object.fromEntries(Object.entries(fields).filter(([, value]) => typeof value === 'string')) : {}
}

function needsReload(response, data) {
  return response.status >= 500 || response.status === 409 ||
    data?.errorCode === 'TICKET_NOT_FOUND' || Boolean(data?.fieldErrors?.expectedVersion)
}

// Transport and lifecycle only; callers retain validation, response contracts and domain feedback.
export default function useTicketMutation({ onBegin, onEnd }) {
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [fieldErrors, setFieldErrors] = useState({})
  const [success, setSuccess] = useState('')
  const [requiresReload, setRequiresReload] = useState(false)
  const mutationRef = useRef(null)
  useEffect(() => () => mutationRef.current?.abort(), [])

  function clearFeedback() {
    setError('')
    setFieldErrors({})
    setSuccess('')
  }

  async function run({ url, method, body, validateResponse, onSuccess, messages,
    reloadOnError = needsReload, showFieldErrors = false }) {
    if (mutationRef.current || !onBegin()) return
    const controller = new AbortController()
    mutationRef.current = controller
    const isCurrent = () => !controller.signal.aborted && mutationRef.current === controller
    let reloadNeeded = false
    setSubmitting(true)
    clearFeedback()
    try {
      const response = await fetch(url, {
        method, headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body), signal: controller.signal,
      })
      const data = await response.json().catch(() => null)
      if (!isCurrent()) return
      if (!response.ok) {
        const fields = stringFields(data?.fieldErrors)
        setFieldErrors(fields)
        reloadNeeded = reloadOnError(response, data)
        setRequiresReload(reloadNeeded)
        const fieldMessage = showFieldErrors ? Object.values(fields).join(' ') : ''
        setError(data?.errorCode === 'VERSION_CONFLICT'
          ? messages.conflict
          : fieldMessage || (typeof data?.detail === 'string' ? data.detail : messages.failure))
        return
      }
      if (!validateResponse(data, response)) throw new Error('Unconfirmed mutation response')
      setSuccess(onSuccess(data))
    } catch {
      if (isCurrent()) {
        reloadNeeded = true
        setRequiresReload(true)
        setError(messages.unconfirmed)
      }
    } finally {
      if (mutationRef.current === controller) {
        mutationRef.current = null
        if (!controller.signal.aborted) setSubmitting(false)
        onEnd(reloadNeeded)
      }
    }
  }

  return { submitting, error, fieldErrors, success, requiresReload,
    clearFeedback, setFieldErrors, run }
}

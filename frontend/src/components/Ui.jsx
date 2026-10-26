import { useEffect, useState } from 'react'
import { getAppointmentDisplayStatus } from '../utils/appointments.js'

export function useApiData(loader, dependencies = []) {
  const [data, setData] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [version, setVersion] = useState(0)

  useEffect(() => {
    let active = true
    setLoading(true)
    setError('')
    loader()
      .then((result) => { if (active) setData(result) })
      .catch((failure) => { if (active) setError(failure.message) })
      .finally(() => { if (active) setLoading(false) })
    return () => { active = false }
  }, [...dependencies, version])

  return { data, setData, loading, error, reload: () => setVersion((value) => value + 1) }
}

export function LoadingError({ loading, error, onRetry }) {
  if (loading) return <p className="feedback" role="status">Loading records…</p>
  if (error) return (
    <div className="feedback feedback--error" role="alert">
      <span>{error}</span>
      {onRetry && <button className="button button--quiet" onClick={onRetry}>Try again</button>}
    </div>
  )
  return null
}

export function Notice({ message, error }) {
  if (!message) return null
  return <p className={`notice ${error ? 'notice--error' : 'notice--success'}`} role={error ? 'alert' : 'status'}>{message}</p>
}

export function StatusBadge({ value }) {
  if (!value) return <span className="muted">—</span>
  return <span className={`badge badge--${String(value).toLowerCase().replaceAll('_', '-')}`}>{String(value).replaceAll('_', ' ')}</span>
}

export function AppointmentStatusBadge({ appointment }) {
  return <StatusBadge value={getAppointmentDisplayStatus(appointment)} />
}

export function DataTable({ columns, rows, empty = 'No records found.', rowKey = 'id' }) {
  if (!rows.length) return <div className="empty-state">{empty}</div>
  return (
    <div className="table-wrap">
      <table>
        <thead><tr>{columns.map((column) => <th key={column.key}>{column.label}</th>)}</tr></thead>
        <tbody>
          {rows.map((row, index) => (
            <tr key={row[rowKey] ?? index}>
              {columns.map((column) => (
                <td key={column.key}>{column.render ? column.render(row) : row[column.key] ?? '—'}</td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function Modal({ title, onClose, children }) {
  useEffect(() => {
    function onKeyDown(event) {
      if (event.key === 'Escape') onClose()
    }
    window.addEventListener('keydown', onKeyDown)
    return () => window.removeEventListener('keydown', onKeyDown)
  }, [onClose])

  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose() }}>
      <section className="modal" role="dialog" aria-modal="true" aria-labelledby="modal-title">
        <div className="modal__header">
          <h2 id="modal-title">{title}</h2>
          <button className="icon-button" aria-label="Close dialog" onClick={onClose}>×</button>
        </div>
        {children}
      </section>
    </div>
  )
}

export function Field({ label, name, value, onChange, type = 'text', required = false, options, placeholder, min, max, rows = 3, disabled }) {
  const common = {
    id: name,
    name,
    value: value ?? '',
    onChange: (event) => onChange(name, event.target.value),
    required,
    disabled,
    placeholder,
    min,
    max,
  }
  return (
    <label className="field" htmlFor={name}>
      <span>{label}{required && <b aria-hidden="true"> *</b>}</span>
      {options ? (
        <select {...common}>
          <option value="">Choose…</option>
          {options.map((option) => <option key={option.value} value={option.value}>{option.label}</option>)}
        </select>
      ) : type === 'textarea' ? (
        <textarea {...common} rows={rows} />
      ) : (
        <input {...common} type={type} />
      )}
    </label>
  )
}

export function FormActions({ onCancel, busy, submitLabel = 'Save' }) {
  return (
    <div className="form-actions">
      <button className="button button--quiet" type="button" onClick={onCancel} disabled={busy}>Cancel</button>
      <button className="button button--primary" type="submit" disabled={busy}>{busy ? 'Saving…' : submitLabel}</button>
    </div>
  )
}

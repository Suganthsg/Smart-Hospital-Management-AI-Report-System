export const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080')
  .replace(/\/+$/, '')

export function createBasicAuthorization(username, password) {
  const bytes = new TextEncoder().encode(`${username}:${password}`)
  const binary = Array.from(bytes, (byte) => String.fromCharCode(byte)).join('')
  return `Basic ${btoa(binary)}`
}

export async function apiRequest(path, { authorization, method = 'GET', body, signal } = {}) {
  const headers = { Accept: 'application/json' }
  if (authorization) headers.Authorization = authorization
  if (body !== undefined) headers['Content-Type'] = 'application/json'

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal,
    })
  } catch (error) {
    if (error.name === 'AbortError') throw error
    throw new Error(`Could not reach the hospital API at ${API_BASE_URL}. Confirm the Spring Boot server is running, VITE_API_BASE_URL is correct, and the backend CORS origin allows this frontend.`)
  }

  if (response.status === 204) return null
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json') ? await response.json() : null
  if (!response.ok) {
    const statusMessage = {
      404: 'The requested record was not found. Refresh the list and try again.',
      405: 'The running hospital API does not support this request. Update or restart the Spring Boot backend, then try again.',
      409: 'This record cannot be deleted because it has linked medical history.',
    }[response.status]
    const serverMessage = payload?.message || payload?.detail
    const message = response.status === 401
      ? 'Authentication failed. Check your email and password.'
      : serverMessage || (response.status === 405
        ? statusMessage
        : payload?.error || statusMessage || `The API returned HTTP ${response.status}.`)
    const failure = new Error(message)
    failure.status = response.status
    throw failure
  }
  return payload
}

export function pageItems(payload) {
  return Array.isArray(payload) ? payload : Array.isArray(payload?.content) ? payload.content : []
}

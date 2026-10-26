import { useMemo, useState } from 'react'
import { apiRequest, createBasicAuthorization } from '../api/client.js'
import { AuthContext } from './context.js'

export function AuthProvider({ children }) {
  const [session, setSession] = useState(null)

  async function login(username, password) {
    const authorization = createBasicAuthorization(username.trim(), password)
    const user = await apiRequest('/api/v1/auth/me', { authorization })
    setSession({ authorization, user })
    return user
  }

  function logout() {
    setSession(null)
  }

  const value = useMemo(() => ({ ...session, login, logout }), [session])
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

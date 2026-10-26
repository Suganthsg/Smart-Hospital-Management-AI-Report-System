import { useState } from 'react'
import { BrowserRouter, Link, Navigate, NavLink, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { Activity, CalendarDays, ClipboardList, FlaskConical, LayoutDashboard, LogOut, Menu, Microscope, Stethoscope, Users, X } from 'lucide-react'
import { apiRequest, pageItems } from './api/client.js'
import { AuthProvider } from './auth/AuthContext.jsx'
import { useAuth } from './auth/useAuth.js'
import { useApiData } from './hooks/useApiData.js'
import { AppointmentStatusBadge, DataTable, Field, LoadingError, Notice, StatusBadge } from './components/Ui.jsx'
import { AppointmentsPage, DoctorsPage, EncountersPage, LaboratoryPage, PatientsPage, PrescriptionsPage, ReportsPage } from './pages/Workspaces.jsx'
import './App.css'

const navigation = [
  { path: '/dashboard', label: 'Dashboard', icon: LayoutDashboard, roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'LAB_TECHNICIAN', 'PATIENT'] },
  { path: '/patients', label: 'Patients', icon: Users, roles: ['ADMIN', 'RECEPTIONIST'] },
  { path: '/doctors', label: 'Doctors', icon: Stethoscope, roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT', 'LAB_TECHNICIAN'] },
  { path: '/appointments', label: 'Appointments', icon: CalendarDays, roles: ['ADMIN', 'DOCTOR', 'RECEPTIONIST', 'PATIENT'] },
  { path: '/encounters', label: 'Encounters', icon: ClipboardList, roles: ['ADMIN', 'DOCTOR', 'PATIENT'] },
  { path: '/prescriptions', label: 'Prescriptions', icon: Activity, roles: ['ADMIN', 'DOCTOR', 'PATIENT'] },
  { path: '/laboratory', label: 'Laboratory', icon: Microscope, roles: ['ADMIN', 'DOCTOR', 'PATIENT', 'LAB_TECHNICIAN'] },
  { path: '/reports', label: 'AI-assisted reports', icon: FlaskConical, roles: ['ADMIN', 'DOCTOR', 'PATIENT'] },
]

function LoginPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const [registering, setRegistering] = useState(false)
  const [values, setValues] = useState({})
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  function update(name, value) {
    setValues((current) => ({ ...current, [name]: value }))
  }

  async function submit(event) {
    event.preventDefault()
    setBusy(true)
    setError('')
    try {
      if (registering) {
        await apiRequest('/api/v1/patients', {
          method: 'POST',
          body: {
            email: values.email,
            password: values.password,
            firstName: values.firstName,
            lastName: values.lastName,
            dateOfBirth: values.dateOfBirth || null,
            phone: values.phone || '',
          },
        })
      }
      await auth.login(values.email, values.password)
      navigate('/dashboard', { replace: true })
    } catch (failure) {
      setError(failure.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <main className="login-page">
      <section className="login-card">
        <Link to="/login" className="brand brand--login">
          <span className="brand__mark"><Activity size={22} /></span>
          <span><strong>Northstar</strong><small>CARE NETWORK</small></span>
        </Link>
        <div className="login-card__intro">
          <p className="eyebrow">SMART HOSPITAL PLATFORM</p>
          <h1>{registering ? 'Create your patient account' : 'Welcome back'}</h1>
          <p>{registering ? 'Register to access your appointments and clinical records.' : 'Sign in to continue to your care workspace.'}</p>
        </div>
        <Notice message={error} error />
        <form className="form-stack" onSubmit={submit}>
          {registering && <>
            <div className="field-grid">
              <Field label="First name" name="firstName" value={values.firstName} onChange={update} required />
              <Field label="Last name" name="lastName" value={values.lastName} onChange={update} required />
            </div>
            <Field label="Date of birth" name="dateOfBirth" type="date" value={values.dateOfBirth} onChange={update} />
            <Field label="Phone" name="phone" type="tel" value={values.phone} onChange={update} />
          </>}
          <Field label="Email address" name="email" type="email" value={values.email} onChange={update} required autoComplete="username" />
          <Field label="Password" name="password" type="password" value={values.password} onChange={update} required autoComplete={registering ? 'new-password' : 'current-password'} minLength={registering ? 12 : undefined} maxLength={registering ? 72 : undefined} />
          {registering && <p className="field-hint">Use at least 12 characters. Credentials are kept in memory only and cleared when this page is refreshed.</p>}
          <button className="button button--primary button--wide" type="submit" disabled={busy}>{busy ? 'Please wait…' : registering ? 'Create account and sign in' : 'Sign in securely'}</button>
        </form>
        <p className="login-switch">
          {registering ? 'Already registered?' : 'New patient?'}{' '}
          <button className="text-button" onClick={() => { setRegistering((value) => !value); setError('') }}>
            {registering ? 'Sign in' : 'Create an account'}
          </button>
        </p>
        <p className="login-footnote">Staff accounts are created by an administrator. Backend authorization remains the security boundary.</p>
      </section>
    </main>
  )
}

function Dashboard() {
  const auth = useAuth()
  const isLabTechnician = auth.user.role === 'LAB_TECHNICIAN'
  const appointments = useApiData(
    () => apiRequest(
      isLabTechnician ? '/api/v1/lab-orders/worklist?page=0&size=5' : '/api/v1/appointments?status=SCHEDULED&page=0&size=5',
      { authorization: auth.authorization },
    ),
    [auth.authorization, isLabTechnician],
  )
  const health = useApiData(
    () => apiRequest('/api/v1/health/database').then((result) => [result]),
    [],
  )
  const appointmentRows = pageItems(appointments.data)
  const upcoming = appointments.data?.totalElements ?? appointmentRows.length

  return (
    <>
      <header className="page-heading page-heading--dashboard">
        <div><p className="eyebrow">YOUR CARE, CONNECTED</p><h1>Welcome back, {auth.user.email.split('@')[0]}</h1><p className="page-subtitle">A clear view of your accessible care and clinical work.</p></div>
      </header>
      <div className="stats-grid">
        <article className="stat-card"><span className="stat-card__icon stat-card__icon--blue"><CalendarDays size={20} /></span><span className="stat-card__label">{isLabTechnician ? 'Laboratory worklist' : 'Scheduled appointments'}</span><strong>{appointments.loading ? '—' : upcoming}</strong><small>{isLabTechnician ? 'Orders ready for lab processing' : 'From your accessible schedule'}</small></article>
        <article className="stat-card"><span className="stat-card__icon stat-card__icon--green"><Activity size={20} /></span><span className="stat-card__label">Database connection</span><strong>{health.data[0]?.status || (health.loading ? '…' : 'Unavailable')}</strong><small>Live backend health check</small></article>
        <article className="stat-card"><span className="stat-card__icon stat-card__icon--violet"><Users size={20} /></span><span className="stat-card__label">Signed in as</span><strong className="stat-card__role">{auth.user.role.replaceAll('_', ' ')}</strong><small>{auth.user.email}</small></article>
      </div>
      <div className="dashboard-grid">
        <section className="panel">
          <div className="section-heading"><div><p className="eyebrow">{isLabTechnician ? 'LABORATORY' : 'YOUR SCHEDULE'}</p><h2>{isLabTechnician ? 'Recent worklist orders' : 'Upcoming appointments'}</h2></div><Link className="text-link" to={isLabTechnician ? '/laboratory' : '/appointments'}>{isLabTechnician ? 'Open worklist' : 'Open schedule'} <span aria-hidden="true">→</span></Link></div>
          <LoadingError loading={appointments.loading} error={appointments.error} onRetry={appointments.reload} />
          <DataTable
            rows={appointmentRows}
            empty="No appointments are available for this account."
            columns={[
              ...(isLabTechnician ? [
                { key: 'testName', label: 'Test' },
                { key: 'medicalRecordNumber', label: 'Patient' },
                { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
              ] : [
                { key: 'startAt', label: 'Date & time', render: (row) => new Date(row.startAt).toLocaleString() },
                { key: 'doctorName', label: 'Doctor' },
                { key: 'status', label: 'Status', render: (row) => <AppointmentStatusBadge appointment={row} /> },
              ]),
            ]}
          />
        </section>
        <section className="panel quick-panel">
          <p className="eyebrow">QUICK ACCESS</p><h2>Care workspaces</h2>
          <div className="quick-links">{navigation.filter((item) => item.path !== '/dashboard' && item.roles.includes(auth.user.role)).map((item) => {
            const Icon = item.icon
            return <Link to={item.path} key={item.path}><span><Icon size={18} /></span>{item.label}<b aria-hidden="true">↗</b></Link>
          })}</div>
        </section>
      </div>
      <LoadingError loading={health.loading} error={health.error} />
    </>
  )
}

function ProtectedLayout() {
  const auth = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [menuOpen, setMenuOpen] = useState(false)
  if (!auth.user) return <Navigate to="/login" replace />

  const items = navigation.filter((item) => item.roles.includes(auth.user.role))
  const pathTitles = Object.fromEntries(navigation.map((item) => [item.path, item.label]))

  return (
    <div className="app-shell">
      <aside className={`sidebar ${menuOpen ? 'sidebar--open' : ''}`}>
        <Link to="/dashboard" className="brand" onClick={() => setMenuOpen(false)}>
          <span className="brand__mark"><Activity size={22} /></span><span><strong>Northstar</strong><small>CARE NETWORK</small></span>
        </Link>
        <div className="sidebar-label">WORKSPACE</div>
        <nav className="main-nav" aria-label="Main navigation">
          {items.map((item) => {
            const Icon = item.icon
            return <NavLink end to={item.path} key={item.path} onClick={() => setMenuOpen(false)}><Icon size={18} /><span>{item.label}</span></NavLink>
          })}
        </nav>
        <div className="sidebar-bottom">
          <div className="profile-chip"><div className="avatar">{auth.user.email.slice(0, 1).toUpperCase()}</div><div className="profile-chip__text"><strong>{auth.user.email}</strong><small>{auth.user.role.replaceAll('_', ' ')}</small></div></div>
          <button className="signout-button" onClick={() => { auth.logout(); navigate('/login', { replace: true }) }}><LogOut size={17} /> Sign out</button>
        </div>
      </aside>
      {menuOpen && <button className="mobile-scrim" aria-label="Close navigation" onClick={() => setMenuOpen(false)} />}
      <main className="main-content">
        <div className="mobile-topbar">
          <button className="icon-button" aria-label={menuOpen ? 'Close menu' : 'Open menu'} onClick={() => setMenuOpen((value) => !value)}>{menuOpen ? <X /> : <Menu />}</button>
          <span>{pathTitles[location.pathname] || 'Care workspace'}</span>
          <span className="avatar avatar--small">{auth.user.email.slice(0, 1).toUpperCase()}</span>
        </div>
        <Routes>
          <Route path="/dashboard" element={<Dashboard />} />
          <Route path="/patients" element={rolePage(auth.user.role, '/patients', <PatientsPage />)} />
          <Route path="/doctors" element={rolePage(auth.user.role, '/doctors', <DoctorsPage />)} />
          <Route path="/appointments" element={rolePage(auth.user.role, '/appointments', <AppointmentsPage />)} />
          <Route path="/encounters" element={rolePage(auth.user.role, '/encounters', <EncountersPage />)} />
          <Route path="/prescriptions" element={rolePage(auth.user.role, '/prescriptions', <PrescriptionsPage />)} />
          <Route path="/laboratory" element={rolePage(auth.user.role, '/laboratory', <LaboratoryPage />)} />
          <Route path="/reports" element={rolePage(auth.user.role, '/reports', <ReportsPage />)} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </div>
  )
}

function rolePage(role, path, element) {
  return navigation.find((item) => item.path === path)?.roles.includes(role) ? element : <Navigate to="/dashboard" replace />
}

function AppRoutes() {
  const auth = useAuth()
  return (
    <Routes>
      <Route path="/login" element={auth.user ? <Navigate to="/dashboard" replace /> : <LoginPage />} />
      <Route path="/*" element={<ProtectedLayout />} />
    </Routes>
  )
}

export default function App() {
  return <BrowserRouter><AuthProvider><AppRoutes /></AuthProvider></BrowserRouter>
}

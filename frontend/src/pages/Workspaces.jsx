import { useEffect, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { apiRequest, pageItems } from '../api/client.js'
import { useAuth } from '../auth/useAuth.js'
import { useApiData } from '../hooks/useApiData.js'
import { AppointmentStatusBadge, DataTable, Field, FormActions, LoadingError, Modal, Notice, StatusBadge } from '../components/Ui.jsx'
import { canCancelAppointment, canRescheduleAppointment } from '../utils/appointments.js'

function localDateInputValue() {
  const date = new Date()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${date.getFullYear()}-${month}-${day}`
}

function isValidEncounterId(value) {
  return /^[1-9]\d*$/.test(value)
}

function PageHeader({ eyebrow, title, description, action }) {
  return <header className="page-heading"><div><p className="eyebrow">{eyebrow}</p><h1>{title}</h1><p className="page-subtitle">{description}</p></div>{action}</header>
}

function useAction() {
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState({ message: '', error: false })
  async function run(task, successMessage) {
    setBusy(true)
    setNotice({ message: '', error: false })
    try {
      const result = await task()
      setNotice({ message: successMessage, error: false })
      return result
    } catch (error) {
      setNotice({ message: error.message, error: true })
      return undefined
    } finally {
      setBusy(false)
    }
  }
  return { busy, notice, setNotice, run }
}

function TextForm({ initial = {}, fields, onSubmit, onCancel, busy, submitLabel, notice }) {
  const [values, setValues] = useState(initial)
  function update(name, value) { setValues((current) => ({ ...current, [name]: value })) }
  return (
    <form className="form-stack" onSubmit={(event) => { event.preventDefault(); onSubmit(values) }}>
      {notice && <Notice {...notice} />}
      {fields.map((field) => <Field key={field.name} {...field} value={values[field.name]} onChange={update} />)}
      <FormActions onCancel={onCancel} busy={busy} submitLabel={submitLabel} />
    </form>
  )
}

function NoAccess({ message }) {
  return <div className="empty-state empty-state--large">{message}</div>
}

export function PatientsPage() {
  const auth = useAuth()
  const canDelete = auth.user.role === 'ADMIN'
  const [query, setQuery] = useState('')
  const [search, setSearch] = useState('')
  const [modal, setModal] = useState(null)
  const [deletePatient, setDeletePatient] = useState(null)
  const action = useAction()
  const patients = useApiData(
    () => apiRequest(`/api/v1/patients?page=0&size=100${search ? `&q=${encodeURIComponent(search)}` : ''}`, { authorization: auth.authorization }).then(pageItems),
    [auth.authorization, search],
  )
  const doctors = useApiData(
    () => apiRequest('/api/v1/doctors?page=0&size=100', { authorization: auth.authorization }).then(pageItems),
    [auth.authorization],
  )
  async function savePatient(values) {
    const body = {
      firstName: values.firstName,
      lastName: values.lastName,
      dateOfBirth: values.dateOfBirth || null,
      phone: values.phone || '',
    }
    const result = await action.run(
      () => apiRequest(modal.patient ? `/api/v1/patients/${modal.patient.id}` : '/api/v1/patients', {
        authorization: auth.authorization,
        method: modal.patient ? 'PUT' : 'POST',
        body: modal.patient ? body : { ...body, email: values.email, password: values.password },
      }),
      modal.patient ? 'Patient record updated.' : 'Patient registered.',
    )
    if (result) { setModal(null); patients.reload() }
  }

  async function assignDoctor(patient, doctorId) {
    const result = await action.run(
      () => apiRequest(`/api/v1/patients/${patient.id}/assigned-doctor/${doctorId}`, { authorization: auth.authorization, method: 'PATCH' }),
      'Assigned doctor updated.',
    )
    if (result) patients.reload()
  }

  async function confirmDeletePatient() {
    if (!deletePatient) return
    const result = await action.run(async () => {
      await apiRequest(`/api/v1/patients/${deletePatient.id}`, {
        authorization: auth.authorization,
        method: 'DELETE',
      })
      return true
    }, 'Patient record deleted.')
    if (result) {
      setDeletePatient(null)
      patients.reload()
    }
  }

  return (
    <>
      <PageHeader eyebrow="PATIENT RECORDS" title="Patients" description="Search and maintain patient registration and care assignments."
        action={<button className="button button--primary" onClick={() => setModal({ patient: null })}>Register patient</button>} />
      <Notice {...action.notice} />
      <section className="panel">
        <form className="toolbar" onSubmit={(event) => { event.preventDefault(); setSearch(query.trim()) }}>
          <Field label="Search patients" name="search" value={query} onChange={(_, value) => setQuery(value)} placeholder="Name, email, or record number" />
          <button className="button button--secondary" type="submit">Search</button>
        </form>
        <LoadingError loading={patients.loading || doctors.loading} error={patients.error || doctors.error} onRetry={() => { patients.reload(); doctors.reload() }} />
        {!patients.loading && !patients.error && <DataTable rows={patients.data} empty="No patient records match this search." columns={[
          { key: 'medicalRecordNumber', label: 'Record number' },
          { key: 'name', label: 'Patient', render: (row) => <><strong>{row.firstName} {row.lastName}</strong><small className="table-subtext">{row.email}</small></> },
          { key: 'phone', label: 'Phone' },
          { key: 'assignedDoctorId', label: 'Assigned doctor', render: (row) => (
            <select className="table-select" aria-label={`Assigned doctor for ${row.firstName} ${row.lastName}`} value={row.assignedDoctorId ?? ''} onChange={(event) => event.target.value && assignDoctor(row, event.target.value)}>
              <option value="">Not assigned</option>{doctors.data.map((doctor) => <option key={doctor.id} value={doctor.id}>{doctor.firstName} {doctor.lastName}</option>)}
            </select>
          ) },
          { key: 'actions', label: 'Actions', render: (row) => <div className="row-actions">
            <button className="button button--quiet button--small" onClick={() => setModal({ patient: row })}>Edit</button>
            {canDelete && <button className="button button--danger button--small" disabled={action.busy} onClick={() => { action.setNotice({ message: '', error: false }); setDeletePatient(row) }}>Delete</button>}
          </div> },
        ]} />}
      </section>
      {modal && <Modal title={modal.patient ? 'Update patient' : 'Register patient'} onClose={() => setModal(null)}>
        <TextForm initial={modal.patient || {}} busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={savePatient} submitLabel={modal.patient ? 'Save changes' : 'Register patient'}
          fields={[
            { label: 'First name', name: 'firstName', required: true },
            { label: 'Last name', name: 'lastName', required: true },
            ...(modal.patient ? [] : [{ label: 'Email', name: 'email', type: 'email', required: true }, { label: 'Password (12–72 characters)', name: 'password', type: 'password', minLength: 12, maxLength: 72, required: true }]),
            { label: 'Date of birth', name: 'dateOfBirth', type: 'date' },
            { label: 'Phone', name: 'phone', type: 'tel' },
          ]} />
      </Modal>}
      {deletePatient && <Modal title="Delete patient" onClose={() => { if (!action.busy) setDeletePatient(null) }}>
        <div className="confirmation-content">
          <p>Are you sure you want to delete this patient?</p>
          <p><strong>{deletePatient.firstName} {deletePatient.lastName}</strong></p>
          <p>Record number: <strong>{deletePatient.medicalRecordNumber}</strong></p>
        </div>
        <Notice {...action.notice} />
        <div className="form-actions">
          <button className="button button--quiet" type="button" disabled={action.busy} onClick={() => setDeletePatient(null)}>Cancel</button>
          <button className="button button--danger" type="button" disabled={action.busy} onClick={confirmDeletePatient}>{action.busy ? 'Deleting…' : 'Delete patient'}</button>
        </div>
      </Modal>}
    </>
  )
}

export function DoctorsPage() {
  const auth = useAuth()
  const canManage = auth.user.role === 'ADMIN'
  const [modal, setModal] = useState('')
  const [doctor, setDoctor] = useState(null)
  const [editDoctor, setEditDoctor] = useState(null)
  const [deleteDoctor, setDeleteDoctor] = useState(null)
  const action = useAction()
  const doctors = useApiData(
    () => apiRequest('/api/v1/doctors?page=0&size=100', { authorization: auth.authorization }).then(pageItems),
    [auth.authorization],
  )
  const canEditAvailability = auth.user.role === 'ADMIN'
    || (auth.user.role === 'DOCTOR' && doctor?.email === auth.user.email)
  const slots = useApiData(
    () => doctor ? apiRequest(`/api/v1/doctors/${doctor.id}/availability`, { authorization: auth.authorization }) : Promise.resolve([]),
    [auth.authorization, doctor?.id],
  )

  async function saveDoctor(values) {
    const editing = modal === 'edit'
    const result = await action.run(
      () => apiRequest(editing ? `/api/v1/doctors/${editDoctor.id}` : '/api/v1/doctors', {
        authorization: auth.authorization,
        method: editing ? 'PUT' : 'POST',
        body: editing
          ? { licenseNumber: values.licenseNumber, firstName: values.firstName, lastName: values.lastName, specialization: values.specialization, phone: values.phone || '' }
          : values,
      }),
      editing ? 'Doctor details updated.' : 'Doctor account created.',
    )
    if (result) { setModal(''); setEditDoctor(null); doctors.reload() }
  }

  async function confirmDeleteDoctor() {
    if (!deleteDoctor) return
    const result = await action.run(async () => {
      await apiRequest(`/api/v1/doctors/${deleteDoctor.id}`, {
        authorization: auth.authorization,
        method: 'DELETE',
      })
      return true
    }, 'Doctor record deleted.')
    if (result) {
      setDeleteDoctor(null)
      doctors.reload()
    }
  }

  async function addSlot(values) {
    const result = await action.run(
      () => apiRequest(`/api/v1/doctors/${doctor.id}/availability`, {
        authorization: auth.authorization,
        method: 'POST',
        body: { dayOfWeek: Number(values.dayOfWeek), startTime: `${values.startTime}:00`, endTime: `${values.endTime}:00` },
      }),
      'Availability slot added.',
    )
    if (result) slots.reload()
  }

  async function removeSlot(slotId) {
    const result = await action.run(
      () => apiRequest(`/api/v1/doctors/${doctor.id}/availability/${slotId}`, { authorization: auth.authorization, method: 'DELETE' }),
      'Availability slot removed.',
    )
    if (result === null) slots.reload()
  }

  const weekDays = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday']
  return <>
    <PageHeader eyebrow="CARE TEAM" title="Doctors" description="Browse clinicians and manage published availability."
      action={canManage && <button className="button button--primary" onClick={() => setModal('create')}>Add doctor</button>} />
    <Notice {...action.notice} />
    <section className="panel">
      <LoadingError loading={doctors.loading} error={doctors.error} onRetry={doctors.reload} />
      {!doctors.loading && !doctors.error && <DataTable rows={doctors.data} empty="No doctors are available." columns={[
        { key: 'doctor', label: 'Doctor', render: (row) => <><strong>Dr. {row.firstName} {row.lastName}</strong><small className="table-subtext">{row.email}</small></> },
        { key: 'specialization', label: 'Specialty' },
        { key: 'licenseNumber', label: 'License' },
        { key: 'availability', label: 'Actions', render: (row) => <div className="row-actions">
          <button className="button button--quiet button--small" onClick={() => { setDoctor(row); setModal('availability') }}>View schedule</button>
          {canManage && <button className="button button--secondary button--small" onClick={() => { setEditDoctor(row); setModal('edit') }}>Edit</button>}
          {canManage && <button className="button button--danger button--small" disabled={action.busy} onClick={() => { action.setNotice({ message: '', error: false }); setDeleteDoctor(row) }}>Delete</button>}
        </div> },
      ]} />}
    </section>
    {(modal === 'create' || modal === 'edit') && <Modal title={modal === 'edit' ? 'Edit doctor details' : 'Add doctor'} onClose={() => { setModal(''); setEditDoctor(null) }}>
      <TextForm initial={editDoctor || {}} busy={action.busy} notice={action.notice} onCancel={() => { setModal(''); setEditDoctor(null) }} onSubmit={saveDoctor} submitLabel={modal === 'edit' ? 'Save changes' : 'Create doctor'} fields={[
        ...(modal === 'create' ? [{ label: 'Email', name: 'email', type: 'email', required: true }] : []),
        ...(modal === 'create' ? [{ label: 'Password (12–72 characters)', name: 'password', type: 'password', minLength: 12, maxLength: 72, required: true }] : []),
        { label: 'License number', name: 'licenseNumber', required: true },
        { label: 'First name', name: 'firstName', required: true },
        { label: 'Last name', name: 'lastName', required: true },
        { label: 'Specialization', name: 'specialization', required: true },
        { label: 'Phone', name: 'phone', type: 'tel' },
      ]} />
    </Modal>}
    {modal === 'availability' && doctor && <Modal title={`Availability — Dr. ${doctor.firstName} ${doctor.lastName}`} onClose={() => setModal('')}>
      <Notice {...action.notice} />
      <LoadingError loading={slots.loading} error={slots.error} onRetry={slots.reload} />
      {!slots.loading && !slots.error && <DataTable rows={slots.data} empty="No availability has been published." columns={[
        { key: 'dayOfWeek', label: 'Day', render: (row) => weekDays[row.dayOfWeek - 1] ?? `Day ${row.dayOfWeek}` },
        { key: 'startTime', label: 'From' }, { key: 'endTime', label: 'To' },
        { key: 'action', label: '', render: (row) => canEditAvailability ? <button className="button button--quiet button--small" onClick={() => removeSlot(row.id)}>Remove</button> : null },
      ]} />}
      {canEditAvailability && <div className="form-divider"><h3>Add availability</h3>
        <TextForm key={doctor.id} busy={action.busy} notice={action.notice} onSubmit={addSlot} submitLabel="Add slot" fields={[
          { label: 'Day of week', name: 'dayOfWeek', options: weekDays.map((label, index) => ({ label, value: index + 1 })), required: true },
          { label: 'Start time', name: 'startTime', type: 'time', required: true },
          { label: 'End time', name: 'endTime', type: 'time', required: true },
        ]} />
      </div>}
    </Modal>}
    {deleteDoctor && <Modal title="Delete doctor" onClose={() => { if (!action.busy) setDeleteDoctor(null) }}>
      <div className="confirmation-content">
        <p>Are you sure you want to delete Dr. {deleteDoctor.firstName} {deleteDoctor.lastName}?</p>
        <p>This removes the doctor profile and disables its user account if no linked records prevent deletion.</p>
      </div>
      <Notice {...action.notice} />
      <div className="form-actions">
        <button className="button button--quiet" type="button" disabled={action.busy} onClick={() => setDeleteDoctor(null)}>Cancel</button>
        <button className="button button--danger" type="button" disabled={action.busy} onClick={confirmDeleteDoctor}>{action.busy ? 'Deleting…' : 'Delete doctor'}</button>
      </div>
    </Modal>}
  </>
}

export function AppointmentsPage() {
  const auth = useAuth()
  const role = auth.user.role
  const staff = role === 'ADMIN' || role === 'RECEPTIONIST'
  const [currentTime, setCurrentTime] = useState(() => Date.now())
  const [modal, setModal] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const bookingInProgress = useRef(false)
  const lastAutoRefresh = useRef(0)
  const action = useAction()
  const appointments = useApiData(
    () => apiRequest('/api/v1/appointments?page=0&size=100', { authorization: auth.authorization }).then(pageItems),
    [auth.authorization, refreshKey],
  )
  const patientChoices = useApiData(
    () => staff ? apiRequest('/api/v1/patients?page=0&size=100', { authorization: auth.authorization }).then(pageItems) : Promise.resolve([]),
    [auth.authorization, staff],
  )
  const doctorChoices = useApiData(
    () => apiRequest('/api/v1/doctors?page=0&size=100', { authorization: auth.authorization }).then(pageItems),
    [auth.authorization],
  )

  useEffect(() => {
    function refreshWhenActive() {
      const now = Date.now()
      if (!document.hidden && now - lastAutoRefresh.current >= 5000) {
        lastAutoRefresh.current = now
        setRefreshKey((value) => value + 1)
      }
    }

    const interval = window.setInterval(refreshWhenActive, 30000)
    window.addEventListener('focus', refreshWhenActive)
    document.addEventListener('visibilitychange', refreshWhenActive)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('focus', refreshWhenActive)
      document.removeEventListener('visibilitychange', refreshWhenActive)
    }
  }, [])

  useEffect(() => {
    const interval = window.setInterval(() => setCurrentTime(Date.now()), 1000)
    return () => window.clearInterval(interval)
  }, [])

  async function submitAppointment(values) {
    if (bookingInProgress.current) return
    bookingInProgress.current = true
    try {
      const body = {
        startAt: new Date(values.startAt).toISOString(),
        durationMinutes: Number(values.durationMinutes),
        reason: values.reason || '',
        ...(staff ? { patientId: Number(values.patientId), doctorId: Number(values.doctorId) } : role === 'DOCTOR' ? { patientId: Number(values.patientId) } : { doctorId: Number(values.doctorId) }),
      }
      const result = await action.run(() => apiRequest('/api/v1/appointments', { authorization: auth.authorization, method: 'POST', body }), 'Appointment scheduled.')
      if (result) { setModal(null); setRefreshKey((value) => value + 1) }
    } finally {
      bookingInProgress.current = false
    }
  }

  async function cancelAppointment(id) {
    const result = await action.run(() => apiRequest(`/api/v1/appointments/${id}/cancel`, { authorization: auth.authorization, method: 'PATCH' }), 'Appointment cancelled.')
    if (result) setRefreshKey((value) => value + 1)
  }

  async function reschedule(values) {
    const durationMinutes = Number(values.durationMinutes)
    const result = await action.run(() => apiRequest(`/api/v1/appointments/${modal.appointment.id}/reschedule`, {
      authorization: auth.authorization, method: 'PUT',
      body: { startAt: new Date(values.startAt).toISOString(), durationMinutes },
    }), 'Appointment rescheduled.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }

  const appointment = modal?.appointment
  return <>
    <PageHeader eyebrow="SCHEDULING" title="Appointments" description="Schedule visits and manage upcoming appointments."
      action={<button className="button button--primary" onClick={() => setModal({ type: 'create' })}>Schedule appointment</button>} />
    <Notice {...action.notice} />
    <section className="panel">
      <LoadingError loading={appointments.loading} error={appointments.error} onRetry={appointments.reload} />
      {!appointments.loading && !appointments.error && <DataTable rows={appointments.data} empty="No appointments are visible for this account." columns={[
        { key: 'startAt', label: 'Date & time', render: (row) => new Date(row.startAt).toLocaleString() },
        { key: 'medicalRecordNumber', label: 'Patient', render: (row) => <>{row.medicalRecordNumber}<small className="table-subtext">Patient ID: {row.patientId}</small></> },
        { key: 'doctorName', label: 'Doctor' },
        { key: 'reason', label: 'Reason' },
        { key: 'status', label: 'Status', render: (row) => <AppointmentStatusBadge appointment={row} /> },
        { key: 'actions', label: '', render: (row) => role !== 'LAB_TECHNICIAN' && (canRescheduleAppointment(row, currentTime) || canCancelAppointment(row, currentTime))
          ? <div className="row-actions">{canRescheduleAppointment(row, currentTime) && <button className="button button--quiet button--small" onClick={() => setModal({ type: 'reschedule', appointment: row })}>Reschedule</button>}{canCancelAppointment(row, currentTime) && <button className="button button--danger button--small" onClick={() => cancelAppointment(row.id)}>Cancel</button>}</div>
          : null },
      ]} />}
    </section>
    {modal?.type === 'create' && <Modal title="Schedule appointment" onClose={() => setModal(null)}>
      <LoadingError
        loading={(staff && patientChoices.loading) || doctorChoices.loading}
        error={patientChoices.error || doctorChoices.error}
        onRetry={() => { patientChoices.reload(); doctorChoices.reload() }}
      />
      <TextForm busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={submitAppointment} submitLabel="Schedule" fields={[
        ...(staff ? [{
          label: 'Patient', name: 'patientId', required: true,
          options: patientChoices.data.map((person) => ({ value: person.id, label: `${person.medicalRecordNumber} — ${person.firstName} ${person.lastName}` })),
        }] : role === 'DOCTOR' ? [{ label: 'Patient ID', name: 'patientId', type: 'number', min: 1, required: true }] : []),
        ...(staff ? [{
          label: 'Doctor', name: 'doctorId', required: true,
          options: doctorChoices.data.map((person) => ({ value: person.id, label: `Dr. ${person.firstName} ${person.lastName} — ${person.specialization}` })),
        }] : role === 'PATIENT' ? [{
          label: 'Doctor', name: 'doctorId', required: true,
          options: doctorChoices.data.map((person) => ({ value: person.id, label: `Dr. ${person.firstName} ${person.lastName} — ${person.specialization}` })),
        }] : []),
        { label: 'Start date and time', name: 'startAt', type: 'datetime-local', step: 900, required: true },
        { label: 'Duration (minutes)', name: 'durationMinutes', type: 'number', min: 15, max: 120, step: 15, required: true },
        { label: 'Reason', name: 'reason', type: 'textarea' },
      ]} />
      <p className="field-hint">The server checks availability, ownership and conflicting appointments.</p>
    </Modal>}
    {modal?.type === 'reschedule' && appointment && <Modal title="Reschedule appointment" onClose={() => setModal(null)}>
      <TextForm initial={{
        startAt: new Date(new Date(appointment.startAt).getTime() - new Date(appointment.startAt).getTimezoneOffset() * 60000).toISOString().slice(0, 16),
        durationMinutes: Math.round((new Date(appointment.endAt) - new Date(appointment.startAt)) / 60000),
      }} busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={reschedule} submitLabel="Save new time" fields={[
        { label: 'Start date and time', name: 'startAt', type: 'datetime-local', step: 900, required: true },
        { label: 'Duration (minutes)', name: 'durationMinutes', type: 'number', min: 15, max: 120, step: 15, required: true },
      ]} />
    </Modal>}
  </>
}

export function EncountersPage() {
  const auth = useAuth()
  const navigate = useNavigate()
  const doctor = auth.user.role === 'DOCTOR'
  const [modal, setModal] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const action = useAction()
  const encounters = useApiData(
    () => apiRequest('/api/v1/encounters?page=0&size=100', { authorization: auth.authorization }).then(pageItems),
    [auth.authorization, refreshKey],
  )

  async function save(values) {
    const isUpdate = Boolean(modal.encounter)
    const body = {
      encounterDate: values.encounterDate,
      clinicalNotes: values.clinicalNotes,
      diagnosis: values.diagnosis,
      ...(isUpdate ? { status: values.status } : { patientId: Number(values.patientId), ...(values.appointmentId ? { appointmentId: Number(values.appointmentId) } : {}) }),
    }
    const result = await action.run(() => apiRequest(isUpdate ? `/api/v1/encounters/${modal.encounter.id}` : '/api/v1/encounters', {
      authorization: auth.authorization, method: isUpdate ? 'PUT' : 'POST', body,
    }), isUpdate ? 'Encounter updated.' : 'Encounter created as a draft.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }

  return <>
    <PageHeader eyebrow="CLINICAL RECORDS" title="Encounters" description="Review patient encounters and document clinician-led care."
      action={doctor && <button className="button button--primary" onClick={() => setModal({ encounter: null, initial: { encounterDate: localDateInputValue() } })}>New encounter</button>} />
    <Notice {...action.notice} />
    <section className="panel">
      <LoadingError loading={encounters.loading} error={encounters.error} onRetry={encounters.reload} />
      {!encounters.loading && !encounters.error && <DataTable rows={encounters.data} empty="No encounters are available for this account." columns={[
        { key: 'id', label: 'Encounter ID', render: (row) => `#${row.id}` },
        { key: 'encounterDate', label: 'Date' },
        { key: 'medicalRecordNumber', label: 'Patient' },
        { key: 'patientId', label: 'Patient ID' },
        { key: 'doctorName', label: 'Clinician' },
        { key: 'diagnosis', label: 'Diagnosis' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
        { key: 'actions', label: '', render: (row) => <div className="row-actions"><button className="button button--quiet button--small" onClick={() => setModal({ encounter: row, view: true })}>Open</button>{doctor && row.status === 'DRAFT' && <button className="button button--secondary button--small" onClick={() => setModal({ encounter: row })}>Edit</button>}</div> },
      ]} />}
    </section>
    {modal?.view && <Modal title={`Encounter #${modal.encounter.id}`} onClose={() => setModal(null)}>
      <div className="detail-list"><p><span>Encounter ID</span><strong>#{modal.encounter.id}</strong></p><p><span>Patient</span><strong>{modal.encounter.medicalRecordNumber}</strong></p><p><span>Patient ID</span><strong>{modal.encounter.patientId}</strong></p><p><span>Doctor</span><strong>{modal.encounter.doctorName}</strong></p><p><span>Diagnosis</span><strong>{modal.encounter.diagnosis}</strong></p><p><span>Clinical notes</span></p><pre className="record-content">{modal.encounter.clinicalNotes}</pre></div>
      <div className="form-actions"><button className="button button--primary" onClick={() => { const encounterId = modal.encounter.id; navigate(`/prescriptions?encounterId=${encodeURIComponent(encounterId)}`); setModal(null) }}>View prescriptions</button></div>
    </Modal>}
    {modal && !modal.view && <Modal title={modal.encounter ? 'Update draft encounter' : 'Create clinical encounter'} onClose={() => setModal(null)}>
      <TextForm initial={modal.encounter || modal.initial || {}} busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={save} submitLabel={modal.encounter ? 'Save draft' : 'Create draft'}
        fields={[
          ...(!modal.encounter ? [{ label: 'Patient ID', name: 'patientId', type: 'number', min: 1, required: true }, { label: 'Appointment ID (optional)', name: 'appointmentId', type: 'number', min: 1 }] : []),
          { label: 'Encounter date', name: 'encounterDate', type: 'date', required: true },
          { label: 'Diagnosis', name: 'diagnosis', type: 'textarea', required: true },
          { label: 'Clinical notes', name: 'clinicalNotes', type: 'textarea', rows: 7, required: true },
          ...(modal.encounter ? [{ label: 'Status', name: 'status', options: [{ label: 'Draft', value: 'DRAFT' }, { label: 'Finalize encounter', value: 'FINALIZED' }], required: true }] : []),
        ]} />
      <p className="field-hint">Only the patient's currently assigned doctor can create or update an encounter. Finalized encounters cannot be changed, and linked appointments must be valid.</p>
    </Modal>}
  </>
}

function EncounterLookup({ value, onChange, onSubmit, busy }) {
  return <form className="toolbar" onSubmit={(event) => { event.preventDefault(); onSubmit() }}>
    <Field label="Encounter ID" name="encounterId" value={value} onChange={(_, next) => onChange(next)} type="number" min={1} required placeholder="Enter an encounter ID" />
    <button className="button button--secondary" disabled={busy} type="submit">Load encounter</button>
  </form>
}

function PrescriptionItemsForm({ initialItems = [], onSave, onCancel, busy, label = 'Save prescription', notice }) {
  const blank = { medicineName: '', dosage: '', route: '', frequency: '', duration: '', quantity: 1, instructions: '' }
  const [items, setItems] = useState(initialItems.length ? initialItems.map((item) => ({ ...item })) : [{ ...blank }])
  function update(index, name, value) {
    setItems((current) => current.map((item, itemIndex) => itemIndex === index ? { ...item, [name]: name === 'quantity' ? Number(value) : value } : item))
  }
  function submit(event) {
    event.preventDefault()
    onSave(items.map(({ medicineName, dosage, route, frequency, duration, quantity, instructions }) => ({ medicineName, dosage, route, frequency, duration, quantity, instructions })))
  }
  const itemFields = [
    ['medicineName', 'Medicine name'], ['dosage', 'Dosage'], ['route', 'Route'], ['frequency', 'Frequency'],
    ['duration', 'Duration'], ['quantity', 'Quantity'], ['instructions', 'Instructions'],
  ]
  return <form className="form-stack" onSubmit={submit}>
    {notice && <Notice {...notice} />}
    {items.map((item, index) => <fieldset className="item-fieldset" key={index}>
      <legend>Medicine {index + 1}</legend>
      <div className="field-grid">{itemFields.map(([name, label]) => <Field key={name} label={label} name={`${name}-${index}`} value={item[name]} onChange={(_, value) => update(index, name, value)} type={name === 'quantity' ? 'number' : name === 'instructions' ? 'textarea' : 'text'} min={name === 'quantity' ? 1 : undefined} required={name !== 'instructions'} />)}</div>
      {items.length > 1 && <button className="text-button" type="button" onClick={() => setItems((current) => current.filter((_, itemIndex) => itemIndex !== index))}>Remove medicine</button>}
    </fieldset>)}
    <button className="button button--quiet button--small" type="button" onClick={() => setItems((current) => [...current, { ...blank }])}>+ Add medicine</button>
    <FormActions onCancel={onCancel} busy={busy} submitLabel={label} />
  </form>
}

export function PrescriptionsPage() {
  const auth = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const [lookupDraft, setLookupDraft] = useState(null)
  const [lookupNotice, setLookupNotice] = useState({ message: '', error: false })
  const encounterId = searchParams.get('encounterId') || ''
  const lookup = lookupDraft ?? encounterId
  const [modal, setModal] = useState(null)
  const [history, setHistory] = useState(null)
  const action = useAction()
  const prescriptions = useApiData(
    () => {
      if (!encounterId) return Promise.resolve(null)
      if (!isValidEncounterId(encounterId)) {
        return Promise.reject(new Error('Enter a valid positive whole-number encounter ID.'))
      }
      return apiRequest(`/api/v1/encounters/${encodeURIComponent(encounterId)}`, { authorization: auth.authorization })
        .then((encounter) => apiRequest(`/api/v1/encounters/${encodeURIComponent(encounter.id)}/prescriptions`, {
          authorization: auth.authorization,
        }).then((items) => ({ encounter, items })))
        .catch((error) => {
          if (error.status === 403) throw new Error('You cannot access this clinical encounter')
          throw error
        })
    },
    [auth.authorization, encounterId],
  )

  async function save(items) {
    const encounter = prescriptions.data?.encounter
    if (prescriptions.loading || prescriptions.error || !encounter || String(encounter.id) !== encounterId) {
      action.setNotice({ message: 'Load an encounter you are authorized to access before saving a prescription.', error: true })
      return
    }
    if (modal.prescription && String(modal.prescription.encounterId) !== String(encounter.id)) {
      action.setNotice({ message: 'This prescription does not belong to the selected encounter.', error: true })
      return
    }
    if (!modal.prescription && (auth.user.role !== 'DOCTOR' || encounter.status !== 'FINALIZED')) {
      action.setNotice({ message: 'A finalized encounter and an authorized doctor are required to create a prescription.', error: true })
      return
    }
    const path = modal.prescription ? `/api/v1/prescriptions/${modal.prescription.id}` : `/api/v1/encounters/${encounterId}/prescriptions`
    const result = await action.run(() => apiRequest(path, { authorization: auth.authorization, method: modal.prescription ? 'PUT' : 'POST', body: { items } }), 'Prescription draft saved.')
    if (result) { setModal(null); prescriptions.reload() }
  }
  async function finalize(id) {
    const result = await action.run(() => apiRequest(`/api/v1/prescriptions/${id}/finalize`, { authorization: auth.authorization, method: 'POST' }), 'Prescription finalized.')
    if (result) prescriptions.reload()
  }
  async function loadHistory(prescription) {
    const result = await action.run(() => apiRequest(`/api/v1/prescriptions/${prescription.id}/history`, { authorization: auth.authorization }), 'Prescription history loaded.')
    if (result) setHistory({ prescription, entries: result })
  }
  function loadEncounter() {
    const id = lookup.trim()
    if (!isValidEncounterId(id)) {
      setSearchParams({})
      setModal(null)
      setLookupNotice({ message: 'Enter a valid positive whole-number encounter ID.', error: true })
      return
    }
    setModal(null)
    setHistory(null)
    setLookupNotice({ message: '', error: false })
    setSearchParams({ encounterId: id })
    setLookupDraft(null)
  }
  const doctor = auth.user.role === 'DOCTOR'
  const canReadHistory = doctor || auth.user.role === 'ADMIN'

  return <>
    <PageHeader eyebrow="MEDICATION RECORDS" title="Prescriptions" description="Review prescription records associated with an encounter." />
    <section className="panel"><EncounterLookup value={lookup} onChange={setLookupDraft} onSubmit={loadEncounter} />
      <Notice {...lookupNotice} />
      <Notice {...action.notice} />
      {encounterId && <>
        {!prescriptions.loading && !prescriptions.error && prescriptions.data?.encounter && <div className="panel-toolbar"><span>Encounter #{prescriptions.data.encounter.id} · Patient ID {prescriptions.data.encounter.patientId}</span>{doctor && prescriptions.data.encounter.status === 'FINALIZED' && <button className="button button--primary" onClick={() => setModal({ prescription: null })}>Create prescription</button>}</div>}
        <LoadingError loading={prescriptions.loading} error={
          prescriptions.error === 'Encounter not found'
              ? `No encounter exists with ID #${encounterId}. Enter the Encounter ID, not the Patient ID.`
              : prescriptions.error === 'You cannot access this clinical encounter'
                ? `You are not authorized to access encounter #${encounterId}.`
                : prescriptions.error
        } onRetry={prescriptions.reload} />
        {!prescriptions.loading && !prescriptions.error && prescriptions.data?.encounter?.status !== 'FINALIZED' && doctor && <p className="field-hint">Prescriptions can only be created for finalized encounters.</p>}
        {!prescriptions.loading && !prescriptions.error && prescriptions.data?.encounter && <DataTable rows={prescriptions.data.items} empty="No prescriptions found for this encounter." columns={[
          { key: 'id', label: 'Prescription' }, { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
          { key: 'items', label: 'Items', render: (row) => row.items?.map((item) => item.medicineName).join(', ') || '—' },
          { key: 'updatedAt', label: 'Last updated', render: (row) => row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—' },
          { key: 'actions', label: '', render: (row) => <div className="row-actions">
            {doctor && row.status === 'DRAFT' && <><button className="button button--quiet button--small" onClick={() => setModal({ prescription: row })}>Edit</button><button className="button button--secondary button--small" onClick={() => finalize(row.id)}>Finalize</button></>}
            {canReadHistory && <button className="button button--quiet button--small" onClick={() => loadHistory(row)}>History</button>}
          </div> },
        ]} />}
      </>}
    </section>
    {modal && !prescriptions.loading && !prescriptions.error && prescriptions.data?.encounter && <Modal title={modal.prescription ? `Edit prescription #${modal.prescription.id}` : `New prescription for encounter #${prescriptions.data.encounter.id}`} onClose={() => setModal(null)}>
      <PrescriptionItemsForm initialItems={modal.prescription?.items} busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSave={save} label={modal.prescription ? 'Save changes' : 'Create draft'} />
    </Modal>}
    {history && <Modal title={`Prescription #${history.prescription.id} history`} onClose={() => setHistory(null)}>
      <DataTable rows={history.entries} empty="No history is available." columns={[
        { key: 'id', label: 'Version' }, { key: 'eventType', label: 'Event' }, { key: 'occurredAt', label: 'When', render: (row) => row.occurredAt ? new Date(row.occurredAt).toLocaleString() : '—' },
        { key: 'items', label: 'Items', render: (row) => row.items?.map((item) => item.medicineName).join(', ') || '—' },
      ]} />
    </Modal>}
  </>
}

export function LaboratoryPage() {
  const auth = useAuth()
  const role = auth.user.role
  const technician = role === 'LAB_TECHNICIAN'
  const canSeeEncounterLab = role === 'ADMIN' || role === 'DOCTOR' || role === 'PATIENT'
  const [lookup, setLookup] = useState('')
  const [encounterId, setEncounterId] = useState('')
  const [modal, setModal] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const action = useAction()
  const orders = useApiData(
    () => technician
      ? apiRequest('/api/v1/lab-orders/worklist?page=0&size=100', { authorization: auth.authorization }).then(pageItems)
      : encounterId ? apiRequest(`/api/v1/encounters/${encounterId}/lab-orders`, { authorization: auth.authorization }) : Promise.resolve([]),
    [auth.authorization, encounterId, refreshKey, technician],
  )

  async function createOrder(values) {
    const result = await action.run(() => apiRequest(`/api/v1/encounters/${encounterId}/lab-orders`, {
      authorization: auth.authorization, method: 'POST', body: { testName: values.testName },
    }), 'Laboratory order created.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }
  async function enterResult(values) {
    const result = await action.run(() => apiRequest(`/api/v1/lab-orders/${modal.order.id}/results`, {
      authorization: auth.authorization, method: 'POST',
      body: { resultValue: values.resultValue, units: values.units || '', referenceRange: values.referenceRange || '', notes: values.notes || '' },
    }), 'Result version recorded.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }
  async function verifyResult(values) {
    const result = await action.run(() => apiRequest(`/api/v1/lab-orders/${modal.order.id}/results/${values.resultId}/verify`, {
      authorization: auth.authorization, method: 'POST',
    }), 'Latest result verified.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }
  async function prepareVerification(order) {
    const result = await action.run(
      () => apiRequest(`/api/v1/lab-orders/${order.id}/results`, { authorization: auth.authorization }),
      'Latest result loaded for clinician review.',
    )
    if (result?.length) setModal({ type: 'verify', order, result: result[0] })
    else if (result) action.setNotice({ message: 'There is no result to verify for this order.', error: true })
  }
  async function showResults(order) {
    const result = await action.run(() => apiRequest(`/api/v1/lab-orders/${order.id}/results`, { authorization: auth.authorization }), 'Result history loaded.')
    if (result) setModal({ type: 'history', order, results: result })
  }

  return <>
    <PageHeader eyebrow="DIAGNOSTICS" title="Laboratory" description={technician ? 'Process ordered tests and append result versions to the worklist.' : 'Review encounter orders and verified result history.'}
      action={role === 'DOCTOR' && encounterId && <button className="button button--primary" onClick={() => setModal({ type: 'order' })}>Order a test</button>} />
    <section className="panel">
      {!technician && <EncounterLookup value={lookup} onChange={setLookup} onSubmit={() => setEncounterId(lookup.trim())} busy={orders.loading} />}
      <Notice {...action.notice} />
      <LoadingError loading={orders.loading} error={orders.error} onRetry={orders.reload} />
      {(technician || encounterId) && !orders.loading && !orders.error && <DataTable rows={orders.data} empty={technician ? 'The laboratory worklist is empty.' : 'No laboratory orders for this encounter.'} columns={[
        { key: 'id', label: 'Order' },
        { key: 'testName', label: 'Test', render: (row) => <><strong>{row.testName}</strong><small className="table-subtext">Encounter #{row.encounterId} · {row.medicalRecordNumber}</small></> },
        { key: 'encounterDate', label: 'Encounter date' },
        { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
        { key: 'actions', label: 'Actions', render: (row) => <div className="row-actions">
          {(role === 'DOCTOR' || role === 'PATIENT' || role === 'ADMIN' || technician) && <button className="button button--quiet button--small" onClick={() => showResults(row)}>Results</button>}
          {technician && <button className="button button--secondary button--small" onClick={() => setModal({ type: 'result', order: row })}>Enter result</button>}
          {role === 'DOCTOR' && row.status === 'RESULTED' && <button className="button button--secondary button--small" onClick={() => prepareVerification(row)}>Review latest result</button>}
        </div> },
      ]} />}
      {technician && <p className="field-hint">Results are append-only versions. Enter only values supplied by the laboratory; the backend validates and controls access.</p>}
    </section>
    {modal?.type === 'order' && <Modal title={`Order laboratory test for encounter #${encounterId}`} onClose={() => setModal(null)}>
      <TextForm busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={createOrder} submitLabel="Place order" fields={[{ label: 'Test name', name: 'testName', required: true }]} />
    </Modal>}
    {modal?.type === 'result' && <Modal title={`Enter result · ${modal.order.testName}`} onClose={() => setModal(null)}>
      <TextForm busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={enterResult} submitLabel="Save result" fields={[
        { label: 'Result value', name: 'resultValue', type: 'textarea', required: true },
        { label: 'Units', name: 'units' }, { label: 'Reference range', name: 'referenceRange' }, { label: 'Notes', name: 'notes', type: 'textarea' },
      ]} />
    </Modal>}
    {modal?.type === 'verify' && <Modal title={`Verify latest result · ${modal.order.testName}`} onClose={() => setModal(null)}>
      <div className="review-result">
        <p><span>Result version {modal.result.resultVersion}</span><StatusBadge value={modal.result.status} /></p>
        <strong>{modal.result.resultValue} {modal.result.units || ''}</strong>
        {modal.result.referenceRange && <small>Reference range: {modal.result.referenceRange}</small>}
        {modal.result.notes && <small>{modal.result.notes}</small>}
      </div>
      <form className="form-stack" onSubmit={(event) => { event.preventDefault(); verifyResult({ resultId: modal.result.id }) }}>
        <Notice {...action.notice} />
        <FormActions onCancel={() => setModal(null)} busy={action.busy} submitLabel={`Verify version ${modal.result.resultVersion}`} />
      </form>
      <p className="field-hint">Verification is an explicit clinician action, recorded by the backend. Confirm this result is accurate before proceeding.</p>
    </Modal>}
    {modal?.type === 'history' && <Modal title={`Results · ${modal.order.testName}`} onClose={() => setModal(null)}>
      <DataTable rows={modal.results} empty="No result versions have been entered." columns={[
        { key: 'resultVersion', label: 'Version' }, { key: 'resultValue', label: 'Result' }, { key: 'units', label: 'Units' },
        { key: 'referenceRange', label: 'Reference range' }, { key: 'status', label: 'Verification', render: (row) => <StatusBadge value={row.status} /> },
        { key: 'verifiedAt', label: 'Verified at', render: (row) => row.verifiedAt ? new Date(row.verifiedAt).toLocaleString() : '—' },
      ]} />
    </Modal>}
    {!technician && !canSeeEncounterLab && <NoAccess message="Laboratory access is not available for this role." />}
  </>
}

export function ReportsPage() {
  const auth = useAuth()
  const doctor = auth.user.role === 'DOCTOR'
  const canSeeHistory = doctor || auth.user.role === 'ADMIN'
  const [lookup, setLookup] = useState('')
  const [encounterId, setEncounterId] = useState('')
  const [modal, setModal] = useState(null)
  const [refreshKey, setRefreshKey] = useState(0)
  const action = useAction()
  const reports = useApiData(
    () => encounterId ? apiRequest(`/api/v1/encounters/${encounterId}/reports`, { authorization: auth.authorization }) : Promise.resolve([]),
    [auth.authorization, encounterId, refreshKey],
  )

  async function generate() {
    const result = await action.run(() => apiRequest(`/api/v1/encounters/${encounterId}/reports/generate`, {
      authorization: auth.authorization, method: 'POST',
    }), 'A report draft was generated from recorded encounter notes and verified laboratory results.')
    if (result) setRefreshKey((value) => value + 1)
  }
  async function edit(values) {
    const result = await action.run(() => apiRequest(`/api/v1/reports/${modal.report.id}/draft`, {
      authorization: auth.authorization, method: 'PUT', body: { content: values.content },
    }), 'Draft changes saved.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }
  async function transition(actionName, values = {}, targetReport = modal?.report) {
    const result = await action.run(() => apiRequest(`/api/v1/reports/${targetReport.id}/${actionName}`, {
      authorization: auth.authorization, method: 'POST',
      ...(actionName === 'approve' || actionName === 'reject' ? { body: { notes: values.notes || '' } } : {}),
    }), actionName === 'submit' ? 'Report submitted for clinician review.' : actionName === 'approve' ? 'Report approved by clinician.' : 'Report rejected with reviewer notes.')
    if (result) { setModal(null); setRefreshKey((value) => value + 1) }
  }
  async function history(report) {
    const result = await action.run(() => apiRequest(`/api/v1/reports/${report.id}/history`, { authorization: auth.authorization }), 'Report history loaded.')
    if (result) setModal({ type: 'history', report, entries: result })
  }

  return <>
    <PageHeader eyebrow="CLINICAL DOCUMENTATION" title="AI-assisted reports" description="Review drafts grounded in recorded clinical notes and verified laboratory results." />
    <section className="panel">
      <EncounterLookup value={lookup} onChange={setLookup} onSubmit={() => setEncounterId(lookup.trim())} busy={reports.loading} />
      {doctor && encounterId && <div className="panel-toolbar"><span>Encounter #{encounterId}</span><button className="button button--primary" onClick={generate} disabled={action.busy}>Generate draft</button></div>}
      <Notice {...action.notice} />
      <LoadingError loading={reports.loading} error={reports.error} onRetry={reports.reload} />
      {encounterId && !reports.loading && !reports.error && <DataTable rows={reports.data} empty="No reports are available for this encounter." columns={[
        { key: 'id', label: 'Report' }, { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
        { key: 'generationMode', label: 'Source' }, { key: 'updatedAt', label: 'Updated', render: (row) => row.updatedAt ? new Date(row.updatedAt).toLocaleString() : '—' },
        { key: 'reviewerNotes', label: 'Reviewer notes' },
        { key: 'actions', label: '', render: (row) => <div className="row-actions">
          <button className="button button--quiet button--small" onClick={() => setModal({ type: 'view', report: row })}>View</button>
          {doctor && ['DRAFT', 'REJECTED'].includes(row.status) && <button className="button button--quiet button--small" onClick={() => setModal({ type: 'edit', report: row })}>Edit draft</button>}
          {doctor && row.status === 'DRAFT' && <button className="button button--secondary button--small" onClick={() => transition('submit', {}, row)}>Submit</button>}
          {doctor && row.status === 'IN_REVIEW' && <><button className="button button--secondary button--small" onClick={() => setModal({ type: 'review', report: row, action: 'approve' })}>Approve</button><button className="button button--danger button--small" onClick={() => setModal({ type: 'review', report: row, action: 'reject' })}>Reject</button></>}
          {canSeeHistory && <button className="button button--quiet button--small" onClick={() => history(row)}>History</button>}
        </div> },
      ]} />}
      <p className="field-hint">Generation creates an editable draft only. A clinician must submit, review, and explicitly approve it; the backend records history.</p>
    </section>
    {modal?.type === 'view' && <Modal title={`Report #${modal.report.id}`} onClose={() => setModal(null)}><StatusBadge value={modal.report.status} /><pre className="record-content">{modal.report.content}</pre></Modal>}
    {modal?.type === 'edit' && <Modal title={`Edit report draft #${modal.report.id}`} onClose={() => setModal(null)}>
      <TextForm initial={{ content: modal.report.content }} busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={edit} submitLabel="Save draft" fields={[{ label: 'Draft content', name: 'content', type: 'textarea', rows: 14, required: true }]} />
      <p className="field-hint">Review every statement for accuracy. Do not add facts that are absent from the clinical record.</p>
    </Modal>}
    {modal?.type === 'review' && <Modal title={`${modal.action === 'approve' ? 'Approve' : 'Reject'} report #${modal.report.id}`} onClose={() => setModal(null)}>
      <TextForm busy={action.busy} notice={action.notice} onCancel={() => setModal(null)} onSubmit={(values) => transition(modal.action, values)} submitLabel={modal.action === 'approve' ? 'Approve report' : 'Reject report'} fields={[
        { label: 'Reviewer notes', name: 'notes', type: 'textarea', required: modal.action === 'reject' },
      ]} />
      <p className="field-hint">Approval is an explicit clinician action and is never performed automatically.</p>
    </Modal>}
    {modal?.type === 'history' && <Modal title={`Report #${modal.report.id} history`} onClose={() => setModal(null)}>
      <DataTable rows={modal.entries} empty="No history entries are available." columns={[
        { key: 'eventType', label: 'Event' }, { key: 'status', label: 'Status', render: (row) => <StatusBadge value={row.status} /> },
        { key: 'occurredAt', label: 'When', render: (row) => row.occurredAt ? new Date(row.occurredAt).toLocaleString() : '—' },
        { key: 'eventNote', label: 'Notes' },
        { key: 'contentSnapshot', label: 'Snapshot', render: (row) => <details><summary>Read snapshot</summary><pre className="record-content record-content--compact">{row.contentSnapshot}</pre></details> },
      ]} />
    </Modal>}
  </>
}

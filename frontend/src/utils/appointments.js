const CANCELLATION_NOTICE_PERIOD_MS = 24 * 60 * 60 * 1000
const HOSPITAL_TIME_ZONE = import.meta.env?.VITE_HOSPITAL_TIME_ZONE || 'Asia/Kolkata'

function calendarDateOrdinal(date, timeZone) {
  if (!Number.isFinite(date.getTime())) return null

  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: 'numeric',
    day: 'numeric',
  }).formatToParts(date)
  const dateParts = Object.fromEntries(parts.map(({ type, value }) => [type, value]))
  const year = Number(dateParts.year)
  const month = Number(dateParts.month)
  const day = Number(dateParts.day)

  return Math.floor(Date.UTC(year, month - 1, day) / (24 * 60 * 60 * 1000))
}

export function getAppointmentDisplayStatus(
  appointment,
  now = new Date(),
  timeZone = HOSPITAL_TIME_ZONE,
) {
  if (appointment.status !== 'SCHEDULED') return appointment.status

  const appointmentDate = calendarDateOrdinal(new Date(appointment.startAt), timeZone)
  const today = calendarDateOrdinal(new Date(now), timeZone)

  return appointmentDate !== null && today !== null && appointmentDate - today >= 2
    ? 'WAITING'
    : appointment.status
}

function isScheduledFutureAppointment(appointment, now) {
  const startTime = new Date(appointment.startAt).getTime()
  return appointment.status === 'SCHEDULED' && Number.isFinite(startTime) && startTime > now
}

export function canRescheduleAppointment(appointment, now = Date.now()) {
  return isScheduledFutureAppointment(appointment, now)
}

export function canCancelAppointment(appointment, now = Date.now()) {
  const startTime = new Date(appointment.startAt).getTime()
  return appointment.status === 'SCHEDULED'
    && Number.isFinite(startTime)
    && startTime - now > CANCELLATION_NOTICE_PERIOD_MS
}

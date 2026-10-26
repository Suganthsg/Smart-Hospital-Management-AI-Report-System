import assert from 'node:assert/strict'
import test from 'node:test'
import { canCancelAppointment, canRescheduleAppointment, getAppointmentDisplayStatus } from './appointments.js'

const now = Date.parse('2030-01-01T05:00:00Z')

test('past appointments cannot be rescheduled or cancelled even when their local time looks later', () => {
  const appointment = {
    startAt: '2030-01-01T04:59:00Z',
    status: 'SCHEDULED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), false)
  assert.equal(canCancelAppointment(appointment, now), false)
})

test('appointments starting at the current instant cannot be managed across offsets', () => {
  const appointment = {
    startAt: '2030-01-01T10:30:00+05:30',
    status: 'SCHEDULED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), false)
  assert.equal(canCancelAppointment(appointment, now), false)
})

test('appointments more than 24 hours away can be cancelled and rescheduled', () => {
  const appointment = {
    startAt: '2030-01-02T05:00:01Z',
    status: 'SCHEDULED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), true)
  assert.equal(canCancelAppointment(appointment, now), true)
})

test('appointments exactly 24 hours away can be rescheduled but not cancelled', () => {
  const appointment = {
    startAt: '2030-01-02T10:30:00+05:30',
    status: 'SCHEDULED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), true)
  assert.equal(canCancelAppointment(appointment, now), false)
})

test('appointments less than 24 hours away can be rescheduled but not cancelled', () => {
  const appointment = {
    startAt: '2030-01-02T04:59:59Z',
    status: 'SCHEDULED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), true)
  assert.equal(canCancelAppointment(appointment, now), false)
})

test('future appointments with non-scheduled statuses have no management actions', () => {
  const appointment = {
    startAt: '2030-01-01T05:00:01Z',
    status: 'CANCELLED',
  }
  assert.equal(canRescheduleAppointment(appointment, now), false)
  assert.equal(canCancelAppointment(appointment, now), false)
})

const hospitalTimeZone = 'Asia/Kolkata'
const justAfterHospitalMidnight = new Date('2030-01-01T18:30:00Z')
const justBeforeHospitalMidnight = new Date('2030-01-02T18:25:00Z')

test('scheduled appointments one hospital calendar day away display SCHEDULED', () => {
  assert.equal(getAppointmentDisplayStatus({
    startAt: '2030-01-03T00:05:00+05:30',
    status: 'SCHEDULED',
  }, justAfterHospitalMidnight, hospitalTimeZone), 'SCHEDULED')
})

test('scheduled appointments exactly two hospital calendar days away display WAITING', () => {
  assert.equal(getAppointmentDisplayStatus({
    startAt: '2030-01-04T00:05:00+05:30',
    status: 'SCHEDULED',
  }, justBeforeHospitalMidnight, hospitalTimeZone), 'WAITING')
})

test('scheduled appointments three or more hospital calendar days away display WAITING', () => {
  assert.equal(getAppointmentDisplayStatus({
    startAt: '2030-01-05T00:05:00+05:30',
    status: 'SCHEDULED',
  }, justAfterHospitalMidnight, hospitalTimeZone), 'WAITING')
})

test('cancelled appointments retain their backend status regardless of date', () => {
  assert.equal(getAppointmentDisplayStatus({
    startAt: '2030-01-05T00:05:00+05:30',
    status: 'CANCELLED',
  }, justAfterHospitalMidnight, hospitalTimeZone), 'CANCELLED')
})

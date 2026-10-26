package com.smart_hospital_ai.demo.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Appointment;
import com.smart_hospital_ai.demo.entity.AppointmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>,
		JpaSpecificationExecutor<Appointment> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from Appointment a where a.id = :id")
	Optional<Appointment> findByIdForUpdate(@Param("id") Long id);

	boolean existsByPatientId(Long patientId);

	boolean existsByDoctorId(Long doctorId);

	@Query("""
		select case when count(a) > 0 then true else false end
		from Appointment a
		where a.doctor.id = :doctorId
		and a.status = :status
		and a.startAt < :endAt
		and a.endAt > :startAt
		and (:excludingId is null or a.id <> :excludingId)
		""")
	boolean existsDoctorOverlap(@Param("doctorId") Long doctorId,
			@Param("startAt") OffsetDateTime startAt,
			@Param("endAt") OffsetDateTime endAt,
			@Param("status") AppointmentStatus status,
			@Param("excludingId") Long excludingId);

	@Query("""
		select case when count(a) > 0 then true else false end
		from Appointment a
		where a.patient.id = :patientId
		and a.status = :status
		and a.startAt < :endAt
		and a.endAt > :startAt
		and (:excludingId is null or a.id <> :excludingId)
		""")
	boolean existsPatientOverlap(@Param("patientId") Long patientId,
			@Param("startAt") OffsetDateTime startAt,
			@Param("endAt") OffsetDateTime endAt,
			@Param("status") AppointmentStatus status,
			@Param("excludingId") Long excludingId);

	@Override
	@EntityGraph(attributePaths = {"patient", "doctor"})
	Page<Appointment> findAll(Specification<Appointment> specification, Pageable pageable);
}
package com.smart_hospital_ai.demo.repository;

import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Patient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PatientRepository extends JpaRepository<Patient, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Patient p where p.id = :id")
	Optional<Patient> findByIdForUpdate(@Param("id") Long id);

	Optional<Patient> findByUserId(Long userId);

	boolean existsByAssignedDoctorId(Long doctorId);

	@Query("""
		select p from Patient p
		where :term = ''
		or lower(p.firstName) like lower(concat('%', :term, '%'))
		or lower(p.lastName) like lower(concat('%', :term, '%'))
		or lower(p.medicalRecordNumber) like lower(concat('%', :term, '%'))
		or lower(p.user.email) like lower(concat('%', :term, '%'))
		""")
	Page<Patient> search(@Param("term") String term, Pageable pageable);
}
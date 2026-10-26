package com.smart_hospital_ai.demo.repository;

import java.util.Optional;
import com.smart_hospital_ai.demo.entity.Doctor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select d from Doctor d where d.id = :id")
	Optional<Doctor> findByIdForUpdate(@Param("id") Long id);

	@Query("""
		select d from Doctor d
		where :term = ''
		or lower(d.firstName) like lower(concat('%', :term, '%'))
		or lower(d.lastName) like lower(concat('%', :term, '%'))
		or lower(d.specialization) like lower(concat('%', :term, '%'))
		or lower(d.licenseNumber) like lower(concat('%', :term, '%'))
		or lower(d.user.email) like lower(concat('%', :term, '%'))
		""")
	Page<Doctor> search(@Param("term") String term, Pageable pageable);

	Optional<Doctor> findByUserId(Long userId);
}
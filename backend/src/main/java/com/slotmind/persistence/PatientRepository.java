package com.slotmind.persistence;

import com.slotmind.domain.Patient;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, String> {

    /** Finds patients whose name or phone contains the query, ignoring case. */
    @Query("""
            select p from Patient p
            where lower(p.fullName) like lower(concat('%', :query, '%'))
               or p.phone like concat('%', :query, '%')
            order by p.fullName asc
            """)
    List<Patient> search(String query, Pageable page);

    /** Lists patients alphabetically. */
    List<Patient> findAllByOrderByFullNameAsc(Pageable page);

    Optional<Patient> findByPhone(String phone);

    Optional<Patient> findFirstByFullNameIgnoreCase(String fullName);
}

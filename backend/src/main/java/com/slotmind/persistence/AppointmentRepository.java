package com.slotmind.persistence;

import com.slotmind.domain.Appointment;
import com.slotmind.domain.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {

    /** Loads every appointment with its provider and slot. */
    @Query("""
            select a from Appointment a
            join fetch a.provider
            join fetch a.slot s
            join fetch s.provider
            """)
    List<Appointment> findAllWithDetails();

    /** Loads appointments in one status with their provider and slot. */
    @Query("""
            select a from Appointment a
            join fetch a.provider
            join fetch a.slot s
            join fetch s.provider
            where a.status = :status
            """)
    List<Appointment> findByStatusWithDetails(AppointmentStatus status);

    /** Loads one appointment with its provider and slot. */
    @Query("""
            select a from Appointment a
            join fetch a.provider
            join fetch a.slot s
            join fetch s.provider
            where a.id = :id
            """)
    Optional<Appointment> findWithDetailsById(String id);

    /** Loads appointments for one provider in the given status, with slot and provider. */
    @Query("""
            select a from Appointment a
            join fetch a.provider
            join fetch a.slot s
            join fetch s.provider
            where a.provider.id = :providerId and a.status = :status
            """)
    List<Appointment> findByProviderAndStatus(String providerId, AppointmentStatus status);

    /** Loads appointments on one slot in the given status, with slot and provider. */
    @Query("""
            select a from Appointment a
            join fetch a.provider
            join fetch a.slot s
            join fetch s.provider
            where a.slot.id = :slotId and a.status = :status
            """)
    List<Appointment> findBySlotAndStatus(String slotId, AppointmentStatus status);
}

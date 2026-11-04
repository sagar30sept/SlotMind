package com.slotmind.persistence;

import com.slotmind.domain.RescheduleSuggestion;
import com.slotmind.domain.SuggestionStatus;
import com.slotmind.domain.TriggerReason;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {

    /** Loads a suggestion with its appointment, slot, and providers. */
    @Query("""
            select s from RescheduleSuggestion s
            join fetch s.appointment
            join fetch s.recommendedSlot rs
            join fetch rs.provider
            left join fetch s.alternateProvider
            where s.id = :id
            """)
    Optional<RescheduleSuggestion> findWithDetailsById(String id);

    /** Loads suggestions for the given appointments, newest first. */
    @Query("""
            select s from RescheduleSuggestion s
            join fetch s.recommendedSlot rs
            join fetch rs.provider
            left join fetch s.alternateProvider
            where s.appointment.id in :appointmentIds
            order by s.createdAt desc
            """)
    List<RescheduleSuggestion> findByAppointmentIds(Collection<String> appointmentIds);

    /** Finds the pending suggestion for this appointment and trigger, if one exists. */
    Optional<RescheduleSuggestion> findFirstByAppointment_IdAndTriggerReasonAndStatus(
            String appointmentId, TriggerReason triggerReason, SuggestionStatus status);

    /** Loads the suggestion for this appointment, trigger, and status, with its relations. */
    @Query("""
            select s from RescheduleSuggestion s
            join fetch s.appointment a
            join fetch a.provider
            join fetch a.slot sl
            join fetch sl.provider
            join fetch s.recommendedSlot rs
            join fetch rs.provider
            left join fetch s.alternateProvider
            where a.id = :appointmentId
              and s.triggerReason = :triggerReason
              and s.status = :status
            """)
    Optional<RescheduleSuggestion> findPending(
            String appointmentId, TriggerReason triggerReason, SuggestionStatus status);

    /** Loads suggestions for one appointment in the given status, newest first. */
    @Query("""
            select s from RescheduleSuggestion s
            join fetch s.appointment a
            join fetch a.provider
            join fetch a.slot sl
            join fetch sl.provider
            join fetch s.recommendedSlot rs
            join fetch rs.provider
            left join fetch s.alternateProvider
            where a.id = :appointmentId
              and s.status = :status
            order by s.createdAt desc
            """)
    List<RescheduleSuggestion> findByAppointmentAndStatus(String appointmentId, SuggestionStatus status);

    /** Returns slot ids the desk already rejected for this appointment. */
    @Query("""
            select rs.id from RescheduleSuggestion s
            join s.recommendedSlot rs
            where s.appointment.id = :appointmentId
              and s.status = com.slotmind.domain.SuggestionStatus.REJECTED
            """)
    List<String> findRejectedSlotIds(String appointmentId);

    Optional<RescheduleSuggestion> findFirstByAppointment_IdOrderByCreatedAtDesc(String appointmentId);
}

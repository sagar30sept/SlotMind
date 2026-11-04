package com.slotmind.persistence;

import com.slotmind.domain.Slot;
import com.slotmind.domain.SlotStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SlotRepository extends JpaRepository<Slot, String> {

    /** Loads every slot with its provider. */
    @Query("select s from Slot s join fetch s.provider")
    List<Slot> findAllWithProvider();

    /** Loads one slot with its provider. */
    @Query("select s from Slot s join fetch s.provider where s.id = :id")
    Optional<Slot> findWithProviderById(String id);

    /** Loads slots in one status with their providers. */
    @Query("select s from Slot s join fetch s.provider where s.status = :status")
    List<Slot> findByStatusWithProvider(SlotStatus status);
}

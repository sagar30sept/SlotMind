package com.slotmind.persistence;

import com.slotmind.domain.WaitlistEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface WaitlistRepository extends JpaRepository<WaitlistEntry, String> {

    /** Loads waitlist rows with the preferred provider, oldest first. */
    @Query("select w from WaitlistEntry w left join fetch w.preferredProvider order by w.createdAt asc")
    List<WaitlistEntry> findAllWithProvider();
}

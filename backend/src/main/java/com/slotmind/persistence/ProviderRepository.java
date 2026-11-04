package com.slotmind.persistence;

import com.slotmind.domain.Provider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderRepository extends JpaRepository<Provider, String> {
}

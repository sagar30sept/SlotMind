package com.slotmind.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "providers")
public class Provider {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Specialty specialty;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProviderStatus status;

    @Version
    private Long version;

    protected Provider() {
    }

    public Provider(String id, String name, Specialty specialty, ProviderStatus status) {
        this.id = id;
        this.name = name;
        this.specialty = specialty;
        this.status = status;
    }

    /** Sets whether the provider is available for booking. */
    public void changeStatus(ProviderStatus next) {
        this.status = next;
    }

    public boolean isOffline() {
        return status == ProviderStatus.OFFLINE;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Specialty getSpecialty() {
        return specialty;
    }

    public ProviderStatus getStatus() {
        return status;
    }

    public Long getVersion() {
        return version;
    }
}

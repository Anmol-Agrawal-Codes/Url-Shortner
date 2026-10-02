package com.example.urlshortener.url;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import com.example.urlshortener.auth.User;
import com.example.urlshortener.analytics.Click;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "url_mappings", uniqueConstraints = @UniqueConstraint(name = "uk_url_code", columnNames = "code"))
public class UrlMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String originalUrl;

    @Column(length = 40)
    private String code;

    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private User owner;

    @OneToMany(mappedBy = "mapping", cascade = CascadeType.REMOVE, orphanRemoval = true)
    private List<Click> clicks = new ArrayList<>();

    protected UrlMapping() {
    }

    public UrlMapping(String originalUrl, Instant expiresAt, User owner) {
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
        this.owner = owner;
    }

    public Long getId() {
        return id;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void update(String originalUrl, Instant expiresAt) {
        this.originalUrl = originalUrl;
        this.expiresAt = expiresAt;
    }
}

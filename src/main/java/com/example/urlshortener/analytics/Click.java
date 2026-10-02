package com.example.urlshortener.analytics;

import java.time.Instant;
import com.example.urlshortener.url.UrlMapping;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "clicks", indexes = @Index(name = "idx_click_mapping_timestamp", columnList = "mapping_id,clicked_at"))
public class Click {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mapping_id", nullable = false)
    private UrlMapping mapping;

    @Column(name = "clicked_at", nullable = false)
    private Instant timestamp;

    @Column(length = 2048)
    private String referrer;

    protected Click() {
    }

    public Click(UrlMapping mapping, Instant timestamp, String referrer) {
        this.mapping = mapping;
        this.timestamp = timestamp;
        this.referrer = referrer;
    }
}

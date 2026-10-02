package com.example.urlshortener.url;

import java.util.Optional;
import java.time.Instant;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UrlMappingRepository extends JpaRepository<UrlMapping, Long> {

    Optional<UrlMapping> findByCode(String code);

    boolean existsByCode(String code);

    Optional<UrlMapping> findByCodeAndOwnerId(String code, Long ownerId);

    Page<UrlMapping> findByOwnerId(Long ownerId, Pageable pageable);

    @Query("select m.id from UrlMapping m where m.expiresAt < :cutoff order by m.id")
    List<Long> findExpiredIds(Instant cutoff, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UrlMapping m where m.id = :id")
    Optional<UrlMapping> findLockedById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from UrlMapping m where m.code = :code and m.owner.id = :ownerId")
    Optional<UrlMapping> findOwnedForUpdate(String code, Long ownerId);
}

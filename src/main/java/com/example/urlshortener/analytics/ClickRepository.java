package com.example.urlshortener.analytics;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ClickRepository extends JpaRepository<Click, Long> {

    interface DailyCount {
        String getClickDate();
        long getTotal();
    }

    @Query(value = """
            select formatdatetime(clicked_at, 'yyyy-MM-dd', 'en', 'UTC') as clickDate, count(*) as total
            from clicks where mapping_id = :mappingId
            group by formatdatetime(clicked_at, 'yyyy-MM-dd', 'en', 'UTC') order by clickDate
            """, nativeQuery = true)
    List<DailyCount> countPerDay(Long mappingId);
}

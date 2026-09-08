package com.queqiao.server.repo;

import com.queqiao.server.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDate;

@Repository
public interface CalendarEventRepository extends JpaRepository<CalendarEvent, String>, JpaSpecificationExecutor<CalendarEvent> {

    List<CalendarEvent> findByDateBetween(LocalDate from, LocalDate to);

    List<CalendarEvent> findByDate(LocalDate date);

    List<CalendarEvent> findByAgent(String agent);

    List<CalendarEvent> findByOrderByDateAsc();

    boolean existsByDedupKey(String dedupKey);

    List<CalendarEvent> findByCreatedAtAfter(java.time.LocalDateTime t);
}

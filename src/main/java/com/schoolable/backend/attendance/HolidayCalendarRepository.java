package com.schoolable.backend.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface HolidayCalendarRepository extends JpaRepository<HolidayCalendar, UUID> {
    List<HolidayCalendar> findByHolidayDate(LocalDate holidayDate);
    List<HolidayCalendar> findByHolidayDateAndDepartment(LocalDate holidayDate, String department);
    List<HolidayCalendar> findByHolidayDateBetween(LocalDate startDate, LocalDate endDate);
    List<HolidayCalendar> findByHolidayDateBetweenAndDepartment(LocalDate startDate, LocalDate endDate, String department);
}

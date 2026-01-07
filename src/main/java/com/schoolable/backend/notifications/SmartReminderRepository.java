package com.schoolable.backend.notifications;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SmartReminderRepository extends JpaRepository<SmartReminder, Long> {
    
    List<SmartReminder> findAllByOrderByCreatedAtDesc();
    
    List<SmartReminder> findByActiveTrue();
    
    List<SmartReminder> findByType(String type);
}

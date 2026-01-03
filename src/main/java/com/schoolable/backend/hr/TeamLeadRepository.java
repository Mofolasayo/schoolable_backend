package com.schoolable.backend.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeamLeadRepository extends JpaRepository<TeamLeadAppointment, UUID> {
    
    Optional<TeamLeadAppointment> findByEmployeeIdAndStatus(UUID employeeId, String status);
    
    List<TeamLeadAppointment> findByEmployeeIdOrderByAppointedAtDesc(UUID employeeId);
    
    List<TeamLeadAppointment> findByStatus(String status);
    
    List<TeamLeadAppointment> findByStatusIn(List<String> statuses);
    
    @Query("SELECT t FROM TeamLeadAppointment t WHERE t.status IN ('acting', 'confirmed') ORDER BY t.appointedAt DESC")
    List<TeamLeadAppointment> findActiveTeamLeads();
    
    @Query("SELECT t FROM TeamLeadAppointment t WHERE t.department = :department AND t.status IN ('acting', 'confirmed')")
    List<TeamLeadAppointment> findByDepartment(String department);
    
    @Query("SELECT COUNT(t) FROM TeamLeadAppointment t WHERE t.status IN ('acting', 'confirmed')")
    long countActiveTeamLeads();
    
    @Query("SELECT COUNT(t) FROM TeamLeadAppointment t WHERE t.status = 'acting'")
    long countActingTeamLeads();
}

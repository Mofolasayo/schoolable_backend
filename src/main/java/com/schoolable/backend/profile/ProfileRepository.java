package com.schoolable.backend.profile;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProfileRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByEmail(String email);
    Optional<Profile> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
    List<Profile> findByRoleNot(String role);
    List<Profile> findByRole(String role);
    List<Profile> findByDepartment(String department);
    
    // HR Management queries
    List<Profile> findByGradeOrderByFullNameAsc(Integer grade);
    List<Profile> findByJobLevelOrderByFullNameAsc(Integer jobLevel);
    List<Profile> findByIsTeamLeadTrue();
    List<Profile> findByProbationStatus(String probationStatus);
    
    @Query("SELECT p FROM Profile p WHERE p.status = :status AND p.probationStatus = :probationStatus")
    List<Profile> findByStatusAndProbationStatus(String status, String probationStatus);
    
    @Query("SELECT p FROM Profile p WHERE p.isTeamLead = true AND p.status = 'active' ORDER BY p.department, p.fullName")
    List<Profile> findActiveTeamLeads();
    
    @Query("SELECT DISTINCT p.department FROM Profile p WHERE p.department IS NOT NULL ORDER BY p.department")
    List<String> findAllDepartments();
    
    @Query("SELECT COUNT(p) FROM Profile p WHERE p.grade = :grade")
    long countByGrade(Integer grade);
    
    @Query("SELECT COUNT(p) FROM Profile p WHERE p.jobLevel = :level")
    long countByJobLevel(Integer level);

    // Team Lead queries
    @Query("SELECT COUNT(p) FROM Profile p WHERE p.teamLeadId = :teamLeadId")
    long countByTeamLeadId(UUID teamLeadId);
    
    List<Profile> findByTeamLeadId(UUID teamLeadId);
    
    // Active employees for Aura calculation
    List<Profile> findByStatusAndProfileCompletedAtIsNotNull(String status);
    
    // Find by status only
    List<Profile> findByStatus(String status);
    
    // Find by department and status
    List<Profile> findByDepartmentAndStatus(String department, String status);
}


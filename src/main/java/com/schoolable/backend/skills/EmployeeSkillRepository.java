package com.schoolable.backend.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmployeeSkillRepository extends JpaRepository<EmployeeSkill, UUID> {

    List<EmployeeSkill> findByEmployeeIdOrderByProficiencyLevelDesc(UUID employeeId);
    
    List<EmployeeSkill> findBySkillIdOrderByProficiencyLevelDesc(UUID skillId);
    
    Optional<EmployeeSkill> findByEmployeeIdAndSkillId(UUID employeeId, UUID skillId);
    
    List<EmployeeSkill> findByVerifiedByIsNotNullAndEmployeeId(UUID employeeId);
    
    @Query("SELECT es FROM EmployeeSkill es WHERE es.skillId = :skillId AND es.proficiencyLevel >= :minLevel ORDER BY es.proficiencyLevel DESC")
    List<EmployeeSkill> findExpertsForSkill(UUID skillId, Integer minLevel);
    
    long countByEmployeeId(UUID employeeId);
}

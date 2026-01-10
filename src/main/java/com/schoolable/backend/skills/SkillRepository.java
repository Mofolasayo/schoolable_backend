package com.schoolable.backend.skills;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

    List<Skill> findByOrganizationAndIsActiveTrueOrderByNameAsc(String organization);
    
    List<Skill> findByCategoryAndIsActiveTrueOrderByNameAsc(String category);
    
    List<Skill> findByIsActiveTrueOrderByNameAsc();
    
    boolean existsByNameAndOrganization(String name, String organization);
}

package com.schoolable.backend.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface JobLevelRepository extends JpaRepository<JobLevel, UUID> {
    
    Optional<JobLevel> findByLevelNumber(Integer levelNumber);
    
    List<JobLevel> findByGradeOrderByLevelNumberAsc(Integer grade);
    
    List<JobLevel> findAllByOrderByLevelNumberAsc();
    
    List<JobLevel> findByIsTeamLeadEligibleTrueOrderByLevelNumberAsc();
    
    @Query("SELECT jl FROM JobLevel jl WHERE jl.levelNumber >= :minLevel AND jl.levelNumber <= :maxLevel ORDER BY jl.levelNumber ASC")
    List<JobLevel> findByLevelRange(Integer minLevel, Integer maxLevel);
}

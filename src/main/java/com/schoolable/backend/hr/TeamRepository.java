package com.schoolable.backend.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository extends JpaRepository<Team, UUID> {
    Optional<Team> findByNameIgnoreCase(String name);
    List<Team> findAllByOrderByNameAsc();
}

package com.schoolable.backend.recognition;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface RecognitionRepository extends JpaRepository<Recognition, UUID> {

    /**
     * Find recognitions received by a user
     */
    List<Recognition> findByToUserIdOrderByCreatedAtDesc(UUID toUserId);

    /**
     * Find recognitions given by a user
     */
    List<Recognition> findByFromUserIdOrderByCreatedAtDesc(UUID fromUserId);

    /**
     * Find public recognitions (for feed)
     */
    List<Recognition> findByIsPublicTrueOrderByCreatedAtDesc(Pageable pageable);

    /**
     * Find by category
     */
    List<Recognition> findByCategoryOrderByCreatedAtDesc(String category);

    /**
     * Count recognitions received by user
     */
    long countByToUserId(UUID toUserId);

    /**
     * Sum points received by user
     */
    @Query("SELECT COALESCE(SUM(r.points), 0) FROM Recognition r WHERE r.toUserId = :userId")
    int getTotalPointsReceived(UUID userId);

    /**
     * Get top recognized employees (leaderboard)
     */
    @Query("SELECT r.toUserId, COUNT(r) as cnt FROM Recognition r " +
           "WHERE r.createdAt > :since GROUP BY r.toUserId ORDER BY cnt DESC")
    List<Object[]> getTopRecognized(LocalDateTime since, Pageable pageable);

    /**
     * Find recognitions in a department
     */
    List<Recognition> findByDepartmentAndIsPublicTrueOrderByCreatedAtDesc(String department, Pageable pageable);
}

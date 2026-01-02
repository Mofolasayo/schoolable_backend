package com.schoolable.backend.performance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PeerHelpfulnessRepository extends JpaRepository<PeerHelpfulnessRating, UUID> {

    // Get all ratings given by a user for a specific week
    List<PeerHelpfulnessRating> findByRaterIdAndWeekNumberAndYear(UUID raterId, Integer weekNumber, Integer year);

    // Get all ratings received by a user for a specific week
    List<PeerHelpfulnessRating> findByRatedUserIdAndWeekNumberAndYear(UUID ratedUserId, Integer weekNumber, Integer year);

    // Check if user has rated another user for a week
    Optional<PeerHelpfulnessRating> findByRaterIdAndRatedUserIdAndWeekNumberAndYear(
        UUID raterId, UUID ratedUserId, Integer weekNumber, Integer year);

    // Get average rating received by a user in a quarter
    @Query("SELECT AVG(p.rating) FROM PeerHelpfulnessRating p WHERE p.ratedUserId = :userId AND p.year = :year AND p.weekNumber >= :startWeek AND p.weekNumber <= :endWeek")
    Double getAverageRatingForPeriod(
        @Param("userId") UUID userId, 
        @Param("year") Integer year, 
        @Param("startWeek") Integer startWeek, 
        @Param("endWeek") Integer endWeek);

    // Get average rating received by a user for all time
    @Query("SELECT AVG(p.rating) FROM PeerHelpfulnessRating p WHERE p.ratedUserId = :userId")
    Double getOverallAverageRating(@Param("userId") UUID userId);

    // Count how many ratings user has given for a week
    long countByRaterIdAndWeekNumberAndYear(UUID raterId, Integer weekNumber, Integer year);

    // Count how many ratings user has received
    long countByRatedUserId(UUID ratedUserId);

    // Get all ratings in a week for an organization
    List<PeerHelpfulnessRating> findByOrganizationAndWeekNumberAndYear(
        String organization, Integer weekNumber, Integer year);
}

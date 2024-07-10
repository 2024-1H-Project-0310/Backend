package com.techeersalon.moitda.domain.meetings.repository;


import com.techeersalon.moitda.domain.meetings.entity.Meeting;
import org.locationtech.jts.geom.Point;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;


@Repository
public interface MeetingRepository extends PagingAndSortingRepository<Meeting, Long>, JpaRepository<Meeting, Long> {

    // jpql
    @Query(
            value = "SELECT me FROM Meeting me JOIN MeetingParticipant mp ON me.id = mp.meetingId WHERE me.userId = :userId AND mp.isWaiting = false AND me.userId = mp.userId",
            countQuery = "SELECT COUNT(me) FROM Meeting me JOIN MeetingParticipant mp ON me.id = mp.meetingId WHERE me.userId = :userId AND mp.isWaiting = false AND me.userId = mp.userId"
    )
    Page<Meeting> findCreatingRecordsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(
            value = "SELECT me FROM Meeting me JOIN MeetingParticipant mp ON me.id = mp.meetingId WHERE mp.userId = :userId AND mp.isWaiting = false AND me.userId != mp.userId",
            countQuery = "SELECT COUNT(me) FROM Meeting me JOIN MeetingParticipant mp ON me.id = mp.meetingId WHERE mp.userId = :userId AND mp.isWaiting = false AND me.userId != mp.userId"
    )
    Page<Meeting> findParticipationRecordsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query(value = "SELECT * FROM meeting WHERE end_time IS NULL AND ST_Distance_Sphere(location_point, :point) <= 1500 ORDER BY ST_Distance_Sphere(location_point, :point) ASC",
            countQuery = "SELECT count(*) FROM meeting WHERE end_time IS NULL AND ST_Distance_Sphere(location_point, :point) <= 1500",
            nativeQuery = true)
    Page<Meeting> findMeetingByDistance(@Param("point")Point point, Pageable pageable);

    @Query(value = "SELECT * FROM meeting WHERE end_time IS NULL ORDER BY ST_Distance_Sphere(location_point, :point) ASC",
            countQuery = "SELECT count(*) FROM meeting WHERE end_time IS NULL",
            nativeQuery = true)
    Page<Meeting> findAllMeetingByDistance(@Param("point")Point point, Pageable pageable);

    @Query(value = "SELECT * FROM meeting WHERE Category_id = :category AND end_time IS NULL AND ST_Distance_Sphere(location_point, :point) <= 1500 ORDER BY ST_Distance_Sphere(location_point, :point) ASC",
            countQuery = "SELECT count(*) FROM meeting WHERE Category_id = :category AND end_time IS NULL",
            nativeQuery = true)
    Page<Meeting> findByLocationNearAndCategory(@Param("point")Point point, Long category, Pageable pageable);

    @Query(value = "SELECT * FROM meeting WHERE end_time IS NULL AND appointment_time >= NOW() AND ST_Distance_Sphere(location_point, :point) <= 1500 ORDER BY ABS(TIMESTAMPDIFF(SECOND, appointment_time, NOW())) ASC ",
            countQuery = "SELECT count(*) FROM meeting WHERE end_time IS NULL",
            nativeQuery = true)
    Page<Meeting> findPageByClosest(@Param("point")Point point, Pageable pageable);

    @Query(value = "SELECT * FROM meeting WHERE end_time IS NULL AND title LIKE %:keyword% ORDER BY ST_Distance_Sphere(location_point, :point) ASC",
            countQuery = "SELECT count(*) FROM meeting WHERE end_time IS NULL AND title LIKE %:keyword%",
            nativeQuery = true)
    Page<Meeting> findPageByKeyword(String keyword, @Param("point")Point point, Pageable pageable);

    List<Meeting> getMeetingsByUserId(Long userId);
}

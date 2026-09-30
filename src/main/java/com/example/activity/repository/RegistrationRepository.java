package com.example.activity.repository;

import com.example.activity.entity.Registration;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RegistrationRepository extends JpaRepository<Registration, Long> {

    /**
     * 幂等键查找：同一用户、同一 requestId 的唯一绑定。
     */
    Optional<Registration> findByUserIdAndRequestId(long userId, String requestId);

    /**
     * 业务去重查找：同一用户对同一活动的既有报名。
     */
    Optional<Registration> findByUserIdAndActivityId(long userId, long activityId);

    /**
     * 当前用户报名分页：按创建时间降序，同时间按 ID 降序。
     */
    @Query("SELECT r FROM Registration r WHERE r.userId = :userId " +
            "ORDER BY r.createdAt DESC, r.id DESC")
    Page<Registration> findByUserIdOrdered(@Param("userId") long userId, Pageable pageable);
}

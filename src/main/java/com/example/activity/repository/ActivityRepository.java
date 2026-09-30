package com.example.activity.repository;

import com.example.activity.entity.Activity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 活动持久化。名额扣减使用原子条件 UPDATE，由 InnoDB 行锁保证不会超卖：
 * 只有当 remaining_quota &gt; 0 且状态为 OPEN 时才减 1。
 */
public interface ActivityRepository extends JpaRepository<Activity, Long> {

    /**
     * 原子扣减一个名额，返回受影响行数（1 表示扣减成功，0 表示已满/已关闭）。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE activity SET remaining_quota = remaining_quota - 1 " +
            "WHERE id = :id AND remaining_quota > 0 AND status = 'OPEN'", nativeQuery = true)
    int decrementQuota(@Param("id") long id);

    /**
     * 仅查询当前剩余名额（用于缓存命中时回源读取实时余量）。
     */
    @Query("SELECT a.remainingQuota FROM Activity a WHERE a.id = :id")
    Integer findRemainingQuota(@Param("id") long id);
}

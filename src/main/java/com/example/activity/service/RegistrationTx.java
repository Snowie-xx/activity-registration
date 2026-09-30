package com.example.activity.service;

import com.example.activity.entity.Activity;
import com.example.activity.entity.ActivityStatus;
import com.example.activity.entity.Registration;
import com.example.activity.exception.BusinessException;
import com.example.activity.repository.ActivityRepository;
import com.example.activity.repository.RegistrationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 报名的「事务核心」。整段逻辑在同一数据库事务中执行：
 * 插入报名记录与名额扣减要么一起提交、要么一起回滚。
 *
 * <p>判定顺序严格遵循任务书：先身份/参数校验（由 Controller 完成），
 * 再按 ①幂等键重放/参数冲突 → ②业务去重 → ③活动满额/关闭 的顺序判定。
 * 已成功的请求即使活动随后满额，也会在 ① 处直接返回原记录。</p>
 *
 * <p>并发正确性由数据库保证：</p>
 * <ul>
 *   <li>唯一约束 (user_id, request_id) 保证同键并发收敛到同一条记录；</li>
 *   <li>唯一约束 (user_id, activity_id) 保证同用户对同活动只成功一次；</li>
 *   <li>条件 UPDATE {@code WHERE remaining_quota > 0} 原子扣减，杜绝超卖。</li>
 * </ul>
 * 唯一键冲突时本方法抛出 DataIntegrityViolationException，由外层
 * {@link RegistrationService} 捕获后在独立事务中解析为业务响应。
 */
@Service
public class RegistrationTx {

    private final ActivityRepository activityRepository;
    private final RegistrationRepository registrationRepository;

    public RegistrationTx(ActivityRepository activityRepository, RegistrationRepository registrationRepository) {
        this.activityRepository = activityRepository;
        this.registrationRepository = registrationRepository;
    }

    @Transactional
    public RegistrationResult register(long userId, long activityId, String requestId) {
        // ① 幂等键：重放返回原记录；键已被绑定到其它活动则参数冲突
        Optional<Registration> byKey = registrationRepository.findByUserIdAndRequestId(userId, requestId);
        if (byKey.isPresent()) {
            Registration existing = byKey.get();
            if (existing.getActivityId().longValue() == activityId) {
                return RegistrationResult.replay(existing);
            }
            throw BusinessException.idempotencyConflict();
        }

        // ② 业务去重：同一用户对同一活动换键重复报名
        Optional<Registration> byActivity = registrationRepository.findByUserIdAndActivityId(userId, activityId);
        if (byActivity.isPresent()) {
            throw BusinessException.alreadyRegistered();
        }

        // ③ 活动存在性 / 关闭 / 满额（仅用于给出正确业务码，最终以条件 UPDATE 为准）
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(BusinessException::activityNotFound);
        if (activity.getStatus() == ActivityStatus.CLOSED) {
            throw BusinessException.activityClosed();
        }
        if (activity.getRemainingQuota() <= 0) {
            throw BusinessException.soldOut();
        }

        // ④ 先原子扣减名额（条件 UPDATE 立即获取活动行 X 锁）。必须先于插入：
        //    若先插入，外键校验对 activity 的 S 锁与扣减的 X 锁在并发下会形成
        //    S→X 升级死锁（50 并发实测触发）。扣减失败（已满/关闭）则抛异常，整体回滚。
        int updated = activityRepository.decrementQuota(activityId);
        if (updated == 0) {
            Activity fresh = activityRepository.findById(activityId)
                    .orElseThrow(BusinessException::activityNotFound);
            if (fresh.getStatus() == ActivityStatus.CLOSED) {
                throw BusinessException.activityClosed();
            }
            throw BusinessException.soldOut();
        }

        // ⑤ 插入报名并立即 flush。唯一键冲突在并发下由外层解析为
        //    重放 / 参数冲突 / 业务去重；此时事务回滚会一并撤销上面的扣减，
        //    因此失败请求不多扣名额、不绑定 requestId。
        Registration saved = registrationRepository.saveAndFlush(
                new Registration(activityId, userId, requestId, LocalDateTime.now()));

        return RegistrationResult.created(saved);
    }
}

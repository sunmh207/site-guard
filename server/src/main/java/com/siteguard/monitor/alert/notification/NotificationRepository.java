package com.siteguard.monitor.alert.notification;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /// 按 sent_at 倒序分页拉取最近 N 条；用于仪表盘"最近通知"展示与人工排查。
    List<Notification> findAllByOrderBySentAtDesc(Pageable pageable);
}

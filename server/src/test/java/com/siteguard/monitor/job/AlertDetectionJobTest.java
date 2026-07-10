package com.siteguard.monitor.job;

import com.siteguard.monitor.alert.detection.AlertDetectionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AlertDetectionJobTest {

    @Mock
    AlertDetectionService service;

    @InjectMocks
    AlertDetectionJob job;

    @Test
    void executeInternal_delegatesToService() throws JobExecutionException {
        job.executeInternal(null);

        verify(service).detectAll();
    }

    /// 异常被吞掉：单次失败不能影响下一次调度
    @Test
    void executeInternal_swallowsExceptions() {
        doThrow(new RuntimeException("boom")).when(service).detectAll();

        assertDoesNotThrow(() -> job.executeInternal(null));
    }
}
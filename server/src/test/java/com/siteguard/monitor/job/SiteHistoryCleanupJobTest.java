package com.siteguard.monitor.job;

import com.siteguard.monitor.repository.SiteCheckHistoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SiteHistoryCleanupJobTest {

    @Mock
    SiteCheckHistoryRepository historyRepo;

    @InjectMocks
    SiteHistoryCleanupJob job;

    @Test
    void executeInternal_deletesHistoryOlderThan7Days() throws JobExecutionException {
        long before = System.currentTimeMillis();

        job.executeInternal(null);

        var captor = ArgumentCaptor.forClass(Long.class);
        verify(historyRepo).deleteByCheckedAtLessThan(captor.capture());
        long threshold = captor.getValue();
        long sevenDaysMs = Duration.ofDays(7).toMillis();
        long upperBound = before - sevenDaysMs + 5_000;
        long lowerBound = before - sevenDaysMs - 5_000;
        assertTrue(threshold >= lowerBound && threshold <= upperBound,
                "threshold should be ~now-7d, got " + threshold);
    }
}

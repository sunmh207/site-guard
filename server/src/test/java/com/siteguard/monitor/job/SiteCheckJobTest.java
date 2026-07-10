package com.siteguard.monitor.job;

import com.siteguard.monitor.service.SiteCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SiteCheckJobTest {

    @Mock
    SiteCheckService service;

    @InjectMocks
    SiteCheckJob job;

    @Test
    void executeInternal_delegatesToService() throws JobExecutionException {
        job.executeInternal(null);

        verify(service).checkAll();
    }
}

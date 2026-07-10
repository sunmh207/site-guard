package com.siteguard.domain.job;

import com.siteguard.domain.service.DomainCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.quartz.JobExecutionException;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DomainCheckJobTest {

    @Mock
    DomainCheckService service;

    @InjectMocks
    DomainCheckJob job;

    @Test
    void executeInternal_delegatesToService() throws JobExecutionException {
        job.executeInternal(null);

        verify(service).checkAll();
    }
}

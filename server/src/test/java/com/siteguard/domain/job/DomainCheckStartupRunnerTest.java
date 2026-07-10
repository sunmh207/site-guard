package com.siteguard.domain.job;

import com.siteguard.domain.service.DomainCheckService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class DomainCheckStartupRunnerTest {

    @Mock
    DomainCheckService service;

    /// 同步 executor：execute(Runnable) 直接跑，让 verify 立即可见
    private final Executor sync = Runnable::run;

    @Test
    void run_invokesCheckAll() {
        var runner = new DomainCheckStartupRunner(service, sync);

        runner.run(new DefaultApplicationArguments());

        verify(service).checkAll();
    }

    @Test
    void run_checkAllThrows_doesNotPropagate() {
        doThrow(new RuntimeException("boom")).when(service).checkAll();
        var runner = new DomainCheckStartupRunner(service, sync);

        assertDoesNotThrow(() -> runner.run(new DefaultApplicationArguments()));
    }
}
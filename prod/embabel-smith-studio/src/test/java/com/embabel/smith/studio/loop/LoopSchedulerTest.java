package com.embabel.smith.studio.loop;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests the loop schedule gating (change 06): a fresh start runs no iterations; starting the
 * schedule makes each tick run an iteration; stopping halts them. The interval itself is the
 * {@code @Scheduled(fixedDelayString)} wiring (see {@code StudioConfiguration}); the behaviour under
 * test here is the {@link LoopSchedulerState} gate.
 */
@ExtendWith(MockitoExtension.class)
class LoopSchedulerTest {

    @Mock
    LoopService loopService;

    @Test
    void freshStartRunsNoIterations() {
        LoopSchedulerState state = new LoopSchedulerState(false);
        LoopScheduler scheduler = new LoopScheduler(loopService, state);
        scheduler.tick();
        verify(loopService, never()).trigger();
    }

    @Test
    void startRunsIterationsOnEachTick() {
        LoopSchedulerState state = new LoopSchedulerState(false);
        LoopScheduler scheduler = new LoopScheduler(loopService, state);
        state.start();
        scheduler.tick();
        scheduler.tick();
        verify(loopService, times(2)).trigger();
    }

    @Test
    void stopHaltsIterations() {
        LoopSchedulerState state = new LoopSchedulerState(false);
        LoopScheduler scheduler = new LoopScheduler(loopService, state);
        state.start();
        scheduler.tick();
        state.stop();
        scheduler.tick();
        verify(loopService, times(1)).trigger();
    }

    @Test
    void stateReflectsStartStopAndAutoPublish() {
        LoopSchedulerState state = new LoopSchedulerState(false);
        org.assertj.core.api.Assertions.assertThat(state.running()).isFalse();
        state.start();
        org.assertj.core.api.Assertions.assertThat(state.running()).isTrue();
        state.stop();
        org.assertj.core.api.Assertions.assertThat(state.running()).isFalse();
        state.setAutoPublish(true);
        org.assertj.core.api.Assertions.assertThat(state.autoPublish()).isTrue();
    }
}

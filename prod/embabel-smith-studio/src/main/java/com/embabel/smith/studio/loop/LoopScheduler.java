package com.embabel.smith.studio.loop;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The interval scheduler for the self-improvement loop (change 06): a {@code @Scheduled} tick that
 * runs one loop iteration only while the schedule is running (gated by {@link LoopSchedulerState},
 * off by default). The interval is {@code smith.studio.loop.interval} (default 60s). On-demand
 * triggers go through the REST surface, not here.
 */
@Component
public class LoopScheduler {

    private static final Logger log = LoggerFactory.getLogger(LoopScheduler.class);

    private final LoopService loopService;
    private final LoopSchedulerState state;

    public LoopScheduler(LoopService loopService, LoopSchedulerState state) {
        this.loopService = loopService;
        this.state = state;
    }

    @Scheduled(fixedDelayString = "${smith.studio.loop.interval:60000}")
    public void tick() {
        if (!state.running()) {
            return; // the schedule is off; the loop runs only when started or triggered on demand
        }
        log.debug("scheduled loop iteration");
        loopService.trigger();
    }
}

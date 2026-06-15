/**
 * FILE     : Ticker.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 * PURPOSE  : Triggers a periodic callback (tick) on a background scheduler at a fixed rate.
 *            Used by the simulation to advance movement/animation logic on a regular cadence.
 */

package edu.curtin.saed.assignment1.sim;

import java.util.concurrent.*;

/**
 * Small utility around a singlethread  ScheduledExecutorServic e .
 * - start(): begins calling the provided Runnable at fixed   intervals.
 * - stopNow( ): cancels the schedule and shuts the execute r Down.
 */
public final class Ticker {
    private final long periodMs;
    private final Runnable tick;
    private ScheduledExecutorService exec;

    /**
     * Creates a new ticker.
     * @param periodMs Interval between ticks in milliseconds.
     * @param tick     The callback to run each period.
     */
    public Ticker(long periodMs, Runnable tick) {
        this.periodMs = periodMs; this.tick = tick;
    }

    /**
     * Starts the periodic executio n of the tick call back on a single background thread.
     * The first tick runs immediately.
     */
    public void start() {
        exec = Executors.newSingleThreadScheduledExecutor();
        exec.scheduleAtFixedRate(tick, 0, periodMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Sto ps the ticker and interrupts the scheduler t hread if needed.
     * Safe to call multiple times.
     */
    public void stopNow() {
        if (exec != null) { exec.shutdownNow(); }
    }
}

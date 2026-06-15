/**
 * FILE     : ServiceManager.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 * PURPOSE  : Manages a fixed-size thread pool to run plane-servicing tasks asynchronously.
 *            Each task calls StandardPlaneServicing.service(...) for a plane/airport pair,
 *            then always invokes a provided completion callback so the simulation can
 *            return the plane to service or dispatch it again.
 */

package edu.curtin.saed.assignment1.sim;
import edu.curtin.saed.assignment1.StandardPlaneServicing;
import edu.curtin.saed.assignment1.model.Plane;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A light wrapper over an ExecutorServi for running many servicing job s in parallel.
 * - The pool size is fix, chosen by the caller.
 * - The onComplete callback runs on the pool thread   after servicing finishe or interrupted.
 */
public final class ServiceManager {
    // Thread pool used to execute servicing tasks in parallel.
    private final ExecutorService pool;

    /**
     * Creates  new ServiceManager with a thread pool. taht fixedsize
     * @param threads Desired number of worker  threads
     */
    public ServiceManager(int threads) {
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads));
    }

    /**
     * Submit an asynchronous servicing task for the given plane at the given airport.
     * Always calls onComplete at the end (even if servicing is interrupted), so that
     * the simulation can update plane/airport state.
     *
     * @param airportId   Airport where the plane is being serviced.
     * @param plane       The plane to service.
     * @param onComplete  Callback to run after servicing finishes or is interrupted.
     */
    public void serviceAsync(int airportId, Plane plane, Runnable onComplete) {
        pool.submit(() -> {
            try {
                // Simulate servicing work; may block for a random period.
                StandardPlaneServicing.service(airportId, plane.getId());
            } catch (InterruptedException ie) {
                // Preserve interrupt status so higher layers can detect it if needed.
                Thread.currentThread().interrupt();
            } finally {
                // Ensure callback is always invoked to keep the simulation consistent.
                onComplete.run();
            }
        });
    }

    /**
     * Attempts stop all actively executing tasks and halts processing of  . waiting tasks.
     * Used when the simulation is e nding.
     */
    public void shutdownNow() { pool.shutdownNow(); }
}

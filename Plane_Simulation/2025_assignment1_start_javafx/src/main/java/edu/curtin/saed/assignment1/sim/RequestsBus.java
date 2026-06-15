/**
 * FILE     : RequestsBus.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 * PURPOSE  : Provides a simple, thread-safe message bus for flight requests using a BlockingQueue.
 *            Producers (RequestProducer threads) put FlightRequest messages in the queue, and the
 *            Dispatcher thread takes them out. Supports a "poison pill" to end the consumer cleanly.
 */

package edu.curtin.saed.assignment1.sim;
import edu.curtin.saed.assignment1.model.FlightRequest;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * The RequestsBus class is a thin wrapper around a BlockingQueue that carrie  FlightRequest
 * messages between producer threads and the dispatcher thread.
 *
 * Design notes:
 * - put()/take() are used (not add()/remove()) so producers/consumers will block appropriately.
 * - tryPublishPoison() offers a special "poison pill" message to help end the consumer loop.
 */
public class RequestsBus
{
    // The underlying thread-safe queue shared by producers and the dispatcher.
    private final BlockingQueue<FlightRequest> q = new LinkedBlockingQueue<>();

    /**
     * AddsFlightRequest to  queue, blocking if the queue momentarily unable to accept.
     * Called by producer threads.
     *
     * @param r The FlightRequest t publish.
     * @throws InterruptedException If the calling thread is interrupted while waiting to put.
     */
    public void publish(FlightRequest r) throws InterruptedException { q.put(r); } // producers

    /**
     * Retrieve and removes the Next flightRequest from the queue, Blocking until one is available.
     * Called by the dispatcherthread.
     *
     * @return The next FlightRequest from the queue.
     * @throws InterruptedException If the calling thread is interrupted whil waiting take.
     */
    public FlightRequest take() throws InterruptedException { return q.take(); }   // dispatcher

    /**
     * Attempts to publish a special "poison pill" request that signal the consumer to shutdown.
     * Uses offer() so it won't block if the queue is momentarily busy
     */
    public void tryPublishPoison() { q.offer(FlightRequest.poison()); }
}

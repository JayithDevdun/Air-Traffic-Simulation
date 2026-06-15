/**
 FILE : RequestProducer.java
 AUTHOR : <Jayith> (21807457)
 UNIT : COMP3003 - Software Architecture And Extensible Design
 PURPOSE : Starts two per-origin threads—one to generate destinations
 (StandardFlightRequests) and one to read and publish FlightRequest messages to the RequestsBus;
 supports idempotent start and clean stop.
 */

package edu.curtin.saed.assignment1.sim;

import edu.curtin.saed.assignment1.StandardFlightRequests;
import edu.curtin.saed.assignment1.model.FlightRequest;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Produces flight requests for a single origin airport.
 * Starts two threads:
 *  - a generator thread that runs StandardFlightRequests.go()
 *  - a reader thread that reads destinations and publishes them to the RequestsBus
 */
public class RequestProducer
{
    private final int nAirports;
    private final int originId;
    private final RequestsBus bus;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private Thread genThread;
    private Thread readThread;


    public RequestProducer(int nAirports, int originId, RequestsBus bus) {
        this.nAirports = nAirports; this.originId = originId; this.bus = bus;
    }

    /** Start generator + reader threads (no-op if already running). */
    public void start() {
        if (!running.compareAndSet(false, true)) { return; }

        // One StandardFlightRequests instance per origin, as required
        var sfr = new StandardFlightRequests(nAirports, originId);

        // Thread 1: generate destination ids with random delays
        genThread = new Thread(() -> {
            try { sfr.go(); }
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }, "req-gen-" + originId);
        genThread.start();

        // Thread 2: read ids and publish to the blocking queue
        readThread = new Thread(() -> {
            // Reader closes automatically on exit
            try (var localBr = sfr.getBufferedReader()) {           // <-- try-with-resources
                String line;
                while (running.get() && (line = localBr.readLine()) != null) {
                    int dest = Integer.parseInt(line.trim());
                    if (dest != originId) {                          // ignore self-dest
                        try { bus.publish(FlightRequest.of(originId, dest)); }  // blocks if needed
                        catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
                    }
                }
            } catch (IOException ignored) { /* pipe closed on stop */ }
        }, "req-read-" + originId);
        readThread.start();
    }

    /** Stop both threads cleanly (idempotent). */
    public void stop() {
        if (!running.compareAndSet(true, false)) { return; }
        if (readThread != null) { readThread.interrupt(); }  // unblock readLine()/publish
        if (genThread  != null) { genThread.interrupt(); }   // ends StandardFlightRequests.go()
    }

}

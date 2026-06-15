/**
 FILE : Dispatcher.java
 AUTHOR : <Jayith> (21807457)
 UNIT : COMP3003 - Software Architecture And Extensible Design
 PURPOSE : Consumes FlightRequest messages from RequestsBus on a
 background thread and forwards (origin Airport, destId) to a handler; supports clean
 shutdown via poison pill and interrupt.
 */

package edu.curtin.saed.assignment1.sim;

import edu.curtin.saed.assignment1.model.Airport;
import edu.curtin.saed.assignment1.model.FlightRequest;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;

/**
 * Simple dispatcher: reads FlightRequest objects from the queue on its own thread
 * and calls the handler with (originAirport, destId).
 */
public final class Dispatcher implements Runnable {
    private final RequestsBus bus;
    private final List<Airport> airports;
    private final AtomicBoolean running;
    private final BiConsumer<Airport, Integer> handler;
    private Thread thread;

    public Dispatcher(RequestsBus bus,
                      List<Airport> airports,
                      AtomicBoolean running,
                      BiConsumer<Airport, Integer> handler) {
        this.bus = bus;
        this.airports = airports;
        this.running = running;
        this.handler = handler;
    }

    /** Start the background thread. */
    public void start() {
        thread = new Thread(this, "dispatcher");
        thread.start();
    }

    /** Ask the thread to stop: send poison and interrupt in case it's blocked. */
    public void stop() {
        bus.tryPublishPoison();
        if (thread != null) {
            thread.interrupt();
        }
    }

    /** Main loop: take from queue, stop on poison, hand off to handler. */
    @Override
    public void run() {
        try {
            while (running.get()) {
                FlightRequest r = bus.take();   // blocks here
                if (r.isPoison()) {             // cooperative shutdown
                    break;
                }
                handler.accept(airports.get(r.origin()), r.dest());
            }
        } catch (InterruptedException ie) {
            // End quietly if interrupted during shutdown.
            Thread.currentThread().interrupt();
        }
    }
}

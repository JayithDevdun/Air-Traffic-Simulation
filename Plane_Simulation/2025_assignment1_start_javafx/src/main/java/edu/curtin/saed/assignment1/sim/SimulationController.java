/**
 * FILE     : SimulationController.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 * PURPOSE  : Orchestrates the entire airport/plane simulation. Owns the GUI-facing GridArea,
 *            builds the world (airports, planes), starts and stops worker threads (request
 *            producers, dispatcher, ticker, servicing pool), updates model state on each tick,
 *            and safely updates the JavaFX UI.
 */

package edu.curtin.saed.assignment1.sim;

import edu.curtin.saed.assignment1.GridArea;
import edu.curtin.saed.assignment1.GridAreaIcon;
import edu.curtin.saed.assignment1.model.Airport;
import edu.curtin.saed.assignment1.model.Plane;
import javafx.application.Platform;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates simulation lifecycle and threading  :
 * <ul
 *   <li>Builds the world (airports/planes) via  {@link WorldBuilder}.</li
 *   <li>Starts one {@link RequestProducer} per   Origin airport to read flight requests.</li>
 *   <li>Runs a {@link Dispatcher} that consumes requests and dispatches planes.</li>
 *   <li>Ticks movement at 10Hz via {@link Ticker} and Updates the JavaFX UI.</li>
 *   <li>Delegates plane servicing T o   A thread pool via {@link ServiceManager}.</li>
 * </ul>
 * All UI updates   are Wrapped Aith {@code Platform.runLater(...)} to remain JavaFX-safe.
 */
public class SimulationController
{
    // ------------------------------ Configuration ------------------------------
    private final double width, height;
    private final int airportCount, planesPerAirport;
    private final double speed;
    private static final long TICK_MS = 100;
    private final GridArea area;
    private final Label statusText;
    private final TextArea textArea;
    private final List<Airport> airports = new ArrayList<>();
    private final List<Plane>   planes   = new ArrayList<>();
    private final Random rnd = new Random();
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final List<RequestProducer> producers = new ArrayList<>();
    private final RequestsBus requestsBus = new RequestsBus();
    private Dispatcher dispatcher;
    private Ticker ticker;
    private ServiceManager serviceManager;

    // ------------------------------ Statistics ---------------------------------
    private final AtomicInteger inFlight   = new AtomicInteger(0);
    private final AtomicInteger inService  = new AtomicInteger(0);
    private final AtomicInteger totalTrips = new AtomicInteger(0);

    // ------------------------------ Resources ----------------------------------
    // Images are loaded from src/main/resources and must be present there.
    private final Image airportImg = loadRequiredImage("airport_icon.png");
    private final Image planeImg   = loadRequiredImage("plane.png");

    // Layout constants (grid-edge margin and plane image heading offset).
    private static final double EDGE_MARGIN = 0.30;
    private static final double PLANE_IMAGE_HEADING_DEG = 90.0;

    /**
     * Constructs the controller with all required UI and simulation parameters.
     *
     * @param area              The drawing surface (icons grid).
     * @param statusText        Label to show live stats.
     * @param textArea          Text area to show event logs.
     * @param width             Logical grid width (cells).
     * @param height            Logical grid height (cells).
     * @param airportCount      Number of airports to create.
     * @param planesPerAirport  Number of planes parked at each airport.
     * @param speed             Plane speed in grid units per second.
     */
    public SimulationController(GridArea area, Label statusText, TextArea textArea,
                                double width, double height, int airportCount, int planesPerAirport, double speed) {
        this.area = area;
        this.statusText = statusText;
        this.textArea = textArea;
        this.width = width;
        this.height = height;
        this.airportCount = airportCount;
        this.planesPerAirport = planesPerAirport;
        this.speed = speed;
    }

    /**
     * Computes a screen-space bearing (in degrees) from point (x1,y1) to (x2,y2).
     * Y increases downward in screen coordinates;  result is used to rotate plane icons.
     */
    private static double bearingDeg(double x1, double y1, double x2, double y2) {
        return Math.toDegrees(Math.atan2(y2 - y1, x2 - x1));
    }

    // ------------------------------ Lifecycle ----------------------------------
    /**
     * Starts the simulation:
     * <ol>
     *   <li>Clears the uI and (re)builds airports/planes.</li>
     *   <li>Starts reQuest producers and the dispatcher.</li>
     *   <li>Creates the servicing pool and the periodi c ticker.</li >
     * </ol>
     * Saf to call once; subsequent calls are ignored while already running.
     */
    public void start()
    {
        if (!running.compareAndSet(false, true)) { return; }

        clearUi();
        buildWorld();

        // One request producer per origin airport (reads StandardFlightRequests output).
        for (int origin = 0; origin < airportCount; origin++) {
            RequestProducer rp = new RequestProducer(airportCount, origin, requestsBus);
            rp.start();
            producers.add(rp);
        }

        // Dispatcher consumes requests and hands them to dispatch/backlog logic.
        dispatcher = new Dispatcher(requestsBus, airports, running, this::dispatchOrBacklog);
        dispatcher.start();

        // Servicing pool sized to cores and problem scale.
        int cores = Math.max(1, Runtime.getRuntime().availableProcessors());
        int poolSize = Math.min(airportCount * planesPerAirport, cores * 2);
        serviceManager = new ServiceManager(poolSize);

        // Movement tick at 10 Hz.
        ticker = new Ticker(TICK_MS, this::onTick);
        ticker.start();

        log("Simulation started.");
    }

    /**
     * Clear ui text fields (on the JavaFX thread).
     * Calld at the start of a NEw run.
     */
    private void clearUi() {
        Platform.runLater(() -> {
            statusText.setText("Idle");
            textArea.clear();
        });
    }

    /**
     * Initiates a graceful shutdown:
     * <ul>
     *   <li>Stops the ticker, request producers, and    dispatcher.</li>
     *   <li>Shuts down the servicing pool.</li>
     *   <li>Leaves the final state drawn on screen.</l i>
     * </ul>
     * Safe to call multiple tim es.
     */
    public void end()
    {
        if (!running.compareAndSet(true, false)) {
            return;
        }

        if (ticker != null) {
            ticker.stopNow();
        }

        for (RequestProducer rp : producers) {
            rp.stop();
        }
        producers.clear();

        if (dispatcher != null) {
            dispatcher.stop();
        }

        if (serviceManager != null) {
            serviceManager.shutdownNow();
        }

        log("Simulation ended (state left visible).");
        updateStatus();
    }

    /**
     * @return true if the simulation is currently running.
     */
    public boolean isRunning() { return running.get(); }


    // ------------------------------ World Build --------------------------------

    /**
     * Builds airports and planes using {@link WorldBuilder}, replaces the model lists,
     * and requests a UI layout update.
     */
    private void buildWorld()
    {
        // Build the world via helper
        WorldBuilder builder = new WorldBuilder(
                width, height, airportCount, planesPerAirport,
                EDGE_MARGIN,
                airportImg, planeImg,
                rnd
        );
        WorldBuilder.Result res = builder.build(area);

        // Replace model lists
        airports.clear();
        airports.addAll(res.airports);
        planes.clear();
        planes.addAll(res.planes);

        // Finish up UI
        requestLayout();
        updateStatus();
    }


    // ------------------------------ Dispatching --------------------------------

    /**
     * Attempts to dispatch  plane immediately; i f none are available, stores the destination
     * in the origin airport's backlog to be hand led late r.
     * @param origin The origin airport Object (from dispatcher callback).
     * @param destId the Destination airport iD.
     */
    private void dispatchOrBacklog(Airport origin, int destId)
    {
        Plane plane = origin.pollAvailable();
        if (plane != null) {
            dispatchNow(origin, destId, plane);
        }
        else {
            origin.addBacklog(destId);
        }
    }


    /**
     * Marks a plane as in-flight towards a destination and updates its icon on the UI.
     * @param origin Origin airport.
     * @param destId Destination airport ID.
     * @param p      Plane to dispatch.
     */
    private void dispatchNow(Airport origin, int destId, Plane p)
    {
        Airport dest = airports.get(destId);
        p.setInService(false);
        p.setInFlight(true);
        p.setDestAirportId(destId);
        p.setDestXY(dest.getX(), dest.getY());
        inFlight.incrementAndGet();

        // Update icon caption, visibility, and rotation on the JavaFX thread.
        Platform.runLater(() -> {
            p.getIcon().setCaption("Plane " + p.getId());
            p.getIcon().setShown(true);
            double angle = bearingDeg(p.getX(), p.getY(), p.getDestX(), p.getDestY());
            p.getIcon().setRotation(angle + PLANE_IMAGE_HEADING_DEG);
        });

        log(String.format("Departure: Plane %d from Airport %d to Airport %d", p.getId(), origin.getId(), destId));
        updateStatus();
    }


    // ------------------------------ Ticking / Movement -------------------------

    /**
     * Clamps value v to the inclusive range [lo, hi].
     * Used to keep planes within the visible grid.
     */
    private static double clamp(double v, double lo, double hi) {
        return (v < lo) ? lo : (v > hi) ? hi : v;
    }


    /**
     * Called by the {@link Ticker} every 100 ms. Advances planes that are in flight,
     * performs arrival handling (including scheduling servicing), and updates the UI.
     * All JavaFX calls are wrapped with {@code Platform.runLater}.
     */
    private void onTick()
    {
        if (!running.get()) {
            return;
        }
        final double dt = TICK_MS / 1000.0;
        final double maxX = Math.max(0.0, width - 1.0);
        final double maxY = Math.max(0.0, height - 1.0);

        boolean any = false;

        // Advance all planes that are currently flying.
        for (Plane p : planes) {
            if (!p.isInFlight()) {continue;}
            double dx = p.getDestX() - p.getX();
            double dy = p.getDestY() - p.getY();
            double dist = Math.hypot(dx, dy);
            double step = speed * dt;

            if (dist <= step) {
                // Arrived this tick.
                p.setXY(p.getDestX(), p.getDestY());
                p.setInFlight(false);
                any = true;
                onArrival(p.getDestAirportId(), p);
            } else {
                // Move toward destination by "step", clamped to grid.
                double nx = p.getX() + (dx / dist) * step;
                double ny = p.getY() + (dy / dist) * step;
                nx = clamp(nx, 0.0, maxX);
                ny = clamp(ny, 0.0, maxY);
                p.setXY(nx, ny);
                any = true;
            }
        }

        // If anything moved/changed, refresh the icons and status on the UI thread.
        if (any) {
            Platform.runLater(() -> {
                for (Plane p : planes) {
                    if (p.isInFlight()) {
                        p.getIcon().setPosition(p.getX(), p.getY());
                        double angle = bearingDeg(p.getX(), p.getY(), p.getDestX(), p.getDestY());
                        p.getIcon().setRotation(angle + PLANE_IMAGE_HEADING_DEG);
                    } else {
                        p.getIcon().setShown(false);
                        p.getIcon().setPosition(p.getX(), p.getY());
                    }
                }
                area.requestLayout();
                statusText.setText(makeStatusText());
            });
        }
    }


    /**
     * Handles arrival at a destination airport:
     * <ul>
     *   <li>Updates counters and logs the landing.</li>
     *   <li>Submits a servicing job to the {@link ServiceManager}.</li>
     *   <li>On completion, returns the plane to availability and replays any backlog.</li>
     * </ul>
     * @param airportId Destination airport ID.
     * @param p         Plane that just lANded
     */
    private void onArrival(int airportId, Plane p)
    {
        inFlight.decrementAndGet();
        totalTrips.incrementAndGet();
        log(String.format("Landing: Plane %d at Airport %d", p.getId(), airportId));
        updateStatus();

        p.setInService(true);
        inService.incrementAndGet();
        serviceManager.serviceAsync(airportId, p, () -> {
            p.setInService(false);
            inService.decrementAndGet();

            Airport a = airports.get(airportId);
            a.addAvailable(p);
            p.setCurrentAirportId(airportId);
            p.setDestAirportId(-1);

            Integer next = a.pollBacklog();
            if (next != null && running.get()) {
                Plane avail = a.pollAvailable();
                if (avail != null) {
                    dispatchNow(a, next, avail);
                }
                else {
                    a.addBacklog(next);
                }
            }

            updateStatus();
        });
    }


    // ------------------------------ Utilities ----------------------------------
    /**
     * Loads an image from the classpath (src/main/resources). Throws if not found,
     * because missing images break the simulation visuals.
     * @param name Resource filename (e.g., "plane.png").
     * @return Loaded JavaFX {@link Image}.
     */
    private static Image loadRequiredImage(String name) {
        var is = SimulationController.class.getClassLoader().getResourceAsStream(name);
        if (is == null) {
            throw new IllegalStateException(
                    "Missing resource: " + name + " (put it under src/main/resources/)"
            );
        }
        return new Image(is);
    }


    /**
     * Appends a line to the on-screen log on the JavaFX thread.
     * @param s Message to append.
     */
    private void log(String s) {
        Platform.runLater(() -> textArea.appendText(s + "\n"));
    }

    /**
     * Requests a layout pass for the GridArea on the JavaFX thread.
     * Use after batching multiple icon changes.
     */
    private void requestLayout() {
        Platform.runLater(area::requestLayout);
    }

    /**
     * Refreshes the status label with current counters on the JavaFX thread.
     */
    private void updateStatus() {
        Platform.runLater(() -> statusText.setText(makeStatusText()));
    }

    /**
     * Buildds the Status string shown in the tooolbar.
     * @return Human-readable counters for in-flight, sErvicing, and total trips.
     */
    private String makeStatusText() {
        return "In-flight: " + inFlight.get()
                + "   Servicing: " + inService.get()
                + "   Trips: " + totalTrips.get();
    }
}

/**
 * FILE     : WorldBuilder.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 * PURPOSE  : Builds the initial simulation world: places a given number of airports within
 *            a safe margin of the grid and allocates a fixed number of planes to each airport.
 *            Also creates and registers the corresponding GridAreaIcon objects for rendering.
 */

package edu.curtin.saed.assignment1.sim;

import edu.curtin.saed.assignment1.GridArea;
import edu.curtin.saed.assignment1.GridAreaIcon;
import edu.curtin.saed.assignment1.model.Airport;
import edu.curtin.saed.assignment1.model.Plane;
import javafx.scene.image.Image;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Constructs airports and planes for a fresh simulation run.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Clear the UI icon list and repopulate it with airport and plane icons.</li>
 *   <li>Randomly position airports inside a "safe band" so icons/captions don't overflow the grid.</li>
 *   <li>Spawn planes at their origin airports and add them to availability queues.</li>
 * </ul>
 * Output is returned via an immutable {@link Result} record-like class.
 */
public final class WorldBuilder {
    /**
     * Immutable container for the newly built model objects.
     * The controller copies these lists into its own state.
     */
    public static final class Result {
        public final List<Airport> airports;
        public final List<Plane>   planes;
        private Result(List<Airport> airports, List<Plane> planes) {
            this.airports = airports; this.planes = planes;
        }
    }

    // ------------------------------ Inputs -------------------------------------
    private final double width, height;
    private final int airportCount, planesPerAirport;
    private final double edgeMargin;
    private final Image airportImg, planeImg;
    private final Random rnd;
    private static final double CAPTION_MARGIN_Y = 1.00;
    private static final double CAPTION_MARGIN_X = 1.00;

    /**
     * Creates a world builder with all necessary parameters.
     *
     * @param width             grid width (cells)
     * @param height            grid height (cells)
     * @param airportCount      number of airports to place
     * @param planesPerAirport  number of planes at each airport
     * @param edgeMargin        margin from edges to avoid overflow
     * @param airportImg        image for airport icons
     * @param planeImg          image for plane icons
     * @param rnd               randomness source (nullable; a new Random is used if null)
     */
    public WorldBuilder(double width, double height, int airportCount, int planesPerAirport,
                        double edgeMargin,
                        Image airportImg, Image planeImg,
                        Random rnd) {
        this.width = width; this.height = height;
        this.airportCount = airportCount; this.planesPerAirport = planesPerAirport;
        this.edgeMargin = edgeMargin;
        this.airportImg = airportImg; this.planeImg = planeImg;
        this.rnd = (rnd != null) ? rnd : new Random();
    }

    /**
     * Builds a fresh set of airports and planes and installs their icons into the given {@link GridArea}.
     * Steps:
     * <ol>
     *   <li>Clear any existing icons on the GridArea.</li>
     *   <li>Compute a "safe band" (min/max X/Y) that accounts for edge and caption margins.</li>
     *   <li>Randomly place airports within that band, clamping to stay inside.</li>
     *   <li>Create planes for each airport (initially hidden icons), and mark them available.</li>
     * </ol>
     *
     * @param area The drawing surface whose icon list will be repopulated.
     * @return     A {@link Result} containing the created airports and planes.
     */
    public Result build(GridArea area) {
        area.getIcons().clear();

        List<Airport> airports = new ArrayList<>();
        List<Plane>   planes   = new ArrayList<>();

        // Valid logical centres are [0 .. width-1] × [0 .. height-1]
        final double maxX = Math.max(0.0, width  - 1.0);
        final double maxY = Math.max(0.0, height - 1.0);

        // Compute safe band allowing for borders + caption
        final double minSafeX = (width  > 1.0) ? edgeMargin + CAPTION_MARGIN_X : 0.0;
        final double maxSafeX = (width  > 1.0) ? (maxX - edgeMargin - CAPTION_MARGIN_X) : 0.0;

        final double minSafeY = (height > 1.0) ? edgeMargin : 0.0;
        // subtract extra space at the bottom so caption stays inside
        final double maxSafeY = (height > 1.0) ? (maxY - edgeMargin - CAPTION_MARGIN_Y) : 0.0;

        // -------- Airports ---------------------------------------------------
        for (int id = 0; id < airportCount; id++) {
            double x;
            if (minSafeX >= maxSafeX) {
                // too narrow — fall back to centre
                x = maxX * 0.5;
            } else {
                x = minSafeX + rnd.nextDouble() * (maxSafeX - minSafeX);
            }

            double y;
            if (minSafeY >= maxSafeY) {
                // too short — bias upward to avoid bottom-overflow
                y = Math.min(maxY, minSafeY);
            } else {
                y = minSafeY + rnd.nextDouble() * (maxSafeY - minSafeY);
            }

            // Clamp into the safe band to be absolutely sure
            x = clamp(x, Math.min(minSafeX, maxX), Math.max(minSafeX, maxSafeX));
            y = clamp(y, Math.min(minSafeY, maxY), Math.max(minSafeY, maxSafeY));

            Airport a = new Airport(id, x, y);
            airports.add(a);
            area.getIcons().add(new GridAreaIcon(x, y, 0.0, 1.0, airportImg, "Airport " + id));
        }

        // -------- Planes (NP per airport), hidden until flying ---------------
        int pid = 0;
        for (Airport a : airports) {
            for (int i = 0; i < planesPerAirport; i++) {
                Plane p = new Plane(pid++, a.getX(), a.getY());
                p.setCurrentAirportId(a.getId());

                GridAreaIcon pIcon = new GridAreaIcon(
                        p.getX(), p.getY(), 0.0, 1.0, planeImg, "Plane " + p.getId());
                pIcon.setShown(false);  // only visible while flying
                p.setIcon(pIcon);

                planes.add(p);
                area.getIcons().add(pIcon);
                a.addAvailable(p);
            }
        }

        return new Result(airports, planes);
    }

    /**
     * Utility: clamps {@code v} to the inclusive range [{@code lo}, {@code hi}].
     * Handles a degenerate band (lo &gt; hi) by snapping to the closer edge.
     */
    private static double clamp(double v, double lo, double hi) {
        if (lo > hi) {
            // degenerate band: clamp to the closer edge
            double mid = (lo + hi) * 0.5;
            return (v < mid) ? lo : hi;
        }
        return (v < lo) ? lo : Math.min(v, hi);
    }
}

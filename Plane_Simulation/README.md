# Air Traffic Simulation
The project simulates an air traffic network on a 2D grid. Airports generate flight requests; planes are dispatched, fly toward destination airports, land, and undergo asynchronous servicing. The simulation maintains evolving state and shows activity live in a JavaFX UI (map on the left, event log on the right).

## Prerequisites
To build and run this project, ensure you have the following installed:

- Java: Version 17 or higher

- Gradle: Wrapper included (gradlew / gradlew.bat) – no separate install required

- JavaFX: Brought in via Gradle dependencies (no manual setup needed)

## Building the Project
From the project root, run:

```./gradlew build```

## Running the Simulation
To run the app for debugging/development:

```./gradlew run```

## (If the project is in a submodule named 2025_assignment1_start_javafx, you can also use:)

```./gradlew :2025_assignment1_start_javafx:run```

### The simulation will:

- Create a grid-based world with a configurable width/height, a set number of airports (NA), and planes per airport (NP).
- Start one RequestProducer per airport that generates random destination requests; these flow through a thread-safe RequestsBus into a Dispatcher.
- Dispatch planes immediately when available; otherwise backlog requests at the origin airport.
- Move planes in real time at 10 Hz (a Ticker calls the movement step every 100 ms). Plane icons rotate to face their destination.
- On arrival, queue the plane for asynchronous servicing in a fixed thread pool (ServiceManager). After servicing, planes return to the origin’s available pool and any backlog is drained.
- Update a live status bar (In-flight / Servicing / Trips) and append events to the right-hand log panel.
- Run until you click End or close the window (graceful shutdown stops all threads cleanly).

## Cleaning the Project
To remove build outputs and start fresh, use:

```./gradlew clean```

## Linting and Testing
This project is configured to use PMD for code quality checks and (optionally) JUnit 5 for unit tests. To run checks:

```./gradlew check```

## Additional Notes

- app/App.java – JavaFX entry point and UI (toolbar, map, log, Start/End handlers).

- sim/SimulationController.java – Orchestrates threads, movement, dispatch/backlog, servicing, and UI updates.

- sim/WorldBuilder.java – Builds airports/planes and places icons on the GridArea safely within edges/caption margins.

- sim/RequestProducer.java – Per-airport producer reading from StandardFlightRequests and publishing to the bus.

- sim/RequestsBus.java – LinkedBlockingQueue bridge between producers and dispatcher.

- sim/Dispatcher.java – Consumes requests, invokes dispatch/backlog logic.

- sim/ServiceManager.java – Fixed thread pool for StandardPlaneServicing.

- sim/Ticker.java – Scheduled executor that calls the movement tick at 10 Hz.

- model/Airport.java, model/Plane.java, model/FlightRequest.java – Domain state.

- GridArea.java, GridAreaIcon.java – Provided drawing surface and icon model.

- StandardFlightRequests.java, StandardPlaneServicing.java – Provided by unit; do not modify.
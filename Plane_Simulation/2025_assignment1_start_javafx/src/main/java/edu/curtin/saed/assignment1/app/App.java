/**
 * FILE     : RequestsBus.java
 * AUTHOR   : <Jayith> (21807457)
 * UNIT     : COMP3003 - Software Architecture And Extensible Design
 /**
 * PURPOSE  : Launches the JavaFX UI for the Air Traffic Simulator. Builds the toolbar and split-pane
 *            layout, lets the user set grid size/airport/plane/speed parameters, replaces the GridArea
 *            with the chosen dimensions and background image, starts/stops the SimulationController,
 *            shows live status and an event log, and performs a graceful shutdown on End/close.
 */
package edu.curtin.saed.assignment1.app;
import edu.curtin.saed.assignment1.GridArea;
import edu.curtin.saed.assignment1.sim.SimulationController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

/**
 * Launches the JavaFX UI for the Air Traffic Simulator.
 * - Top toolbar: parameter inputs (W, H, NA, NP, S) + Start or End buttons + live status label
 * - Center: SplitPane with the map (GridArea) on the left and an event log TextArea on the right
 * - Handles starting/stopping the simulation and graceful shutdown on window close.
 */

public class App extends Application
{
    private GridArea area;
    private TextArea textArea;
    private Label statusText;
    private SimulationController sim;


    @Override
    public void start(Stage stage)
    {

        // W/H are the Logical Grid dimensions. 1.0 minimum prevents divide-by-zero in scaling.
        Spinner<Double> wSpin  = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(1.0, 10_000, 12.0, 0.1));
        Spinner<Double> hSpin  = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(1.0, 10_000, 12.0, 0.1));
        // NA: number of Airports  (> =2 ensures there is somewhere to fly)
        Spinner<Integer> naSpin = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(2,  5000, 10, 1));
        // NP: planes per airport (>=1 ensures flights can occur)
        Spinner<Integer> npSpin = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(1,  5000, 10, 1));
        // S: plane speed (grid units per second)
        Spinner<Double> sSpin  = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(0.1,  1000, 1.2, 0.1));


        // Allow typing into spinners (not only clicking)
        wSpin.setEditable(true); hSpin.setEditable(true); sSpin.setEditable(true);
        naSpin.setEditable(true); npSpin.setEditable(true);
        // Keep the toolbar compact
        wSpin.setPrefWidth(90); hSpin.setPrefWidth(90); sSpin.setPrefWidth(90);
        naSpin.setPrefWidth(80); npSpin.setPrefWidth(80);

        // Small labels for the toolbar
        var wLbl  = new Label("W:");
        var hLbl  = new Label("H:");
        var naLbl = new Label("NA:");
        var npLbl = new Label("NP:");
        var sLbl  = new Label("S:");

        // ----- Map area (left side of the SplitPane) -------------------------
        // Initial placeholder grid; it will be replaced with the user-sized grid on Start.
        area = new GridArea(12.0, 12.0);

        String bgUrl = App.class.getResource("/map_bg.png").toExternalForm();
        area.setStyle(
                "-fx-background-image: url('" + bgUrl + "');" +
                        "-fx-background-size: cover;" +            // scale to fill while preserving aspect
                        "-fx-background-position: center;" +       // keep it centered
                        "-fx-background-repeat: no-repeat;"        // no tiling
        );

        // ----- Buttons and outputs ------------------------------------------
        var startBtn = new Button("Start");
        var endBtn   = new Button("End");
        endBtn.setDisable(true); // disabled until a simulation is running

        statusText = new Label("Idle");   // updated periodically by SimulationController
        textArea   = new TextArea();      // log of departures/landings/servicing
        textArea.setEditable(false);

        // Toolbar across the top with inputs, Start/End, and status
        var toolbar = new ToolBar(
                new Label("Params "), wLbl, wSpin, hLbl, hSpin, naLbl, naSpin, npLbl, npSpin, sLbl, sSpin,
                new Separator(), startBtn, endBtn, new Separator(), statusText
        );
        ((Region)toolbar).setPadding(new Insets(4,8,4,8));

        // SplitPane: map on the left, event log on the right
        var split = new SplitPane(area, textArea);
        split.setDividerPositions(0.75);

        // Root layout
        var root = new BorderPane();
        root.setTop(toolbar);
        root.setCenter(split);

        // Stage setup
        var scene = new Scene(root, 1200, 900);
        stage.setTitle("Air Traffic Simulator");
        stage.setScene(scene);
        stage.show();


        // ----- Start button handler -----------------------------------------
        startBtn.setOnAction(e -> {
            // No effect if a run is already in progress
            if (sim != null && sim.isRunning()){
                return;
            }

            // Read parameters from UI
            double width  = wSpin.getValue() + 1;
            double height = hSpin.getValue() + 1;
            int    airportCount      = naSpin.getValue();
            int    planesPerAirport  = npSpin.getValue();
            double speed = sSpin.getValue();

            // Basic validation to match assignment constraints
            if (width <= 0.0 || height <= 0.0 || airportCount < 2 || planesPerAirport < 1 || speed <= 0) {
                showError("Invalid parameters", "Respect constraints: W>0, H>0, NA≥2, NP≥1, S>0");
                return;
            }

            try {
                // Recreate the GridArea so its internal scaling matches the chosen W×H
                GridArea newArea = new GridArea(width, height);
                // Preserve the existing background style
                newArea.setStyle(area.getStyle());
                // Swap the component in the SplitPane
                int idx = split.getItems().indexOf(area);
                split.getItems().set(idx, newArea);
                area = newArea;

                // Create and start the simulation controller (spawns worker threads)
                sim = new SimulationController(area, statusText, textArea, width, height, airportCount, planesPerAirport, speed);
                sim.start();

                // UI state: prevent another start; allow ending
                startBtn.setDisable(true);
                endBtn.setDisable(false);
            } catch (IllegalArgumentException | IllegalStateException ex) {
                showError("Couldn't start simulation", String.valueOf(ex.getMessage()));
            }
        });

        // ----- End button handler -------------------------------------------
        endBtn.setOnAction(e -> {
            if (sim == null || !sim.isRunning()){ return; }
            endBtn.setDisable(true);
            // Run shutdown on a background thread to avoid UI hiccups
            new Thread(() -> {
                sim.end();
                // Re-enable Start after shutdown completes
                Platform.runLater(() -> startBtn.setDisable(false));
            }, "end-button-shutdown").start();
        });

        // ----- Window close handler -----------------------------------------
        // Spec: If simulation is running, end it gracefully before closing.
        stage.setOnCloseRequest(ev -> {
            // Block UI while we’re exiting
            startBtn.setDisable(true);
            endBtn.setDisable(true);

            if (sim != null && sim.isRunning()) {
                // Keep the window open until shutdown finishes
                ev.consume();
                new Thread(() -> {
                    sim.end();
                    Platform.runLater(Platform::exit);
                }, "graceful-exit").start();
            } else {
                Platform.exit();
            }
        });
    }

    /**
     * Displays a simple blocking error dialog.
     */
    private void showError(String title, String msg)
    {
        Alert a = new Alert(Alert.AlertType.ERROR, msg, ButtonType.OK);
        a.setHeaderText(title);
        a.showAndWait();
    }

    public static void main(String[] args) { launch(); }
}

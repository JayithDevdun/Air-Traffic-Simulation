package edu.curtin.saed.assignment1.model;

import edu.curtin.saed.assignment1.GridAreaIcon;

public class Plane
{
    private final int id;
    private volatile double x, y;
    private volatile double destX, destY;
    private volatile boolean inFlight = false;
    private volatile boolean inService = false;
    private volatile int currentAirportId = -1;
    private volatile int destAirportId = -1;
    private GridAreaIcon icon;

    public Plane(int id, double x, double y) { this.id = id; this.x = x; this.y = y; }
    public int getId() { return id; }

    public double getX() { return x; }
    public double getY() { return y; }
    public void setXY(double x, double y) { this.x = x; this.y = y; }

    public double getDestX() { return destX; }
    public double getDestY() { return destY; }
    public void setDestXY(double dx, double dy) { this.destX = dx; this.destY = dy; }

    public boolean isInFlight() { return inFlight; }
    public void setInFlight(boolean v) { this.inFlight = v; }

    public boolean isInService() { return inService; }
    public void setInService(boolean v) { this.inService = v; }

    public int getCurrentAirportId() { return currentAirportId; }
    public void setCurrentAirportId(int id) { this.currentAirportId = id; }

    public int getDestAirportId() { return destAirportId; }
    public void setDestAirportId(int id) { this.destAirportId = id; }

    public GridAreaIcon getIcon() { return icon; }
    public void setIcon(GridAreaIcon icon) { this.icon = icon; }
}

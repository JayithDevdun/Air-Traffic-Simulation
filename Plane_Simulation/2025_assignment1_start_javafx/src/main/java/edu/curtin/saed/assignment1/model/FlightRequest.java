package edu.curtin.saed.assignment1.model;

public final class FlightRequest
{
    private final int origin, dest;
    private final boolean poison;

    private FlightRequest(int origin, int dest, boolean poison) {
        this.origin = origin; this.dest = dest; this.poison = poison;
    }

    public static FlightRequest of(int origin, int dest) { return new FlightRequest(origin, dest, false); }
    public static FlightRequest poison() { return new FlightRequest(-1, -1, true); }

    public int origin() { return origin; }
    public int dest() { return dest; }
    public boolean isPoison() { return poison; }
}

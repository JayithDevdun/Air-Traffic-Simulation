package edu.curtin.saed.assignment1.model;

import java.util.ArrayDeque;
import java.util.Deque;

public class Airport
{
    private final int id;
    private final double x, y;

    private final Object lock = new Object();
    private final Deque<Integer> backlog = new ArrayDeque<>();
    private final Deque<Plane>   available = new ArrayDeque<>();

    public Airport(int id, double x, double y) { this.id = id; this.x = x; this.y = y; }
    public int getId() { return id; }
    public double getX() { return x; }
    public double getY() { return y; }

    public void addBacklog(int destId) { synchronized (lock) { backlog.addLast(destId); } }
    public Integer pollBacklog()        { synchronized (lock) { return backlog.pollFirst(); } }
    public void addAvailable(Plane p)   { synchronized (lock) { available.addLast(p); } }
    public Plane pollAvailable()        { synchronized (lock) { return available.pollFirst(); } }
}

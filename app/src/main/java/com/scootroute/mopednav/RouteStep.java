package com.scootroute.mopednav;

/**
 * Represents a single navigation step from ORS.
 */
public class RouteStep {
    public String instruction;   // "Turn right onto Kalakaua Ave"
    public String type;          // "turn-right", "turn-left", "straight", "arrive"
    public double distanceMeters;
    public double waypointLat;   // GPS coords of this step's endpoint
    public double waypointLng;

    public RouteStep(String instruction, String type,
                     double distanceMeters,
                     double waypointLat, double waypointLng) {
        this.instruction   = instruction;
        this.type          = type;
        this.distanceMeters= distanceMeters;
        this.waypointLat   = waypointLat;
        this.waypointLng   = waypointLng;
    }
}

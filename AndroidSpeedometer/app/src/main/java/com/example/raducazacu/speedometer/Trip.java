package com.example.raducazacu.speedometer;

/**
 * Created by Radu.Cazacu on 12/28/2016.
 */

public class Trip {

    private int wheelLenght_mm; // in mm

    public double CurrentSpeed_mps; // in m/s
    public double Distance_m; // in m
    public double Duration_s; // in s
    public double AvgSpeed_mps; // in m/s
    public double MaxSpeed_mps; // in m/s

    public Trip(int wheelLenght_mm)
    {
        this.wheelLenght_mm = wheelLenght_mm;
    }

    /**
     * Updates the values of the trip according to the input data
     * @param rotations - no of rotations read in the specified duration
     * @param duration_ms - period of time when the rotations were scanned
     * @param totalRotation - total number of rotations detected from the first detected rotation
     * @param totalDuration_ms - total duration of the wheel movement - if wheel stopped the totalDuration is paused
     */
    public void Update(long rotations, long duration_ms, long totalRotation, long totalDuration_ms)
    {
        double sampleDistance_m = (rotations * wheelLenght_mm) * Math.pow(10,-3);

        if(duration_ms != 0)
            CurrentSpeed_mps = sampleDistance_m / (duration_ms * Math.pow(10, -3));

        Distance_m = (totalRotation * wheelLenght_mm) * Math.pow(10,-3);

        Duration_s = totalDuration_ms * Math.pow(10, -3);

        if(Duration_s != 0)
            AvgSpeed_mps = Distance_m / Duration_s;

        if(MaxSpeed_mps < CurrentSpeed_mps)
            MaxSpeed_mps = CurrentSpeed_mps;
    }
    public String getSpeedKmph(double speed_mps)
    {
        double speed_kmph = speed_mps * 3600 * Math.pow(10, -3);
        return String.format("%.1f", speed_kmph);
    }
    public String getDistanceKm(double dist_m)
    {
        double distance_km = dist_m * Math.pow(10, -3);
        return String.format("%.2f", distance_km);
    }
    public String getDuration(double duration_s)
    {
        short hours = (short)Math.abs(duration_s / 3600);
        short minutes = (short)Math.abs((duration_s - (hours*3600)) / 60);
        short seconds = (short)(duration_s - (hours*3600) - (minutes*60));
        return String.format("%1sh.%2sm:%3ss", hours, minutes, seconds);
    }
}

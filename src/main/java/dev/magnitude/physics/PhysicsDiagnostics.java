package dev.magnitude.physics;

/** Constant-memory measurements for the most recent 128 movement queries on this logical side. */
public final class PhysicsDiagnostics {
    public final TimingWindow timings=new TimingWindow(128);
    public int samples, denied;
    public long totalNanos, maximumNanos;
    public String failure="none";
    public void movement(long nanos,boolean rejected) {
        timings.add(nanos);
        if(samples>=128) {samples=0;denied=0;totalNanos=0;maximumNanos=0;}
        samples++;totalNanos+=nanos;maximumNanos=Math.max(maximumNanos,nanos);
        if(rejected)denied++;
    }
    public double averageMillis() {return samples==0?0:totalNanos/(samples*1_000_000.0);}
}

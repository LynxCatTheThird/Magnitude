package dev.magnitude.physics;

import java.util.Arrays;

/** Fixed storage. Percentile work happens only on explicit diagnostic reads. */
public final class TimingWindow {
    private final long[] samples;
    private int cursor,count;
    public record Summary(int samples,double p50,double p95,double p99,double maximum) {}
    public TimingWindow(int capacity){if(capacity<1||capacity>4096)throw new IllegalArgumentException("capacity");samples=new long[capacity];}
    public void add(long nanos){if(nanos<0)return;samples[cursor]=nanos;cursor=(cursor+1)%samples.length;count=Math.min(samples.length,count+1);}
    public void clear(){cursor=0;count=0;}
    public Summary summary(){
        if(count==0)return new Summary(0,0,0,0,0);
        var sorted=Arrays.copyOf(samples,count);Arrays.sort(sorted);
        return new Summary(count,percentile(sorted,.5),percentile(sorted,.95),percentile(sorted,.99),sorted[count-1]/1_000_000d);
    }
    private static double percentile(long[] sorted,double p){return sorted[Math.max(0,(int)Math.ceil(sorted.length*p)-1)]/1_000_000d;}
}

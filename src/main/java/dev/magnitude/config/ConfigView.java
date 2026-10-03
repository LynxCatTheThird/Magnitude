package dev.magnitude.config;

import java.util.Map;

/** Read-only, non-sensitive state used by both commands and the client menu. */
public record ConfigView(long revision,long personalRevision,boolean administrator,Map<String,Double> server,
                         Map<String,Double> personal,String result,String detail,double size,
                         boolean terrainEffective,boolean pressureEffective,String terrainReason,String pressureReason,
                         double serverTickMs,double moveAvgMs,double moveMaxMs,int denied,int samples,
                         int pending,String lastFailure,String reason,dev.magnitude.physics.TimingWindow.Summary tickTimings,
                         dev.magnitude.physics.TimingWindow.Summary moveTimings,int cellsUsed,int pairsUsed,int materialChecks,int blockWrites,long rejectionEvents) {}

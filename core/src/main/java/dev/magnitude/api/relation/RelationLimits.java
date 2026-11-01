package dev.magnitude.api.relation;

/** All memory and graph bounds are explicit, including retained FREE revisions. */
public record RelationLimits(int maxPerHost, int maxRelations, int maxTrackedTargets,
                             int maxDepth, int dedupCapacity, int maxActors) {
    /** Default actor bound includes target and host identities; neither grows with traffic. */
    public RelationLimits(int maxPerHost, int maxRelations, int maxTrackedTargets, int maxDepth, int dedupCapacity) {
        this(maxPerHost, maxRelations, maxTrackedTargets, maxDepth, dedupCapacity, defaultActorLimit(maxTrackedTargets, maxRelations));
    }

    private static int defaultActorLimit(int targets, int relations) {
        long total = (long) targets + relations;
        if (total < 1 || total > Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid default actor bound");
        return (int) total;
    }

    public RelationLimits {
        if (maxPerHost < 1 || maxRelations < 1 || maxTrackedTargets < maxRelations
                || maxDepth < 1 || dedupCapacity < 1 || maxActors < 1) {
            throw new IllegalArgumentException("Positive bounds and maxTrackedTargets >= maxRelations required");
        }
    }
}

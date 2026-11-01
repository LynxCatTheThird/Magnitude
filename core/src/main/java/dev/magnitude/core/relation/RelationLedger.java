package dev.magnitude.core.relation;

import dev.magnitude.api.relation.*;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded in-memory implementation. Construct and access on the server owner
 * thread. It does not persist state or move entities. A world adapter must verify
 * geometry and authorization before submitting, and execute side effects only
 * for changed() results. Actor sequence high-water marks (including rejected requests) and FREE target
 * revisions are retained for this epoch. Bounded response caching never turns an
 * evicted, previously rejected request into a new command. Actor/target capacity
 * exhaustion rejects new identities; records are never dropped to make space.
 * Accepted holds reserve both participants' exit identities before commit.
 */
public final class RelationLedger implements RelationService {
    private final UUID epoch;
    private final RelationLimits limits;
    private final Thread owner = Thread.currentThread();
    private final Map<UUID, RelationSnapshot> states = new HashMap<>();
    private final Map<UUID, Set<UUID>> children = new HashMap<>();
    private final LinkedHashMap<RequestKey, Decision> decisions = new LinkedHashMap<>();
    private final Map<UUID, Long> sequenceHighWater = new HashMap<>();
    private int heldCount;

    public RelationLedger(UUID epoch, RelationLimits limits) {
        this.epoch = Objects.requireNonNull(epoch, "epoch");
        this.limits = Objects.requireNonNull(limits, "limits");
    }

    @Override public UUID epoch() { return epoch; }

    @Override public RelationSnapshot snapshot(UUID target) {
        checkThread();
        Objects.requireNonNull(target, "target");
        RelationSnapshot current = states.get(target);
        return current != null ? current : RelationSnapshot.free(target);
    }

    @Override public Map<UUID, RelationSnapshot> snapshotAll() {
        checkThread();
        return Map.copyOf(states);
    }

    @Override public int heldCount() { checkThread(); return heldCount; }

    @Override public int occupiedSlots(UUID host) {
        checkThread();
        Objects.requireNonNull(host, "host");
        return children.getOrDefault(host, Set.of()).size();
    }

    @Override public RelationResult submit(RelationRequest request, RelationAuthorization authorization) {
        checkThread();
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(authorization, "authorization");
        // Authentication is checked before the cache to prevent impersonated replays.
        if (!authorization.permits(request)) return reject(request, RelationReason.UNAUTHORIZED, false);
        if (!epoch.equals(request.epoch())) return reject(request, RelationReason.STALE_EPOCH, false);
        RequestKey key = new RequestKey(request.actor(), request.requestId());
        Decision prior = decisions.get(key);
        if (prior != null) {
            if (prior.request.equals(request)) return prior.result.asReplay();
            // A changed payload is still an authenticated rejected command. Its
            // fresh sequence must be consumed before this cache entry can be
            // evicted, otherwise the same conflict could later become valid.
            sequenceHighWater.compute(request.actor(), (actor, previous) ->
                    previous == null ? request.sequence() : Math.max(previous, request.sequence()));
            return reject(request, RelationReason.REQUEST_ID_CONFLICT, false);
        }
        Long highWater = sequenceHighWater.get(request.actor());
        if (highWater != null && request.sequence() <= highWater) {
            return reject(request, RelationReason.STALE_REQUEST, false);
        }
        if (highWater == null && sequenceHighWater.size() >= limits.maxActors()) {
            return reject(request, RelationReason.ACTOR_CAPACITY, false);
        }
        // An authorized, fresh command consumes its sequence even if its
        // revision, capacity or graph checks below reject it. Otherwise an old
        // rejection could be resurrected when the response cache is evicted.
        sequenceHighWater.put(request.actor(), request.sequence());
        RelationResult result;
        RelationSnapshot before = snapshot(request.target());
        if (before.revision() != request.expectedRevision()) {
            result = reject(request, RelationReason.STALE_REVISION, true);
        } else {
            result = switch (request.operation()) {
                case HOLD -> hold(request, before);
                case RELEASE -> release(request, before);
                case COMPLETE_RECOVERY -> completeRecovery(request, before);
            };
        }
        decisions.put(key, new Decision(request, result));
        if (decisions.size() > limits.dedupCapacity()) decisions.remove(decisions.keySet().iterator().next());
        return result;
    }

    private RelationResult hold(RelationRequest request, RelationSnapshot before) {
        UUID host = request.host().orElseThrow();
        if (request.target().equals(host)) return reject(request, RelationReason.SELF_HOLD, true);
        if (before.state() == RelationState.HELD) return reject(request, RelationReason.TARGET_HELD, true);
        if (before.state() == RelationState.RECOVERY) return reject(request, RelationReason.TARGET_RECOVERING, true);
        if (snapshot(host).state() == RelationState.RECOVERY) return reject(request, RelationReason.HOST_RECOVERING, true);
        if (occupiedSlots(host) >= limits.maxPerHost()) return reject(request, RelationReason.HOST_CAPACITY, true);
        if (heldCount >= limits.maxRelations()) return reject(request, RelationReason.GLOBAL_CAPACITY, true);
        if (!states.containsKey(request.target()) && states.size() >= limits.maxTrackedTargets()) {
            return reject(request, RelationReason.TRACKING_CAPACITY, true);
        }
        int ancestorDepth = 0;
        UUID ancestor = host;
        while (true) {
            if (ancestor.equals(request.target())) return reject(request, RelationReason.CYCLE, true);
            Optional<UUID> parent = snapshot(ancestor).host();
            if (parent.isEmpty()) break;
            ancestor = parent.orElseThrow();
            ancestorDepth++;
        }
        if ((long) ancestorDepth + 1 + descendantDepth(request.target()) > limits.maxDepth()) {
            return reject(request, RelationReason.DEPTH_LIMIT, true);
        }
        // Both participants must retain the ability to send their first exit
        // request even if a third-party administrator submitted this HOLD.
        // Reserve only after every ordinary hold check succeeds.
        int exitIdentities = (sequenceHighWater.containsKey(host) ? 0 : 1)
                + (sequenceHighWater.containsKey(request.target()) ? 0 : 1);
        if (exitIdentities > limits.maxActors() - sequenceHighWater.size()) {
            return reject(request, RelationReason.ACTOR_CAPACITY, true);
        }
        RelationSnapshot after = new RelationSnapshot(request.target(), RelationState.HELD,
                Math.addExact(before.revision(), 1), Optional.of(host));
        sequenceHighWater.putIfAbsent(host, 0L);
        sequenceHighWater.putIfAbsent(request.target(), 0L);
        states.put(request.target(), after);
        children.computeIfAbsent(host, ignored -> new HashSet<>()).add(request.target());
        heldCount++;
        return applied(request, after);
    }

    private RelationResult release(RelationRequest request, RelationSnapshot before) {
        if (before.state() == RelationState.FREE) {
            return new RelationResult(request.requestId(), RelationResult.Status.UNCHANGED,
                    RelationReason.ALREADY_FREE, Optional.of(before), false);
        }
        if (before.state() != RelationState.HELD) return reject(request, RelationReason.NOT_HELD, true);
        if (!before.host().equals(request.host())) return reject(request, RelationReason.HOST_MISMATCH, true);
        UUID host = before.host().orElseThrow();
        RelationState state = request.placement() == PlacementStatus.SAFE ? RelationState.FREE : RelationState.RECOVERY;
        RelationSnapshot after = new RelationSnapshot(request.target(), state,
                Math.addExact(before.revision(), 1), Optional.empty());
        Set<UUID> occupied = children.get(host);
        occupied.remove(request.target());
        if (occupied.isEmpty()) children.remove(host);
        heldCount--;
        states.put(request.target(), after);
        return applied(request, after);
    }

    private RelationResult completeRecovery(RelationRequest request, RelationSnapshot before) {
        if (before.state() != RelationState.RECOVERY) return reject(request, RelationReason.NOT_RECOVERING, true);
        RelationSnapshot after = new RelationSnapshot(request.target(), RelationState.FREE,
                Math.addExact(before.revision(), 1), Optional.empty());
        states.put(request.target(), after);
        return applied(request, after);
    }

    private int descendantDepth(UUID target) {
        ArrayDeque<DepthNode> pending = new ArrayDeque<>();
        pending.add(new DepthNode(target, 0));
        int maximum = 0;
        while (!pending.isEmpty()) {
            DepthNode node = pending.remove();
            maximum = Math.max(maximum, node.depth);
            for (UUID child : children.getOrDefault(node.id, Set.of())) {
                pending.add(new DepthNode(child, node.depth + 1));
            }
        }
        return maximum;
    }

    private RelationResult applied(RelationRequest request, RelationSnapshot state) {
        return new RelationResult(request.requestId(), RelationResult.Status.APPLIED,
                RelationReason.APPLIED, Optional.of(state), false);
    }

    private RelationResult reject(RelationRequest request, RelationReason reason, boolean discloseState) {
        return new RelationResult(request.requestId(), RelationResult.Status.REJECTED, reason,
                discloseState ? Optional.of(snapshot(request.target())) : Optional.empty(), false);
    }

    private void checkThread() {
        if (Thread.currentThread() != owner) throw new IllegalStateException("RelationLedger requires its owner thread");
    }

    private record RequestKey(UUID actor, UUID requestId) {}
    private record Decision(RelationRequest request, RelationResult result) {}
    private record DepthNode(UUID id, int depth) {}
}

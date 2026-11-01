package example.magnitude;

import dev.magnitude.api.math.FiniteVec3;
import dev.magnitude.api.math.YawTransform;
import dev.magnitude.api.relation.PlacementStatus;
import dev.magnitude.api.relation.RelationAuthorization;
import dev.magnitude.api.relation.RelationLimits;
import dev.magnitude.api.relation.RelationOperation;
import dev.magnitude.api.relation.RelationReason;
import dev.magnitude.api.relation.RelationRequest;
import dev.magnitude.api.relation.RelationResult;
import dev.magnitude.api.relation.RelationService;
import dev.magnitude.api.relation.RelationState;
import dev.magnitude.api.relation.Relations;
import dev.magnitude.api.scale.ScaleLimits;
import dev.magnitude.api.scale.ScaleSnapshot;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** A standalone consumer: only public API imports and a published Maven dependency. */
public final class CoreConsumer {
    private static int checks;

    private CoreConsumer() {}

    public static void main(String[] args) {
        scaleAndCoordinates();
        relationSession();
        System.out.println("core-consumer: " + checks + " public API checks passed.");
        System.out.println("No Minecraft world, networking, persistence or physical movement was executed.");
    }

    private static void scaleAndCoordinates() {
        ScaleLimits limits = new ScaleLimits(0.25, 4.0);
        check(limits.requireValid(2.0) == 2.0, "Valid scale was unexpectedly changed");
        expectIllegal(() -> limits.requireValid(Double.NaN), "NaN accepted");
        expectIllegal(() -> limits.requireValid(8.0), "Unsupported scale accepted");

        ScaleSnapshot snapshot = new ScaleSnapshot(id(1), 7, 2, 2, 1.2, 3.6, 3.24);
        check(snapshot.observedWidth() == 1.2, "Observed world dimensions were scaled twice");
        check(snapshot.revision() == 7, "Snapshot revision changed");

        // World-block offsets, already converted from model units by the caller.
        // The public API uses -Z forward; +90 degrees around +Y turns forward to -X.
        YawTransform handFrame = new YawTransform(new FiniteVec3(10, 64, 20), 90);
        FiniteVec3 localOffset = new FiniteVec3(0, 1, -2);
        FiniteVec3 world = handFrame.localToWorld(localOffset);
        check(world.equals(new FiniteVec3(8, 65, 20)), "Unexpected public yaw convention");
        check(handFrame.worldToLocal(world).equals(localOffset), "Local coordinate round-trip failed");
        expectIllegal(() -> new FiniteVec3(0, Double.POSITIVE_INFINITY, 0), "Non-finite vector accepted");
    }

    private static void relationSession() {
        UUID epoch = id(100);
        UUID host = id(1);
        UUID target = id(2);
        RelationLimits limits = new RelationLimits(1, 4, 8, 4, 8, 8);
        RelationService relations = Relations.create(epoch, limits);
        check(relations.snapshot(target).state() == RelationState.FREE, "Unknown target must start free");

        RelationRequest hold = RelationRequest.hold(epoch, id(101), 1, host, host, target, 0);
        RelationResult held = relations.submit(hold, trustedDemoGrant(hold));
        check(held.changed() && held.reason() == RelationReason.APPLIED, "Hold was not applied");
        check(relations.snapshot(target).state() == RelationState.HELD, "Target not held");
        check(relations.occupiedSlots(host) == 1, "Host capacity was not reserved once");

        RelationResult replay = relations.submit(hold, trustedDemoGrant(hold));
        check(replay.replayed() && !replay.changed(), "Replay permits duplicate side effects");
        check(relations.snapshot(target).revision() == 1, "Replay advanced target revision");

        RelationRequest conflictingPayload = RelationRequest.hold(epoch, hold.requestId(), 1, host,
                id(3), target, 0);
        RelationResult conflict = relations.submit(conflictingPayload, trustedDemoGrant(conflictingPayload));
        check(conflict.reason() == RelationReason.REQUEST_ID_CONFLICT, "Request ID accepted new payload");

        RelationRequest blocked = RelationRequest.hold(epoch, id(102), 2, host, host, id(4), 0);
        check(relations.submit(blocked, trustedDemoGrant(blocked)).reason() == RelationReason.HOST_CAPACITY,
                "Full host accepted another target");

        // A world-aware adapter would determine placement. This demonstration only supplies
        // the decision and verifies the relation contract; it does not claim an exit exists.
        RelationRequest release = RelationRequest.release(epoch, id(103), 1, target, host, target,
                relations.snapshot(target).revision(), PlacementStatus.PENDING);
        RelationResult recovering = relations.submit(release, trustedDemoGrant(release));
        check(recovering.changed(), "Release did not revoke the old relation");
        check(relations.snapshot(target).state() == RelationState.RECOVERY, "Pending exit not represented");
        check(relations.occupiedSlots(host) == 0 && relations.heldCount() == 0,
                "Recovery still occupies the old host slot");
        check(relations.snapshot(target).host().isEmpty(), "Recovery retains old host authority");

        RelationResult repeatedRelease = relations.submit(release, trustedDemoGrant(release));
        check(repeatedRelease.replayed() && !repeatedRelease.changed(), "Release replay changed state");

        RelationRequest placed = RelationRequest.completeRecovery(epoch, id(104), 2, target, target,
                relations.snapshot(target).revision());
        check(relations.submit(placed, trustedDemoGrant(placed)).reason() == RelationReason.UNAUTHORIZED,
                "A request claiming SAFE placement authorized itself");
        // This explicit grant stands for a trusted adapter's verified placement decision.
        // The demo has no world, so it tests only the authorization contract.
        RelationAuthorization placementConfirmed = new RelationAuthorization(target, target,
                Optional.empty(), Set.of(RelationOperation.COMPLETE_RECOVERY), Set.of(PlacementStatus.SAFE));
        check(relations.submit(placed, placementConfirmed).changed(), "Safe placement not applied");
        check(relations.snapshot(target).state() == RelationState.FREE, "Safe placement did not free target");
        check(relations.snapshot(target).revision() == 3, "Unexpected lifecycle revision count");

        // Sequences are scoped per actor and epoch. The host's accepted latest sequence
        // is 2 (the capacity rejection also consumed it); a new ID cannot reuse that sequence.
        RelationRequest staleSequence = RelationRequest.hold(epoch, id(105), 2, host, host, target, 3);
        check(relations.submit(staleSequence, trustedDemoGrant(staleSequence)).reason()
                == RelationReason.STALE_REQUEST, "New request ID reused an old actor sequence");
        check(relations.snapshot(target).state() == RelationState.FREE, "Stale sequence altered target");

        // A new in-memory service is a new session, not a demonstration of disk recovery.
        RelationService nextSession = Relations.create(id(200), limits);
        check(nextSession.submit(hold, trustedDemoGrant(hold)).reason() == RelationReason.STALE_EPOCH,
                "A new session accepted an old-session request");
        check(nextSession.heldCount() == 0, "Stale epoch altered the new session");
    }

    /**
     * This example is itself the trusted server-side caller. This default grant never
     * authorizes SAFE placement. A real network adapter must resolve actor identity,
     * target consent and world checks independently before granting.
     * Never replace those checks with a grant reconstructed from an untrusted client request.
     */
    private static RelationAuthorization trustedDemoGrant(RelationRequest request) {
        return new RelationAuthorization(request.actor(), request.target(), request.host(),
                Set.of(request.operation()));
    }

    private static UUID id(long value) {
        return new UUID(0, value);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void expectIllegal(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(message);
    }
}

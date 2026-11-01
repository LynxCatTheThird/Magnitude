package dev.magnitude.relation;

import dev.magnitude.api.relation.*;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class RelationServiceTest {
    private static final UUID EPOCH = id(100);
    private static final UUID A = id(1), B = id(2), C = id(3), D = id(4);
    private long requests = 1000;

    private static UUID id(long value) { return new UUID(0, value); }
    private RelationService service() {
        return Relations.create(EPOCH, new RelationLimits(2, 8, 16, 3, 8));
    }
    private RelationRequest hold(UUID host, UUID target, long revision) {
        long sequence = requests++;
        return RelationRequest.hold(EPOCH, id(sequence), sequence, host, host, target, revision);
    }
    private RelationRequest release(UUID actor, UUID host, UUID target, long revision, PlacementStatus placement) {
        long sequence = requests++;
        return RelationRequest.release(EPOCH, id(sequence), sequence, actor, host, target, revision, placement);
    }
    private static RelationAuthorization grant(RelationRequest request) {
        // This pure-core fixture models an adapter that has completed all world checks/placement.
        return new RelationAuthorization(request.actor(), request.target(), request.host(),
                Set.of(request.operation()), Set.of(request.placement()));
    }
    private static RelationResult submit(RelationService service, RelationRequest request) {
        return service.submit(request, grant(request));
    }

    @Test void newTargetIsFreeWithoutAllocatingState() {
        RelationService s = service();
        assertEquals(RelationSnapshot.free(B), s.snapshot(B));
        assertTrue(s.snapshotAll().isEmpty());
    }

    @Test void firstOfTwoHostsWinsAndVersionIsConfirmed() {
        RelationService s = service();
        assertTrue(submit(s, hold(A, B, 0)).changed());
        RelationResult second = submit(s, hold(C, B, 0));
        assertEquals(RelationReason.STALE_REVISION, second.reason());
        assertEquals(Optional.of(A), s.snapshot(B).host());
        assertEquals(1, second.snapshot().orElseThrow().revision());
        assertEquals(1, s.heldCount());
    }

    @Test void duplicateHoldReturnsOriginalDecisionWithoutNewSideEffect() {
        RelationService s = service();
        RelationRequest request = hold(A, B, 0);
        RelationResult first = submit(s, request), replay = submit(s, request);
        assertTrue(first.changed());
        assertTrue(replay.replayed());
        assertFalse(replay.changed());
        assertEquals(first.snapshot(), replay.snapshot());
        assertEquals(1, s.snapshot(B).revision());
    }

    @Test void replayAfterReleaseCannotReattachTheTarget() {
        RelationService s = service();
        RelationRequest request = hold(A, B, 0);
        submit(s, request);
        submit(s, release(B, A, B, 1, PlacementStatus.SAFE));
        RelationResult replay = submit(s, request);
        assertTrue(replay.replayed());
        assertFalse(replay.changed());
        assertEquals(RelationState.FREE, s.snapshot(B).state());
        assertEquals(2, s.snapshot(B).revision());
    }

    @Test void sameActorAndRequestIdWithAnotherPayloadIsRejected() {
        RelationService s = service();
        RelationRequest request = hold(A, B, 0);
        submit(s, request);
        RelationRequest conflicting = RelationRequest.hold(EPOCH, request.requestId(), request.sequence(), A, A, C, 0);
        assertEquals(RelationReason.REQUEST_ID_CONFLICT, submit(s, conflicting).reason());
        assertEquals(RelationState.FREE, s.snapshot(C).state());
    }

    @Test void requestIdsAreScopedToAuthenticatedActor() {
        RelationService s = service();
        UUID requestId = id(999);
        assertTrue(submit(s, RelationRequest.hold(EPOCH, requestId, 1, A, A, B, 0)).changed());
        assertTrue(submit(s, RelationRequest.hold(EPOCH, requestId, 1, C, C, D, 0)).changed());
        assertEquals(2, s.heldCount());
    }

    @Test void authorizationBindsActorHostTargetAndOperationBeforeCachedReply() {
        RelationService s = service();
        RelationRequest request = hold(A, B, 0);
        submit(s, request);
        var invalid = new RelationAuthorization(C, B, Optional.of(A), Set.of(RelationOperation.HOLD));
        RelationResult result = s.submit(request, invalid);
        assertEquals(RelationReason.UNAUTHORIZED, result.reason());
        assertTrue(result.snapshot().isEmpty());
        assertFalse(result.replayed());
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(request,
                new RelationAuthorization(A, C, Optional.of(A), Set.of(RelationOperation.HOLD))).reason());
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(request,
                new RelationAuthorization(A, B, Optional.of(C), Set.of(RelationOperation.HOLD))).reason());
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(request,
                new RelationAuthorization(A, B, Optional.of(A), Set.of(RelationOperation.RELEASE))).reason());
        assertEquals(1, s.heldCount());
    }

    @Test void deniedRequestCannotPoisonAnAuthorizedRequestId() {
        RelationService s = service();
        RelationRequest request = hold(A, B, 0);
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(request,
                new RelationAuthorization(A, B, Optional.of(A), Set.of())).reason());
        assertTrue(submit(s, request).changed());
    }

    @Test void staleEpochIsRejectedWithoutLeakingTargetState() {
        RelationService s = service();
        RelationRequest request = RelationRequest.hold(id(101), id(102), 1, A, A, B, 0);
        RelationResult result = submit(s, request);
        assertEquals(RelationReason.STALE_EPOCH, result.reason());
        assertTrue(result.snapshot().isEmpty());
        assertTrue(s.snapshotAll().isEmpty());
    }

    @Test void cannotHoldSelfOrCreateACycle() {
        RelationService s = service();
        assertEquals(RelationReason.SELF_HOLD, submit(s, hold(A, A, 0)).reason());
        submit(s, hold(A, B, 0));
        assertEquals(RelationReason.CYCLE, submit(s, hold(B, A, 0)).reason());
        assertEquals(1, s.heldCount());
        assertEquals(RelationState.FREE, s.snapshot(A).state());
    }

    @Test void depthLimitIncludesTheSubtreeBeingAttached() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(3, 8, 16, 2, 8));
        submit(s, hold(A, B, 0));
        submit(s, hold(B, C, 0));
        assertEquals(RelationReason.DEPTH_LIMIT, submit(s, hold(D, A, 0)).reason());
        assertEquals(RelationReason.DEPTH_LIMIT, submit(s, hold(C, D, 0)).reason());
        assertEquals(2, s.heldCount());
    }

    @Test void hostAndGlobalCapacityAreIndependent() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 2, 8, 3, 8));
        submit(s, hold(A, B, 0));
        assertEquals(RelationReason.HOST_CAPACITY, submit(s, hold(A, C, 0)).reason());
        submit(s, hold(C, D, 0));
        assertEquals(RelationReason.GLOBAL_CAPACITY, submit(s, hold(id(5), id(6), 0)).reason());
    }

    @Test void pendingReleaseFreesCapacityBeforePlacementCompletes() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 1, 8, 2, 8));
        submit(s, hold(A, B, 0));
        RelationResult result = submit(s, release(B, A, B, 1, PlacementStatus.PENDING));
        assertTrue(result.changed());
        assertEquals(RelationState.RECOVERY, s.snapshot(B).state());
        assertTrue(s.snapshot(B).host().isEmpty());
        assertEquals(0, s.heldCount());
        assertEquals(0, s.occupiedSlots(A));
        assertTrue(submit(s, hold(A, C, 0)).changed());
    }

    @Test void recoveringTargetsCannotBeHeldOrStartNewHolds() {
        RelationService s = service();
        submit(s, hold(A, B, 0));
        submit(s, release(B, A, B, 1, PlacementStatus.PENDING));
        assertEquals(RelationReason.TARGET_RECOVERING, submit(s, hold(C, B, 2)).reason());
        assertEquals(RelationReason.HOST_RECOVERING, submit(s, hold(B, C, 0)).reason());
        long sequence = requests++;
        RelationRequest finish = RelationRequest.completeRecovery(EPOCH, id(sequence), sequence, B, B, 2);
        assertTrue(submit(s, finish).changed());
        assertEquals(3, s.snapshot(B).revision());
        assertTrue(submit(s, hold(C, B, 3)).changed());
    }

    @Test void wrongHostCannotReleaseAndRepeatedReleaseDoesNotAdvanceVersion() {
        RelationService s = service();
        submit(s, hold(A, B, 0));
        assertEquals(RelationReason.HOST_MISMATCH,
                submit(s, release(B, C, B, 1, PlacementStatus.SAFE)).reason());
        RelationRequest release = release(B, A, B, 1, PlacementStatus.SAFE);
        assertTrue(submit(s, release).changed());
        assertTrue(submit(s, release).replayed());
        RelationResult another = submit(s, release(B, A, B, 2, PlacementStatus.SAFE));
        assertEquals(RelationReason.ALREADY_FREE, another.reason());
        assertFalse(another.changed());
        assertEquals(2, s.snapshot(B).revision());
        assertEquals(0, s.heldCount());
    }

    @Test void completingRecoveryInAnotherStateDoesNothing() {
        RelationService s = service();
        RelationRequest finish = RelationRequest.completeRecovery(EPOCH, id(9), 1, B, B, 0);
        assertEquals(RelationReason.NOT_RECOVERING, submit(s, finish).reason());
        assertTrue(s.snapshotAll().isEmpty());
    }

    @Test void evictionDoesNotTurnAnOldSuccessfulHoldIntoANewOne() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 2, 4, 2, 1));
        RelationRequest original = hold(A, B, 0);
        submit(s, original);
        submit(s, release(B, A, B, 1, PlacementStatus.SAFE));
        assertEquals(RelationReason.STALE_REQUEST, submit(s, original).reason());
        assertEquals(RelationState.FREE, s.snapshot(B).state());
        assertEquals(2, s.snapshot(B).revision());
    }

    @Test void tombstonesAreBoundedAndNotSilentlyDropped() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 1, 1, 1, 1, 2));
        submit(s, hold(A, B, 0));
        submit(s, release(B, A, B, 1, PlacementStatus.SAFE));
        assertEquals(RelationReason.TRACKING_CAPACITY, submit(s, hold(A, C, 0)).reason());
        assertEquals(1, s.snapshotAll().size());
        assertTrue(submit(s, hold(A, B, 2)).changed());
    }

    @Test void snapshotsAndAuthorizationSetsCannotBeMutated() {
        RelationService s = service();
        Set<RelationOperation> permissions = new HashSet<>(Set.of(RelationOperation.HOLD));
        RelationRequest request = hold(A, B, 0);
        var auth = new RelationAuthorization(A, B, Optional.of(A), permissions);
        permissions.clear();
        assertTrue(s.submit(request, auth).changed());
        assertThrows(UnsupportedOperationException.class, () -> s.snapshotAll().clear());
        assertThrows(UnsupportedOperationException.class, () -> auth.allowedOperations().clear());
        Map<UUID, RelationSnapshot> old = s.snapshotAll();
        submit(s, release(B, A, B, 1, PlacementStatus.SAFE));
        assertEquals(RelationState.HELD, old.get(B).state());
    }

    @Test void ownerThreadIsEnforced() throws InterruptedException {
        RelationService s = service();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread other = new Thread(() -> {
            try { s.snapshot(A); } catch (Throwable e) { failure.set(e); }
        });
        other.start(); other.join();
        assertInstanceOf(IllegalStateException.class, failure.get());
        assertTrue(s.snapshotAll().isEmpty());
    }

    @Test void invalidRequestsAndLimitsFailBeforeEnteringTheService() {
        assertThrows(IllegalArgumentException.class, () -> new RelationLimits(0, 1, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new RelationLimits(1, 2, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> RelationRequest.hold(EPOCH, A, 1, A, A, B, -1));
        assertThrows(IllegalArgumentException.class,
                () -> RelationRequest.hold(EPOCH, A, 0, A, A, B, 0));
        assertThrows(IllegalArgumentException.class, () -> new RelationLimits(1, 1, 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> RelationRequest.release(EPOCH, A, 1, A, A, B, 0, PlacementStatus.NOT_APPLICABLE));
    }

    @Test void seededSequencesPreserveGraphCapacityAndRevisionInvariants() {
        for (long seed : new long[]{7, 91, 2026}) {
            RelationService s = service(); Random random = new Random(seed);
            for (int step = 0; step < 1000; step++) {
                UUID host = id(1+random.nextInt(12)), target = id(1+random.nextInt(12));
                RelationSnapshot before = s.snapshot(target);
                long revision = random.nextInt(5)==0 ? 0 : before.revision();
                RelationRequest request;
                if (before.state()==RelationState.RECOVERY) {
                    long sequence = requests++;
                    request=RelationRequest.completeRecovery(EPOCH,id(sequence),sequence,target,target,revision);
                } else if (before.state()==RelationState.HELD && random.nextBoolean()) {
                    request=release(target,before.host().orElseThrow(),target,revision,
                            random.nextBoolean()?PlacementStatus.SAFE:PlacementStatus.PENDING);
                } else request=hold(host,target,revision);
                RelationResult result=submit(s,request);
                RelationSnapshot after=s.snapshot(target);
                assertEquals(before.revision()+(result.changed()?1:0),after.revision(),"seed="+seed+" step="+step);
                if (!result.changed()) assertEquals(before,after);
                assertGraph(s);
            }
        }
    }


    @Test void evictedCapacityRejectionCannotBecomeASuccessLater() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 2, 8, 2, 1));
        RelationRequest first = RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0);
        RelationRequest rejected = RelationRequest.hold(EPOCH, id(101), 2, A, A, C, 0);
        assertTrue(submit(s, first).changed());
        assertEquals(RelationReason.HOST_CAPACITY, submit(s, rejected).reason());
        assertEquals(0, s.snapshot(C).revision());
        // Different actor frees A's slot and evicts the rejection response.
        assertTrue(submit(s, RelationRequest.release(EPOCH, id(102), 1, B, A, B, 1, PlacementStatus.SAFE)).changed());
        assertEquals(RelationReason.STALE_REQUEST, submit(s, rejected).reason());
        assertEquals(RelationState.FREE, s.snapshot(C).state());
        assertEquals(0, s.heldCount());
        // An intentional new attempt is a new command with a higher sequence.
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(103), 3, A, A, C, 0)).changed());
    }

    @Test void outOfOrderAndReusedSequencesCannotChangeState() {
        RelationService s = service();
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(100), 10, A, A, B, 0)).changed());
        assertEquals(RelationReason.STALE_REQUEST,
                submit(s, RelationRequest.hold(EPOCH, id(101), 9, A, A, C, 0)).reason());
        assertEquals(RelationReason.STALE_REQUEST,
                submit(s, RelationRequest.hold(EPOCH, id(102), 10, A, A, C, 0)).reason());
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(103), 11, A, A, C, 0)).changed());
    }

    @Test void deniedAndWrongEpochRequestsDoNotAdvanceAnActorsSequence() {
        RelationService s = service();
        RelationRequest denied = RelationRequest.hold(EPOCH, id(100), 100, A, A, B, 0);
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(denied,
                new RelationAuthorization(C, B, Optional.of(A), Set.of(RelationOperation.HOLD))).reason());
        RelationRequest wrongEpoch = RelationRequest.hold(id(999), id(101), 100, A, A, B, 0);
        assertEquals(RelationReason.STALE_EPOCH, submit(s, wrongEpoch).reason());
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(102), 1, A, A, B, 0)).changed());
    }

    @Test void authenticatedRejectionsConsumeSequenceEvenWithoutTargetState() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(2, 2, 4, 2, 1));
        RelationRequest self = RelationRequest.hold(EPOCH, id(100), 5, A, A, A, 0);
        assertEquals(RelationReason.SELF_HOLD, submit(s, self).reason());
        assertTrue(s.snapshotAll().isEmpty());
        assertEquals(RelationReason.STALE_REQUEST,
                submit(s, RelationRequest.hold(EPOCH, id(101), 4, A, A, B, 0)).reason());
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(102), 6, A, A, B, 0)).changed());
        assertEquals(RelationReason.STALE_REQUEST, submit(s, self).reason());
    }

    @Test void actorHighWaterStorageIsBoundedWithoutEviction() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(2, 2, 8, 2, 1, 2));
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0)).changed());
        assertEquals(RelationReason.ACTOR_CAPACITY,
                submit(s, RelationRequest.hold(EPOCH, id(101), 1, C, C, D, 0)).reason());
        assertTrue(submit(s, RelationRequest.release(EPOCH, id(102), 2, A, A, B, 1, PlacementStatus.SAFE)).changed());
        assertEquals(RelationReason.ACTOR_CAPACITY,
                submit(s, RelationRequest.hold(EPOCH, id(103), 2, C, C, D, 0)).reason());
    }

    @Test void defaultReleasePermissionCannotAuthorizeSafePlacement() {
        RelationService s = service();
        submit(s, hold(A, B, 0));
        RelationRequest claimedSafe = RelationRequest.release(EPOCH, id(100), 1, B, A, B, 1, PlacementStatus.SAFE);
        RelationAuthorization ordinaryRelease = new RelationAuthorization(B, B, Optional.of(A), Set.of(RelationOperation.RELEASE));
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(claimedSafe, ordinaryRelease).reason());
        assertEquals(RelationState.HELD, s.snapshot(B).state());
        // Same sequence remains available because the forged placement was unauthorized.
        RelationRequest pending = RelationRequest.release(EPOCH, id(101), 1, B, A, B, 1, PlacementStatus.PENDING);
        assertTrue(s.submit(pending, ordinaryRelease).changed());
        assertEquals(RelationState.RECOVERY, s.snapshot(B).state());
        RelationRequest complete = RelationRequest.completeRecovery(EPOCH, id(102), 2, B, B, 2);
        var ordinaryRecovery = new RelationAuthorization(B, B, Optional.empty(), Set.of(RelationOperation.COMPLETE_RECOVERY));
        assertEquals(RelationReason.UNAUTHORIZED, s.submit(complete, ordinaryRecovery).reason());
        var placed = new RelationAuthorization(B, B, Optional.empty(),
                Set.of(RelationOperation.COMPLETE_RECOVERY), Set.of(PlacementStatus.SAFE));
        assertTrue(s.submit(complete, placed).changed());
        assertEquals(RelationState.FREE, s.snapshot(B).state());
    }

    @Test void cachedRequestIdCannotBeRepurposedWithANewSequence() {
        RelationService s = service();
        RelationRequest request = RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0);
        submit(s, request);
        assertEquals(RelationReason.REQUEST_ID_CONFLICT,
                submit(s, RelationRequest.hold(EPOCH, id(100), 2, A, A, C, 0)).reason());
        assertEquals(RelationState.FREE, s.snapshot(C).state());
    }


    @Test void changedPayloadRejectionConsumesSequenceEvenAfterOriginalCacheEviction() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 2, 8, 2, 1));
        RelationRequest original = RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0);
        RelationRequest conflicting = RelationRequest.hold(EPOCH, id(100), 2, A, A, C, 0);
        assertTrue(submit(s, original).changed());
        assertEquals(RelationReason.REQUEST_ID_CONFLICT, submit(s, conflicting).reason());
        assertTrue(submit(s, RelationRequest.release(EPOCH, id(101), 1, B, A, B, 1, PlacementStatus.SAFE)).changed());
        assertEquals(RelationReason.STALE_REQUEST, submit(s, conflicting).reason());
        assertEquals(RelationState.FREE, s.snapshot(C).state());
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(102), 3, A, A, C, 0)).changed());
    }


    @Test void holdIsRejectedWhenTheTargetCannotBeReservedAnExitIdentity() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 1, 4, 1, 2, 1));
        RelationRequest hold = RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0);
        assertEquals(RelationReason.ACTOR_CAPACITY, submit(s, hold).reason());
        assertEquals(RelationState.FREE, s.snapshot(B).state());
        assertEquals(0, s.heldCount());
        assertTrue(s.snapshotAll().isEmpty());
    }

    @Test void targetCanReleaseAndRecoverWhenAllActorSlotsAreOccupied() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 1, 4, 1, 8, 2));
        assertTrue(submit(s, RelationRequest.hold(EPOCH, id(100), 1, A, A, B, 0)).changed());
        assertEquals(RelationReason.ACTOR_CAPACITY,
                submit(s, RelationRequest.hold(EPOCH, id(101), 1, C, C, D, 0)).reason());
        RelationRequest exit = RelationRequest.release(EPOCH, id(102), 1, B, A, B, 1, PlacementStatus.PENDING);
        var allowedExit = new RelationAuthorization(B, B, Optional.of(A), Set.of(RelationOperation.RELEASE));
        assertTrue(s.submit(exit, allowedExit).changed());
        assertEquals(RelationState.RECOVERY, s.snapshot(B).state());
        assertEquals(0, s.occupiedSlots(A));
        RelationRequest placed = RelationRequest.completeRecovery(EPOCH, id(103), 2, B, B, 2);
        assertTrue(submit(s, placed).changed());
        assertEquals(RelationState.FREE, s.snapshot(B).state());
    }

    @Test void administratorHoldReservesBothHostAndTargetExitIdentities() {
        for (UUID releasingActor : new UUID[]{A, B}) {
            RelationService s = Relations.create(EPOCH, new RelationLimits(1, 1, 4, 1, 8, 3));
            // C is the administrator; A and B have not submitted any requests.
            RelationRequest adminHold = RelationRequest.hold(EPOCH, id(100), 1, C, A, B, 0);
            assertTrue(submit(s, adminHold).changed());
            assertEquals(RelationReason.ACTOR_CAPACITY,
                    submit(s, RelationRequest.hold(EPOCH, id(101), 1, D, D, id(5), 0)).reason());
            RelationRequest exit = RelationRequest.release(EPOCH, id(102), 1, releasingActor,
                    A, B, 1, PlacementStatus.PENDING);
            assertTrue(submit(s, exit).changed());
            assertEquals(RelationState.RECOVERY, s.snapshot(B).state());
            assertEquals(0, s.heldCount());
        }
    }

    @Test void failedHoldDoesNotReserveUnrelatedHostAndTargetIdentities() {
        RelationService s = Relations.create(EPOCH, new RelationLimits(1, 2, 8, 2, 8, 4));
        RelationRequest rejected = RelationRequest.hold(EPOCH, id(100), 1, A, B, C, 99);
        assertEquals(RelationReason.STALE_REVISION, submit(s, rejected).reason());
        // A's processed sequence is retained, but B/C must not have been reserved.
        RelationRequest other = RelationRequest.hold(EPOCH, id(101), 1, D, id(5), id(6), 0);
        assertTrue(submit(s, other).changed());
        assertEquals(1, s.heldCount());
    }

    private static void assertGraph(RelationService s) {
        Map<UUID,RelationSnapshot> all=s.snapshotAll();
        assertTrue(all.size()<=16);
        long held=all.values().stream().filter(v->v.state()==RelationState.HELD).count();
        assertEquals(held,s.heldCount());assertTrue(held<=8);
        for (var entry:all.values()) {
            assertTrue(s.occupiedSlots(entry.target())<=2);
            Set<UUID> seen=new HashSet<>();UUID cursor=entry.target();int depth=0;
            while (s.snapshot(cursor).host().isPresent()) {
                assertTrue(seen.add(cursor),"cycle");
                cursor=s.snapshot(cursor).host().orElseThrow();depth++;
                assertTrue(depth<=3);
            }
            if (entry.state()!=RelationState.HELD) assertTrue(entry.host().isEmpty());
        }
    }
}

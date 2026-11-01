package dev.magnitude.test;

import dev.magnitude.fabric.internal.carry.persistence.CarryRecoveryStore;
import dev.magnitude.fabric.internal.carry.persistence.RecoveryToken;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;

/** Real Fabric attachment and entity-NBT + filesystem sequences; not a complete process-restart test. */
public final class CarryRecoveryGameTests {
    // These real Pig instances are intentionally not registered twice in the level:
    // stale NBT reloads model one entity across a crash without spawning duplicates.
    private static Pig pig(GameTestHelper helper) {
        helper.setBlock(4, 0, 4, Blocks.STONE);
        helper.setBlock(5, 0, 4, Blocks.STONE);
        Pig pig = new Pig(EntityTypes.PIG, helper.getLevel());
        pig.setPos(helper.absoluteVec(new Vec3(4.5, 1, 4.5)));
        return pig;
    }
    private static CompoundTag save(Pig pig) {
        var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, pig.registryAccess());
        pig.saveWithoutId(output);
        return output.buildResult();
    }
    private static Pig load(GameTestHelper helper, CompoundTag tag) {
        Pig pig = new Pig(EntityTypes.PIG, helper.getLevel());
        pig.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        return pig;
    }
    private static String dimension(Pig pig) { return pig.level().dimension().identifier().toString(); }
    private static void controlled(Pig pig) {
        pig.setNoGravity(true); pig.setNoAi(true); pig.setPermanentlyInvulnerable(true);
    }
    private static void restore(Pig pig, RecoveryToken token) {
        pig.setNoGravity(token.originalNoGravity());
        pig.setNoAi(token.originalNoAi());
        pig.setPermanentlyInvulnerable(token.originalInvulnerable());
    }
    @GameTest
    public void releasedJournalRecoversStaleHeldEntitySave(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-release-");
        try {
            Pig pig = pig(helper);
            var store = CarryRecoveryStore.open(root, 4);
            RecoveryToken original = store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            controlled(pig);
            CompoundTag heldSave = save(pig);
            restore(pig, original);
            Vec3 releasedPosition = helper.absoluteVec(new Vec3(5.5, 1, 4.5));
            pig.setPos(releasedPosition);
            store.complete(pig);
            helper.assertTrue(CarryRecoveryStore.token(pig).isEmpty(), "live marker not cleared");
            helper.assertTrue(store.isReleased(pig.getUUID()), "completion tombstone missing");

            Pig reloaded = load(helper, heldSave);
            helper.assertTrue(reloaded.isNoGravity() && reloaded.isNoAi(), "fixture did not reload controlled flags");
            var reopened = CarryRecoveryStore.open(root, 4);
            RecoveryToken recovery = reopened.recoverableToken(reloaded).orElseThrow();
            helper.assertTrue(recovery.safePosition().equals(releasedPosition), "lost actual completed placement");
            restore(reloaded, recovery);
            reloaded.setPos(recovery.safePosition()); // fixture has already validated this platform
            reopened.complete(reloaded);
            helper.assertTrue(!reloaded.isNoGravity() && !reloaded.isNoAi()
                    && !reloaded.isPermanentlyInvulnerable(), "original flags not restored");
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @GameTest
    public void newerIntentAndCompletionSupersedeOlderEntityMarker(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-recapture-");
        try {
            Pig pig = pig(helper);
            var store = CarryRecoveryStore.open(root, 1);
            RecoveryToken first = store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            controlled(pig);
            CompoundTag firstHeldSave = save(pig);
            restore(pig, first);
            store.complete(pig);

            // A new legitimate baseline differs from the first one; old flags cannot win.
            pig.setNoGravity(true); pig.setNoAi(true); pig.setPermanentlyInvulnerable(true);
            RecoveryToken second = store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            helper.assertTrue(second.generation() > first.generation(), "generation did not advance");
            Pig afterCrash = load(helper, firstHeldSave);
            var reopened = CarryRecoveryStore.open(root, 1);
            RecoveryToken selected = reopened.recoverableToken(afterCrash).orElseThrow();
            helper.assertTrue(selected.id().equals(second.id()), "stale entity marker hid newer durable intent");
            helper.assertTrue(selected.originalNoGravity() && selected.originalNoAi()
                    && selected.originalInvulnerable(), "new baseline flags lost");
            restore(afterCrash, selected);
            reopened.complete(afterCrash);

            // Crash again before the restored entity is saved: RELEASED must still win.
            Pig anotherOldSave = load(helper, firstHeldSave);
            var reopenedAgain = CarryRecoveryStore.open(root, 1);
            RecoveryToken completed = reopenedAgain.recoverableToken(anotherOldSave).orElseThrow();
            helper.assertTrue(completed.id().equals(second.id()) && completed.originalInvulnerable(),
                    "completion deleted the newest recovery generation");
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @GameTest
    public void journalCapacityRejectsNewCaptureButPreservesExit(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-capacity-");
        try {
            Pig first = pig(helper); Pig second = pig(helper);
            var store = CarryRecoveryStore.open(root, 1);
            RecoveryToken token = store.prepare(UUID.randomUUID(), first, dimension(first), first.position());
            controlled(first);
            expectIOException(helper, () -> store.prepare(UUID.randomUUID(), second, dimension(second), second.position()));
            helper.assertTrue(CarryRecoveryStore.token(second).isEmpty(), "rejected capture attached marker");
            restore(first, token);
            store.complete(first);
            helper.assertTrue(CarryRecoveryStore.token(first).isEmpty(), "full journal blocked existing exit");
            CompoundTag releasedSave = save(first);
            Pig loadedWithoutMarker = load(helper, releasedSave);
            store.reconcileUnmarked(loadedWithoutMarker);
            helper.assertTrue(store.records().isEmpty(), "verified marker-free load did not reclaim tombstone");
            store.prepare(UUID.randomUUID(), second, dimension(second), second.position());
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @GameTest
    public void mismatchedGenerationIsQuarantined(GameTestHelper helper) throws IOException {
        Path firstRoot = Files.createTempDirectory("magnitude-recovery-conflict-a-");
        Path secondRoot = Files.createTempDirectory("magnitude-recovery-conflict-b-");
        try {
            Pig pig = pig(helper);
            var first = CarryRecoveryStore.open(firstRoot, 2);
            first.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            first.complete(pig);
            var second = CarryRecoveryStore.open(secondRoot, 2);
            second.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            Vec3 before = pig.position();
            expectIOException(helper, () -> first.recoverableToken(pig));
            helper.assertTrue(CarryRecoveryStore.token(pig).isPresent() && before.equals(pig.position()),
                    "generation conflict mutated entity");
            helper.succeed();
        } finally { removeTemporary(firstRoot); removeTemporary(secondRoot); }
    }
    @GameTest
    public void failedAtomicWriteDoesNotCapture(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-io-");
        try {
            Pig pig = pig(helper); var store = CarryRecoveryStore.open(root, 2);
            Files.createDirectory(store.path()); // force replacement to fail on this private fixture
            expectIOException(helper, () -> store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position()));
            helper.assertTrue(!store.writable() && store.records().isEmpty(), "failed store remained writable");
            helper.assertTrue(CarryRecoveryStore.token(pig).isEmpty() && !pig.isNoGravity()
                    && !pig.isNoAi() && !pig.isPermanentlyInvulnerable(), "failed preparation mutated target");
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @GameTest
    public void corruptJournalStillAllowsRestoreOnlyMarkerRecovery(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-corrupt-");
        try {
            Pig pig = pig(helper); var original = CarryRecoveryStore.open(root, 4);
            RecoveryToken token = original.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            controlled(pig);
            String corrupt = "{intentionally malformed journal";
            Files.writeString(original.path(), corrupt);
            expectIOException(helper, () -> CarryRecoveryStore.open(root, 4));
            var fallback = CarryRecoveryStore.openRecoveryOnly(root, 4);
            helper.assertTrue(!fallback.writable() && fallback.records().isEmpty(), "fallback must not trust the bad journal");
            RecoveryToken recovered = fallback.recoverableToken(pig).orElseThrow();
            helper.assertTrue(recovered.id().equals(token.id()), "valid entity recovery marker was lost");
            restore(pig, recovered);
            expectIOException(helper, () -> fallback.complete(pig));
            helper.assertTrue(CarryRecoveryStore.token(pig).isPresent(), "failed cleanup must preserve retry marker");
            helper.assertTrue(!pig.isNoGravity() && !pig.isNoAi() && !pig.isPermanentlyInvulnerable(),
                    "cleanup failure restored held control flags");
            Pig fresh = pig(helper);
            expectIOException(helper, () -> fallback.prepare(UUID.randomUUID(), fresh, dimension(fresh), fresh.position()));
            helper.assertTrue(CarryRecoveryStore.token(fresh).isEmpty(), "read-only fallback accepted capture");
            helper.assertTrue(Files.readString(original.path()).equals(corrupt), "fallback replaced the bad journal");
            Path absentWorld = root.resolve("must-not-be-created");
            CarryRecoveryStore.openRecoveryOnly(absentWorld, 4);
            helper.assertTrue(!Files.exists(absentWorld), "recovery-only factory performed file I/O");
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @GameTest
    public void preparationRejectsExistingFireOrFreezeWithoutClearingIt(GameTestHelper helper) throws IOException {
        Path root = Files.createTempDirectory("magnitude-recovery-thermal-");
        try {
            Pig pig = pig(helper); var store = CarryRecoveryStore.open(root, 2);
            pig.setRemainingFireTicks(20);
            expectIOException(helper, () -> store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position()));
            helper.assertTrue(pig.getRemainingFireTicks() == 20 && CarryRecoveryStore.token(pig).isEmpty(),
                    "burning admission mutated the original effect");
            pig.setRemainingFireTicks(0);
            pig.setTicksFrozen(10);
            expectIOException(helper, () -> store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position()));
            helper.assertTrue(pig.getTicksFrozen() == 10 && CarryRecoveryStore.token(pig).isEmpty(),
                    "frozen admission mutated the original effect");
            helper.assertTrue(store.records().isEmpty() && store.generation() == 0, "thermal rejection wrote recovery state");
            pig.setTicksFrozen(0);
            RecoveryToken token = store.prepare(UUID.randomUUID(), pig, dimension(pig), pig.position());
            helper.assertTrue(token.generation() == 1 && CarryRecoveryStore.token(pig).isPresent(),
                    "cold target should remain admissible");
            helper.succeed();
        } finally { removeTemporary(root); }
    }
    @FunctionalInterface private interface IoAction { void run() throws IOException; }
    private static void expectIOException(GameTestHelper helper, IoAction action) {
        try { action.run(); }
        catch (IOException expected) { return; }
        helper.assertTrue(false, "Expected IOException");
    }
    private static void removeTemporary(Path path) throws IOException {
        try (var paths = Files.walk(path)) {
            for (Path entry : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(entry);
        }
    }
}

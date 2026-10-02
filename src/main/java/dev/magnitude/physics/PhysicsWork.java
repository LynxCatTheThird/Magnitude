package dev.magnitude.physics;

/** Separate budgets on the client and server threads, shared by all players on each side. */
public final class PhysicsWork {
    private static final class Budget { int cells;int pairs; }
    private static final ThreadLocal<Budget> WORK=ThreadLocal.withInitial(Budget::new);
    private PhysicsWork() {}
    public static void beginTick(){var budget=WORK.get();budget.cells=131072;budget.pairs=524288;}
    public static boolean cells(long count){var b=WORK.get();if(count<0 || count>b.cells)return false;b.cells-=(int)count;return true;}
    public static boolean pairs(long count){var b=WORK.get();if(count<0 || count>b.pairs)return false;b.pairs-=(int)count;return true;}
}

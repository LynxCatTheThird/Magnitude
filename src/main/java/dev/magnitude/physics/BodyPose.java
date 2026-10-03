package dev.magnitude.physics;

/** Server-owned joint angles in radians; support bit 1 = left, 2 = right. */
public record BodyPose(int action, long startTick, double phase, int support,
                       double leftLeg, double rightLeg, double leftArm, double rightArm, double head,
                       double leftKnee,double rightKnee) {
    public BodyPose(int action,long startTick,double phase,int support,double leftLeg,double rightLeg,double leftArm,double rightArm,double head){
        this(action,startTick,phase,support,leftLeg,rightLeg,leftArm,rightArm,head,0,0);
    }
    public static final BodyPose IDLE = new BodyPose(0, 0, 0, 3, 0, 0, 0, 0, 0);
    public static BodyPose interpolate(BodyPose a,BodyPose b,double delta) {
        double t=Math.clamp(delta,0,1);
        return new BodyPose(b.action,b.startTick,b.phase,b.support,lerp(a.leftLeg,b.leftLeg,t),lerp(a.rightLeg,b.rightLeg,t),lerp(a.leftArm,b.leftArm,t),lerp(a.rightArm,b.rightArm,t),lerp(a.head,b.head,t),lerp(a.leftKnee,b.leftKnee,t),lerp(a.rightKnee,b.rightKnee,t));
    }
    private static double lerp(double a,double b,double t){return a+(b-a)*t;}
    public boolean valid() {
        return action >= 0 && action <= 3 && support >= 0 && support <= 3
            && Double.isFinite(phase) && phase >= 0 && phase < Math.PI * 2
            && ((support&1)==0 || Math.abs(leftLeg)<.05 || leftKnee<-.01)
            && ((support&2)==0 || Math.abs(rightLeg)<.05 || rightKnee<-.01)
            && angle(leftLeg) && angle(rightLeg) && angle(leftArm) && angle(rightArm) && angle(head) && knee(leftKnee) && knee(rightKnee);
    }
    private static boolean knee(double value){return Double.isFinite(value)&&value>=-2.2&&value<=0;}
    private static boolean angle(double value) { return Double.isFinite(value) && Math.abs(value) <= 1.2; }
    public BodyPose withSupport(int mask) { return new BodyPose(action,startTick,phase,mask,(mask&1)!=0 ? 0 : leftLeg,(mask&2)!=0 ? 0 : rightLeg,leftArm,rightArm,head,leftKnee,rightKnee); }
}

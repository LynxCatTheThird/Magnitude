package dev.magnitude.physics;

/** Two rigid segments and a horizontal boot. All lengths scale continuously with model height. */
public final class LegKinematics {
    public static final double HIP=.76, THIGH=.38, SHIN=.76/3, BOOT=.76/6;
    public record Joint(double hip,double knee,boolean reachable){}
    private LegKinematics(){}
    /** Target is in the sagittal plane, relative to hip; positive drop points down. */
    public static Joint solve(double drop,double forward,double height){
        double a=THIGH*height,b=SHIN*height,d=Math.hypot(drop,forward);
        if(!Double.isFinite(d)||!Double.isFinite(height)||height<=0||d<Math.abs(a-b)+height*1e-10||d>a+b+height*1e-9)
            return new Joint(0,0,false);
        double bend=Math.acos(Math.clamp((d*d-a*a-b*b)/(2*a*b),-1,1));
        double hip=Math.atan2(forward,drop)+Math.acos(Math.clamp((d*d+a*a-b*b)/(2*a*d),-1,1));
        double knee=-bend;
        return new Joint(hip,knee,Math.abs(hip)<=1.2&&knee>=-2.2);
    }
    public static double forward(double hip,double knee,double height){return (THIGH*Math.sin(hip)+SHIN*Math.sin(hip+knee))*height;}
    public static double drop(double hip,double knee,double height){return (THIGH*Math.cos(hip)+SHIN*Math.cos(hip+knee))*height;}
    public static double lift(double hip,double knee,double height){return (THIGH+SHIN)*height-drop(hip,knee,height);}
}

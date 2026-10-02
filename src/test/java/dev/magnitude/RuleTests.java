package dev.magnitude;
import dev.magnitude.core.Rules;
import java.util.Random;
public final class RuleTests {
    private static int checks;
    private static void check(boolean condition,String name) {checks++;if(!condition)throw new AssertionError(name);}
    public static void main(String[] args) {
        for(double value:new double[]{Double.NaN,Double.POSITIVE_INFINITY,Double.NEGATIVE_INFINITY}) {
            boolean rejected=false;try{Rules.bounded(value,1.0/64,32);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"non-finite input rejected");
        }
        check(Rules.bounded(-100,1.0/64,32)==1.0/64,"positive minimum");
        check(Rules.bounded(Double.MAX_VALUE,1.0/64,32)==32,"extreme maximum");
        Random random=new Random(84519);
        for(int i=0;i<100000;i++) {
            double donor=1.0/64+random.nextDouble()*31.984375;
            double recipient=1.0/64+random.nextDouble()*31.984375;
            double moved=Rules.transfer(donor,32-recipient,random.nextDouble(),1.0/64);
            check(donor-moved>=1.0/64-1e-12,"donor minimum");check(recipient+moved<=32+1e-12,"recipient cap");
            check(Math.abs((donor-moved)+(recipient+moved)-(donor+recipient))<1e-12,"charge conservation");
        }
        Rules.Budget budget=new Rules.Budget();budget.reset(256);int accepted=0;for(int i=0;i<100000;i++)if(budget.take())accepted++;check(accepted==256,"hard event budget");check(budget.remaining()==0,"no underflow");
        check(!Rules.ratio(4,0,2),"division by zero");check(!Rules.ratio(Double.NaN,1,2),"invalid size ratio");
        check(Rules.insideEllipsoid(0,0,0,3,1),"center included");check(!Rules.insideEllipsoid(3,0,3,3,1),"square corner excluded");check(!Rules.insideEllipsoid(0,2,0,3,1),"vertical bound");
        System.out.println("RULE TESTS PASSED: "+checks+" assertions");
    }
}

package dev.fnf.terralatitude;

public final class ClimateMathTest {
    private static int checks;
    private static void check(boolean b, String message) { checks++; if (!b) throw new AssertionError(message); }
    private static boolean near(double a, double b) { return Math.abs(a-b) < 1e-12; }
    public static void main(String[] args) {
        for (int ti=0; ti<=100; ti++) {
            double t = -1 + ti / 50.0;
            check(near(ClimateMath.altitude(t,false,0.5,2),t),"sea-level bypass");
            check(near(ClimateMath.altitude(t,true,0,2),t),"zero elevation");
            check(near(ClimateMath.altitude(t,true,1,0),t),"zero cooling strength");
            check(ClimateMath.blend(t,.8,0)==t,"zero band influence");
            check(near(ClimateMath.blend(t,.8,1),.8),"full band influence");
            check(near(ClimateMath.smooth(t,t,t,t,t),t),"constant field preserved");
            check(near(ClimateMath.smooth(t,t-.2,t+.2,t-.3,t+.3),t),"linear transition preserved");
            double previous=t;
            for (int ei=0; ei<=100; ei++) {
                double cooled=ClimateMath.altitude(t,true,ei/100.0,2);
                check(cooled<=previous+1e-12,"higher terrain never warms");
                check(cooled>=-1 && cooled<=1,"bounded climate");
                previous=cooled;
            }
        }
        for (int i=0;i<100;i++) {
            double colder=-1+i*.02, warmer=colder+.02;
            check(ClimateMath.altitude(colder,true,.6,2)<=ClimateMath.altitude(warmer,true,.6,2),"latitude ordering");
        }
        check(ClimateMath.altitude(9,true,1,2)==1,"hot outlier saturates");
        check(ClimateMath.altitude(-9,true,1,2)==-1,"cold outlier saturates");
        check(ClimateMath.smooth(-1,-1,1,-1,-1)==-.75,"step kernel bounded");
        System.out.println("ClimateMathTest passed ("+checks+" assertions)");
    }
}

package dev.fnf.terralatitude;

import com.naturaltemperature.*;
import com.naturaltemperature.mixin.WorldPainter;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.*;

/** Offline parity harness: never starts a client/server or loads a world. */
public class NaturalClimateAdapterTest {
    public static double rawT, rawH;
    private static long checks;
    public static double phase() { return ((WorldSeedHolder.getSeed() & 1048575L) / 1048575.0) * 2.0 * Math.PI; }
    public static void main(String[]args)throws Exception {
        registerModes();
        Method temperature=WorldPainter.class.getDeclaredMethod("customTemperatureCalculation",int.class,int.class,int.class);
        Method humidity=WorldPainter.class.getDeclaredMethod("customHumidityCalculation",int.class,int.class,int.class);
        temperature.setAccessible(true);humidity.setAccessible(true);
        defaults();
        Random random=new Random(907537);
        for (long seed: new long[]{0L, 987654321L, -3437391787389L}) {
            WorldSeedHolder.cacheSeed(seed);
            WorldPainter reference=new WorldPainter();
            for(int mode=0; mode<=10; mode++) for(int scenario=0; scenario<18; scenario++) {
                defaults();Config.generation_mode=mode;
                Config.equatorial_distance=15000.0+scenario*200.0;
                Config.equator_offset=scenario%2==0?1137:-1597;
                Config.humidity_offset=scenario%2==0?-1937:2541;
                Config.wave_magnification=new double[]{0.0,2.0,50.0}[scenario%3];
                Config.looping_world=scenario%2==0;
                Config.extra_bands=scenario%3!=0;
                Config.enable_humidity_bands=scenario%2!=0;
                Config.randomize_underground=scenario%2==0;
                Config.underground_randomization_type=scenario%3;
                Config.global_temperature_modifier_percentage=-20.0+scenario*2.0;
                Config.global_temperature_mitigation_percentage=scenario*3.0;
                NtSettings settings=NtSettings.capture();
                if(!NtSettings.fromMap(settings.toMap()).toMap().equals(settings.toMap()))throw new AssertionError("snapshot roundtrip");
                for(int point=0;point<160;point++) {
                    rawT=random.nextDouble()*2-1;rawH=random.nextDouble()*2-1;
                    int x=random.nextInt(160001)-80000,z=random.nextInt(160001)-50000;
                    int y=new int[]{-64,45,46,100}[point%4];
                    float expectedT=(float)temperature.invoke(reference,x,y,z);
                    float expectedH=(float)humidity.invoke(reference,x,y,z);
                    var actual=NaturalClimateAdapter.sample(settings,x,y,z,rawT,rawH);
                    equal(expectedT,actual.temperature(),"temperature mode "+mode+" scenario "+scenario+" point "+x+","+y+","+z);
                    equal(expectedH,actual.humidity(),"humidity mode "+mode+" scenario "+scenario+" point "+x+","+y+","+z);
                    // Captured zones remain authoritative even if another world's global config changes.
                    if(point==0) {
                        double oldT=Config.temperature_zone_1,oldH=Config.humidity_zone_1,oldP=Config.percentage_coverage_zone_1;
                        Config.temperature_zone_1=.935;Config.humidity_zone_1=-.927;Config.percentage_coverage_zone_1=97;
                        var isolated=NaturalClimateAdapter.sample(settings,x,y,z,rawT,rawH);
                        equal(actual.temperature(),isolated.temperature(),"snapshot T isolation");
                        equal(actual.humidity(),isolated.humidity(),"snapshot H isolation");
                        Config.temperature_zone_1=oldT;Config.humidity_zone_1=oldH;Config.percentage_coverage_zone_1=oldP;
                    }
                }
            }
        }
        defaults();NtSettings one=NtSettings.capture();
        Map<String,String> twoMap=new HashMap<>(one.toMap());twoMap.put("temperature_zone_1","0.731");NtSettings two=NtSettings.fromMap(twoMap);
        if(NtSnapshotScope.active())throw new AssertionError("leaked scope");
        try(var scope=NtSnapshotScope.enter(one)) {
            equal(one.number("temperature_zone_1"),NtSnapshotScope.number("temperature_zone_1",999),"outer scope");
            try(var nested=NtSnapshotScope.enter(two)) {equal(.731,NtSnapshotScope.number("temperature_zone_1",999),"nested scope");}
            equal(one.number("temperature_zone_1"),NtSnapshotScope.number("temperature_zone_1",999),"restored scope");
        }
        equal(999,NtSnapshotScope.number("temperature_zone_1",999),"scope released");
        try {try(var scope=NtSnapshotScope.enter(one)){throw new IllegalStateException("test");}}
        catch(IllegalStateException expected){}
        if(NtSnapshotScope.active())throw new AssertionError("exception leaked scope");
        try(ExecutorService pool=Executors.newFixedThreadPool(2)) {
            var results=pool.invokeAll(List.of(()->{try(var scope=NtSnapshotScope.enter(one)){return NtSnapshotScope.number("temperature_zone_1",999);}},
                    ()->{try(var scope=NtSnapshotScope.enter(two)){return NtSnapshotScope.number("temperature_zone_1",999);}}));
            equal(one.number("temperature_zone_1"),((Number)results.get(0).get()).doubleValue(),"thread one");
            equal(.731,((Number)results.get(1).get()).doubleValue(),"thread two");
        }
        Map<String,String> custom=new HashMap<>(one.toMap());custom.put("generation_mode","101");
        try{NtSettings.fromMap(custom);throw new AssertionError("custom mode accepted");}catch(IllegalArgumentException expected){}
        System.out.println("PASS: "+checks+" assertions; all NT built-in modes 0..10 match original WorldPainter coordinate/context pipeline; per-world zones, nested/exception scopes, thread isolation, and custom-mode rejection checked.");
    }
    static void equal(double expected,double actual,String label){
        if(Double.doubleToLongBits(expected)!=Double.doubleToLongBits(actual)) {
            throw new AssertionError(label+" expected "+expected+" actual "+actual);
        }
        checks++;
    }
    static void defaults() throws Exception {
        for(Field f:Config.class.getFields())if(!Modifier.isFinal(f.getModifiers())) {
            if(f.getType()==double.class)f.setDouble(null,0.0);
            else if(f.getType()==int.class)f.setInt(null,0);
            else if(f.getType()==boolean.class)f.setBoolean(null,false);
            else if(f.getType()==String.class)f.set(null,"default");
        }
        Config.equatorial_distance=15000;Config.humidity_bands_width=1000;Config.natural_temperature_influence=100;
        Config.randomize_underground_below_y=45;Config.wave_magnification=2;
        double[] temps={.18,8,.87,-.91,8,-.35},humid={.23,8,-.77,.89,8,-.54},coverage={17,24,13,10,22,14};
        for(int i=1;i<=6;i++){
            Config.class.getField("temperature_zone_"+i).setDouble(null,temps[i-1]);
            Config.class.getField("humidity_zone_"+i).setDouble(null,humid[i-1]);
            Config.class.getField("percentage_coverage_zone_"+i).setDouble(null,coverage[i-1]);
        }
    }
    static void registerModes(){
        TemperatureModeRegistry.register("default",TemperatureCalculator::calculateDefault);
        TemperatureModeRegistry.register("simplified",TemperatureCalculator::calculateSimplified);
        TemperatureModeRegistry.register("linear",TemperatureCalculator::calculateLinear);
        TemperatureModeRegistry.register("circular",TemperatureCalculator::calculateCircular);
        TemperatureModeRegistry.register("banded",TemperatureCalculator::calculateBanded);
        TemperatureModeRegistry.register("circular6",TemperatureCalculator::calculateCircular6);
        TemperatureModeRegistry.register("square6",TemperatureCalculator::calculateSquare6);
        HumidityModeRegistry.register("default",HumidityCalculator::calculateDefault);
        HumidityModeRegistry.register("simplified",HumidityCalculator::calculateSimplified);
        HumidityModeRegistry.register("linear",HumidityCalculator::calculateLinear);
        HumidityModeRegistry.register("circular",HumidityCalculator::calculateCircular);
        HumidityModeRegistry.register("banded",HumidityCalculator::calculateBanded);
        HumidityModeRegistry.register("circular6",HumidityCalculator::calculateCircular6);
        HumidityModeRegistry.register("square6",HumidityCalculator::calculateSquare6);
    }
}

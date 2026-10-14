package dev.fnf.terralatitude;

import java.lang.invoke.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import net.minecraft.world.level.biome.Climate;

/** Compiled legacy oracle with supplied inputs, not a world-generation test. */
public final class CompositionCompatibilityTest {
    public record Input(ClimateProfile profile, int x, int y, int z,
                        Climate.TargetPoint base, RawClimateAccess.Sample raw) {}
    private static final ThreadLocal<Input> CURRENT = new ThreadLocal<>();
    private static long checks;
    private static volatile long sink;
    public static Input input() { return CURRENT.get(); }
    public static Climate.TargetPoint currentPoint() {
        Input i = input();
        return compose(i).point();
    }
    private static ClimateComposition.Evaluation compose(Input i) {
        return ClimateComposition.compose(i.profile(), i.x(), i.y(), i.z(), i.base(), i.raw());
    }

    public static void main(String[] args) throws Throwable {
        NaturalClimateAdapterTest.registerModes();
        NaturalClimateAdapterTest.defaults();
        NtSettings defaults = NtSettings.capture();
        Class<?> legacy = Class.forName("dev.fnf.terralatitude.LegacyIntegratedClimate");
        Method evaluate = legacy.getMethod("evaluate", Climate.Sampler.class, int.class, int.class, int.class);
        Class<?> resultType = evaluate.getReturnType();
        String[] numeric = {"bandTemperature", "bandHumidity", "seaLevelTemperature"};
        Method[] getters = Arrays.stream(numeric).map(n -> {
            try { return resultType.getMethod(n); } catch (Exception e) { throw new AssertionError(e); }
        }).toArray(Method[]::new);
        Method point = resultType.getMethod("point"), nativePoint = resultType.getMethod("nativePoint"),
                regional = resultType.getMethod("regional"), cave = resultType.getMethod("randomizedUnderground");
        Random random = new Random(703021);
        List<Input> corpus = new ArrayList<>();
        for (int mode = 0; mode <= 10; mode++) for (int scenario = 0; scenario < 12; scenario++) {
            Map<String,String> settings = new HashMap<>(defaults.toMap());
            settings.put("generation_mode", Integer.toString(mode));
            settings.put("world_seed", Long.toString(scenario % 2 == 0 ? 7287776294461630023L : -3437391787389L));
            settings.put("natural_temperature_influence", new String[]{"0", "37", "100"}[scenario % 3]);
            settings.put("randomize_underground", Boolean.toString(scenario % 2 == 0));
            settings.put("underground_randomization_type", Integer.toString(scenario % 3));
            settings.put("looping_world", Boolean.toString(scenario % 2 != 0));
            settings.put("enable_humidity_bands", Boolean.toString(scenario % 3 != 0));
            NtSettings nt = NtSettings.fromMap(settings);
            ClimateProfile profile = new ClimateProfile(true,
                    new double[]{0, .65, 1}[scenario / 3 % 3], new double[]{.5, 0, 1}[(scenario + scenario / 3) % 3],
                    scenario % 2 == 0 ? 0 : 128, new double[]{0, .5, 1, 2.200000047683716, 4, 32}[scenario % 6],
                    "fixture-preset-" + scenario, nt);
            for (int n = 0; n < 96; n++) {
                int x = (random.nextInt(40001) - 20000) * 4, z = (random.nextInt(40001) - 20000) * 4;
                int y = new int[]{-64, 44, 48, 64, 128, 320}[n % 6];
                Climate.TargetPoint base = new Climate.TargetPoint(random.nextInt(20001)-10000,
                        random.nextInt(20001)-10000, 1234+n, -2345-n, n, 3456+n);
                float e = n % 8 == 0 ? 0 : n % 8 == 1 ? 1 : random.nextFloat();
                RawClimateAccess.Sample raw = new RawClimateAccess.Sample(true,
                        n % 8 == 0 ? -1 : n % 8 == 1 ? 1 : random.nextFloat()*2-1, random.nextFloat()*2-1,
                        base.temperature()/10000.0, base.humidity()/10000.0,
                        100, 62, .5F, .1F, .11F, e, 528, n % 3 != 0);
                Input i = new Input(profile, x, y, z, base, raw);
                CURRENT.set(i);
                Object old = evaluate.invoke(null, null, 0, 0, 0);
                var now = compose(i);
                check(point.invoke(old).equals(now.point()), "quantized climate changed");
                check(nativePoint.invoke(old).equals(now.nativePoint()), "native climate changed");
                check(regional.invoke(old) == now.regional(), "raw-input branch changed");
                check(cave.invoke(old).equals(now.randomizedUnderground()), "cave branch changed");
                double[] values = {now.bandTemperature(), now.bandHumidity(), now.seaLevelTemperature()};
                for (int j=0; j<3; j++) check(Double.doubleToLongBits((double)getters[j].invoke(old))
                        == Double.doubleToLongBits(values[j]), numeric[j] + " changed");
                var p = now.point();
                check(p.continentalness()==base.continentalness() && p.erosion()==base.erosion()
                        && p.depth()==base.depth() && p.weirdness()==base.weirdness(), "unrelated axis changed");
                if (!now.randomizedUnderground()) {
                    if (nt.influence()==0 || profile.temperatureStrength()==0)
                        check(p.temperature()==base.temperature(), "zero temperature influence changed native axis");
                    if (nt.influence()==0 || profile.moistureStrength()==0)
                        check(p.humidity()==base.humidity(), "zero humidity influence changed native axis");
                }
                if (!ClimateComposition.needsRegional(profile, y)) {
                    check(ClimateComposition.compose(profile,x,y,z,base,null).point().equals(p), "bypass requested raw input");
                }
                corpus.add(i);
            }
        }
        // Test actual saved profile decoding and the exact underground threshold separately.
        ClimateProfile saved = ClimateProfile.fromJson(Files.readString(Path.of(args[0])));
        check(saved.toJson().equals(Files.readString(Path.of(args[0]))), "schema-1 profile serialization changed");
        Input i = corpus.stream().filter(c -> ClimateComposition.needsRegional(c.profile(), c.y())).findFirst().orElseThrow();
        try { ClimateComposition.compose(i.profile(),i.x(),i.y(),i.z(),i.base(),null); throw new AssertionError("missing raw accepted"); }
        catch (IllegalStateException expected) { checks++; }
        ClimateProfile disabled = new ClimateProfile(false,1,.5,128,2.2,"fixture",defaults);
        try { ClimateComposition.compose(disabled,0,64,0,i.base(),i.raw()); throw new AssertionError("disabled composition accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        Map<String,String> caves = new HashMap<>(defaults.toMap());
        caves.put("randomize_underground","true");
        ClimateProfile caveProfile = new ClimateProfile(true,1,.5,128,2.2,"caves",NtSettings.fromMap(caves));
        check(!ClimateComposition.needsRegional(caveProfile,45) && ClimateComposition.needsRegional(caveProfile,46), "cave threshold changed");
        // Reversed request order and concurrent immutable profiles must retain their own results.
        List<Climate.TargetPoint> expected = corpus.stream().map(c -> compose(c).point()).toList();
        Collections.reverse(corpus);
        try (ExecutorService pool = Executors.newFixedThreadPool(4)) {
            List<Callable<Climate.TargetPoint>> work = corpus.stream()
                    .map(c -> (Callable<Climate.TargetPoint>)() -> compose(c).point()).toList();
            var results = pool.invokeAll(work);
            for (int n=0;n<results.size();n++) check(results.get(n).get().equals(expected.get(expected.size()-1-n)), "thread/profile isolation");
        }
        CURRENT.remove();
        System.out.println("PASS: " + corpus.size() + " compiled-0.2.1 comparisons; " + checks
                + " assertions; exact T/H and intermediates, six target axes, bypasses, cave threshold, saved schema, and concurrent profiles.");
        benchmark(evaluate, point, corpus);
    }

    private static void benchmark(Method legacy, Method point, List<Input> all) throws Throwable {
        var lookup = MethodHandles.lookup();
        MethodHandle old = MethodHandles.insertArguments(lookup.unreflect(legacy), 0, null, 0, 0, 0);
        old = MethodHandles.filterReturnValue(old, lookup.unreflect(point));
        MethodHandle now = lookup.findStatic(CompositionCompatibilityTest.class,"currentPoint",MethodType.methodType(Climate.TargetPoint.class));
        List<Input> points = all.stream().filter(c -> ClimateComposition.needsRegional(c.profile(),c.y())).toList();
        for (int n=0;n<6;n++) { batch(old,points); batch(now,points); }
        long[] before = new long[7], after = new long[7];
        for (int n=0;n<7;n++) {
            if (n%2==0) { before[n]=batch(old,points); after[n]=batch(now,points); }
            else { after[n]=batch(now,points); before[n]=batch(old,points); }
        }
        System.out.println("COMPOSITION_BENCHMARK " + Arrays.toString(before) + " " + Arrays.toString(after));
        CURRENT.remove();
    }
    private static long batch(MethodHandle method, List<Input> points) throws Throwable {
        long start=System.nanoTime(), sum=0;
        for (int repeat=0;repeat<4;repeat++) for(Input i:points) {
            CURRENT.set(i);
            Climate.TargetPoint p=(Climate.TargetPoint)method.invokeExact();
            sum+=p.temperature()+p.humidity();
        }
        sink=sum;
        return System.nanoTime()-start;
    }
    private static void check(boolean valid,String message) { checks++; if(!valid)throw new AssertionError(message); }
}

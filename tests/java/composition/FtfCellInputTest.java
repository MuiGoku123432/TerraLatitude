package dev.fnf.terralatitude;

import com.mojang.serialization.MapCodec;
import etcodehome.freeterraforged.data.worldgen.preset.settings.WorldSettings;
import etcodehome.freeterraforged.world.worldgen.cell.Cell;
import etcodehome.freeterraforged.world.worldgen.cell.climate.Climate;
import etcodehome.freeterraforged.world.worldgen.cell.climate.ClimateModule;
import etcodehome.freeterraforged.world.worldgen.cell.heightmap.Levels;
import etcodehome.freeterraforged.world.worldgen.cell.terrain.TerrainType;
import etcodehome.freeterraforged.world.worldgen.noise.module.Noise;
import java.lang.reflect.Field;

public final class FtfCellInputTest {
    private static int checks;
    private static final class Probe implements Noise {
        private final float value;
        float x,z; int calls;
        Probe(float value){this.value=value;}
        public float compute(float x,float z,int seed){this.x=x;this.z=z;calls++;return value;}
        public float minValue(){return value;}
        public float maxValue(){return value;}
        public Noise mapAll(Noise.Visitor visitor){return this;}
        public MapCodec<? extends Noise> codec(){throw new UnsupportedOperationException("supplied fixture noise");}
    }
    public static void main(String[] args)throws Exception {
        int cases=0;
        for(int height:new int[]{320,528,1024})for(int sea:new int[]{0,62,128}) {
            var properties=new WorldSettings.Properties(null,height,64,sea,-54,48,0,0);
            properties.worldHeight=height;properties.worldDepth=64;properties.seaLevel=sea;
            Levels levels=new Levels(properties);
            ClimateModule module=ClimateModule.class.getConstructor().newInstance();
            Probe t=new Probe(.25F),h=new Probe(.75F),wx=new Probe(.5F),wz=new Probe(-.25F);
            set(module,"temperature",t);set(module,"moisture",h);set(module,"warpX",wx);set(module,"warpZ",wz);
            set(module,"warpStrength",8F);set(module,"biomeFreq",.125F);
            Probe ox=new Probe(.5F),oz=new Probe(-.5F);
            Climate climate=new Climate(0,ox,oz,100,levels,module);
            for(float blockY:new float[]{-64,sea-1,sea,sea+1,sea+32,height,height+64})
                for(float edge:new float[]{0,.2F,.4F,1}) {
                    Cell cell=new Cell();cell.height=levels.scale((int)blockY);cell.biomeRegionEdge=edge;
                    cell.temperature=.1F;cell.moisture=.2F;
                    var originalTerrain=cell.terrain;
                    float originalHeight=cell.height;
                    int beforeOffsets=ox.calls;
                    var sample=RawClimateAccess.fromCell(climate,levels,cell,-100,200);
                    check(sample.valid(),"valid populated cell");
                    check(sample.surfaceY()==levels.scale(originalHeight),"native FTF height scaling");
                    check(sample.normalizedInlandElevation()==Math.max(0F,Math.min(1F,levels.getNormalizedInlandElevation(originalHeight))),"native FTF elevation");
                    check(sample.aboveGround()==(originalHeight>levels.ground),"native FTF ground gate");
                    check(sample.temperature()==-.5 && sample.humidity()==.5,"raw axes");
                    boolean shift=cell.height>levels.water && edge<.4F;
                    check(ox.calls-beforeOffsets==(shift?1:0),"region edge gate");
                    check(cell.height==originalHeight && cell.temperature==.1F && cell.moisture==.2F
                            && cell.biomeRegionEdge==edge && cell.terrain==originalTerrain,"shared cell mutated");
                    cell.height=-999;cell.temperature=-999;
                    check(sample.cellHeight()==originalHeight && sample.nativeTemperature()==(double).1F,"snapshot retained mutable cell");
                    cases++;
                }
            Cell mountain=new Cell();mountain.height=levels.scale(sea+100);mountain.biomeRegionEdge=.2F;mountain.terrain=TerrainType.MOUNTAIN_CHAIN;
            var sample=RawClimateAccess.fromCell(climate,levels,mountain,-100,200);
            check(sample.valid() && t.x==(-100+25+4)*.125F && t.z==(200-25-2)*.125F,"edge/warp float operation order");
            mountain.height=Float.NaN;
            check(!RawClimateAccess.fromCell(climate,levels,mountain,0,0).valid(),"nonfinite height accepted");
        }
        check(!RawClimateAccess.fromCell(null,null,null,0,0).valid(),"missing inputs");
        System.out.println("PASS: "+cases+" populated-cell cases; "+checks+" assertions; native FTF Levels, heights/gates, raw noise, edge/warp inputs, and immutable cell snapshots.");
    }
    private static void set(Object object,String name,Object value)throws Exception {
        Field f=ClimateModule.class.getDeclaredField(name);f.setAccessible(true);f.set(object,value);
    }
    private static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
}

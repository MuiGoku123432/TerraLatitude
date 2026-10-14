import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
public class NtFixtureTransformer {
 public static void main(String[]args)throws Exception{
  Path out=Path.of(args[1]);
  try(JarFile jar=new JarFile(args[0])){
   for(String name:List.of("com/naturaltemperature/mixin/WorldPainter","com/naturaltemperature/TemperatureCalculator","com/naturaltemperature/HumidityCalculator")){
    ClassNode c=new ClassNode();new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(c,0);
    if(name.endsWith("WorldPainter")){
     for(MethodNode m:c.methods){
      if(m.name.equals("customTemperatureCalculation")){
       AbstractInsnNode n=m.instructions.getFirst();
       while(!(n instanceof FieldInsnNode f && f.owner.equals("com/naturaltemperature/Config") && f.name.equals("equatorial_distance"))){
        AbstractInsnNode next=n.getNext();m.instructions.remove(n);n=next;
       }
       InsnList prefix=new InsnList();
       prefix.add(new FieldInsnNode(Opcodes.GETSTATIC,"dev/fnf/terralatitude/NaturalClimateAdapterTest","rawT","D"));prefix.add(new VarInsnNode(Opcodes.DSTORE,8));
       prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC,"dev/fnf/terralatitude/NaturalClimateAdapterTest","phase","()D",false));prefix.add(new VarInsnNode(Opcodes.DSTORE,6));
       m.instructions.insertBefore(n,prefix);m.localVariables=null;
      } else if(m.name.equals("customHumidityCalculation")){
       AbstractInsnNode n=m.instructions.getFirst();
       while(!(n instanceof MethodInsnNode f && f.owner.equals("com/naturaltemperature/WorldSeedHolder") && f.name.equals("getSeed"))){
        AbstractInsnNode next=n.getNext();m.instructions.remove(n);n=next;
       }
       InsnList prefix=new InsnList();
       prefix.add(new FieldInsnNode(Opcodes.GETSTATIC,"dev/fnf/terralatitude/NaturalClimateAdapterTest","rawH","D"));prefix.add(new VarInsnNode(Opcodes.DSTORE,5));
       prefix.add(new FieldInsnNode(Opcodes.GETSTATIC,"dev/fnf/terralatitude/NaturalClimateAdapterTest","rawT","D"));prefix.add(new VarInsnNode(Opcodes.DSTORE,7));
       m.instructions.insertBefore(n,prefix);m.localVariables=null;
      }
     }
    }else{
     // Apply the compiled redirect handlers to fixture bytes. This deliberately
     // does not bootstrap Mixin: it proves numerical behavior and handler coverage,
     // not live mixin ordering/application in a complete modpack.
     String kind=name.endsWith("TemperatureCalculator")?"Temperature":"Humidity";
     ClassNode mixin=new ClassNode();
     new ClassReader(Files.readAllBytes(out.resolve("dev/fnf/terralatitude/mixin/Nt"+kind+"SettingsMixin.class"))).accept(mixin,0);
     c.interfaces.addAll(mixin.interfaces);
     int handlers=0,sites=0;
     for(MethodNode handler:mixin.methods) {
      if(handler.visibleAnnotations==null)continue;
      for(AnnotationNode redirect:handler.visibleAnnotations) {
       if(!redirect.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))continue;
       @SuppressWarnings("unchecked") List<String> methods=(List<String>)value(redirect,"method");
       AnnotationNode at=(AnnotationNode)value(redirect,"at");
       String target=(String)value(at,"target");
       String field=target.substring(target.indexOf(';')+1,target.indexOf(':'));
       int matched=0;
       for(MethodNode m:c.methods)if(methods.contains(m.name)) {
        for(AbstractInsnNode n:m.instructions.toArray()) {
         if(n instanceof FieldInsnNode f && f.getOpcode()==Opcodes.GETSTATIC
             && f.owner.equals("com/naturaltemperature/Config") && f.name.equals(field)) {
          m.instructions.set(n,new MethodInsnNode(Opcodes.INVOKESTATIC,name,handler.name,"()D",false));
          matched++;
         }
        }
       }
       if(matched==0)throw new AssertionError("Unmatched calculator redirect: "+field);
       handler.visibleAnnotations=null;handler.invisibleAnnotations=null;
       c.methods.add(handler);handlers++;sites+=matched;
      }
     }
     if(handlers!=12)throw new AssertionError("Expected12 redirect handlers, got "+handlers);
     System.out.println(kind+" calculator: "+handlers+" compiled redirect handlers applied to "+sites+" test-fixture field reads");
    }
    ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);c.accept(w);
    Path dst=out.resolve(name+".class");Files.createDirectories(dst.getParent());Files.write(dst,w.toByteArray());
   }
  }
 }
 static Object value(AnnotationNode node,String key) {
  for(int i=0;i<node.values.size();i+=2)if(key.equals(node.values.get(i)))return node.values.get(i+1);
  throw new IllegalArgumentException("Missing annotation value "+key);
 }
}

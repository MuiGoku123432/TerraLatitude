import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.points.MethodHead;
import org.spongepowered.asm.mixin.injection.struct.Target;
import sun.misc.Unsafe;

/**
 * Bounded regression using the installed library's real comparator, HEAD locator,
 * injection-node tracking and instruction insertion. The callback weaving shell
 * mirrors CallbackInjector's cancellable HEAD contract; full Mixin bootstrap and
 * Minecraft are deliberately not run.
 */
public final class OrderRegression implements Opcodes {
    public static class FixtureTarget {
        public String sample(boolean owned) { return "vanilla"; }
    }
    public static class HandlerBodies {
        private void nt(boolean owned, CallbackInfoReturnable<String> cir) { cir.setReturnValue("NT"); }
        private void tl(boolean owned, CallbackInfoReturnable<String> cir) { if (owned) cir.setReturnValue("TL"); }
    }
    record Definition(String name, int priority, int registrationOrder, int injectorOrder, MethodNode handler) {}
    private static final Unsafe UNSAFE;
    static {
        try {
            Field field=Unsafe.class.getDeclaredField("theUnsafe");field.setAccessible(true);UNSAFE=(Unsafe)field.get(null);
        } catch(Exception exception) { throw new ExceptionInInitializerError(exception); }
    }
    static int checks;
    static Path outputDirectory;
    public static void main(String[]args)throws Exception {
        Path ntJar=Path.of(args[0]),tlJar=Path.of(args[1]);
        outputDirectory=Path.of(args[2]);
        verifyApplicatorStructure();
        ClassNode nt=readJar(ntJar,"com/naturaltemperature/mixin/WorldPainter.class");
        ClassNode tl=readJar(tlJar,"dev/fnf/terralatitude/mixin/IntegratedSamplerMixin.class");
        MethodNode nativeHandler=nt.methods.stream().filter(m->m.name.equals("modifyTemperature")).findFirst().orElseThrow();
        MethodNode addonHandler=tl.methods.stream().filter(m->m.name.equals("terraLatitude$sample")).findFirst().orElseThrow();
        int ntPriority=priority(nt), tlPriority=priority(tl), ntOrder=order(nativeHandler), tlOrder=order(addonHandler);
        check(ntPriority==1000,"installed NT priority="+ntPriority);
        check(ntOrder==1000,"installed NT injector order="+ntOrder);
        check(tlPriority==2000,"installed TL priority="+tlPriority);
        ClassNode bodies=readResource("OrderRegression$HandlerBodies.class");
        MethodNode ntBody=bodies.methods.stream().filter(m->m.name.equals("nt")).findFirst().orElseThrow();
        MethodNode tlBody=bodies.methods.stream().filter(m->m.name.equals("tl")).findFirst().orElseThrow();
        // Always retain the original regression, even when the supplied addon JAR is fixed.
        Definition nativeDefinition=new Definition("NT",ntPriority,0,ntOrder,ntBody);
        Definition oldDefinition=new Definition("TL",tlPriority,1,1000,tlBody);
        Definition fixedDefinition=new Definition("TL",tlPriority,1,900,tlBody);
        evaluate("old",nativeDefinition,oldDefinition,"NT","NT");
        evaluate("fixed",nativeDefinition,fixedDefinition,"TL","NT");
        // Reverse registration order: the explicit injector order must remain decisive.
        evaluate("fixed_reversed_registration",new Definition("NT",ntPriority,1,ntOrder,ntBody),
                new Definition("TL",tlPriority,0,900,tlBody),"TL","NT");
        evaluate("fixed_equal_priority",nativeDefinition,new Definition("TL",1000,1,900,tlBody),"TL","NT");
        check(tlOrder==900,"supplied addon must contain the injector-order fix; actual order="+tlOrder);
        evaluate("installed_fixed",nativeDefinition,new Definition("TL",tlPriority,1,tlOrder,tlBody),"TL","NT");
        System.out.println("PASS: "+checks+" assertions. Installed NT priority/order="+ntPriority+"/"+ntOrder+
                "; installed TL priority/order="+tlPriority+"/"+tlOrder);
        System.out.println("LIMIT: actual installed Mixin ordering/HEAD/Target components, synthetic cancellable handlers; no full Mixin bootstrap, FTF wrapping, or Minecraft execution.");
    }
    static void verifyApplicatorStructure() throws Exception {
        ClassNode type=readResource("org/spongepowered/asm/mixin/transformer/MixinApplicatorStandard.class");
        MethodNode method=type.methods.stream().filter(m->m.name.equals("apply") && m.desc.equals("(Ljava/util/SortedSet;)V")).findFirst().orElseThrow();
        int treeSet=-1,orders=-1,apply=-1,index=0;
        for(AbstractInsnNode instruction:method.instructions) {
            if(instruction instanceof TypeInsnNode n && n.getOpcode()==NEW && n.desc.equals("java/util/TreeSet"))treeSet=index;
            if(instruction instanceof MethodInsnNode n && n.name.equals("getInjectorOrders"))orders=index;
            if(instruction instanceof MethodInsnNode n && n.name.equals("applyMixin"))apply=index;
            index++;
        }
        check(treeSet>=0 && orders>treeSet && apply>orders,"installed applicator collects sorted injector orders before applying injections");
        ClassNode callback=readResource("org/spongepowered/asm/mixin/injection/callback/CallbackInjector$Callback.class");
        MethodNode inject=callback.methods.stream().filter(m->m.name.equals("inject") && m.desc.equals("()V")).findFirst().orElseThrow();
        boolean delegates=false;
        for(AbstractInsnNode instruction:inject.instructions)if(instruction instanceof MethodInsnNode n
                && n.owner.equals("org/spongepowered/asm/mixin/injection/struct/Target") && n.name.equals("insertBefore"))delegates=true;
        check(delegates,"installed CallbackInjector delegates insertion to tested Target.insertBefore");
    }
    static void evaluate(String label,Definition nt,Definition tl,String expectedOwned,String expectedOther)throws Exception {
        List<Definition> definitions=new ArrayList<>(List.of(nt,tl));
        // Invoke the installed MixinInfo comparator, not a guessed priority comparator.
        Map<Definition,Object> infos=new IdentityHashMap<>();for(var d:definitions)infos.put(d,info(d));
        Class<?> infoClass=Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");
        Method compare=infoClass.getDeclaredMethod("compareTo",infoClass);compare.setAccessible(true);
        definitions.sort((a,b)->{try{return (Integer)compare.invoke(infos.get(a),infos.get(b));}catch(Exception e){throw new RuntimeException(e);}});
        ClassNode target=readResource("OrderRegression$FixtureTarget.class");
        // Each run gets a fresh target definition in an isolated class loader.
        MethodNode sample=target.methods.stream().filter(m->m.name.equals("sample")).findFirst().orElseThrow();
        Target mixinTarget=Target.of(null,target,sample);
        MethodHead head=(MethodHead)UNSAFE.allocateInstance(MethodHead.class);
        Map<Definition,org.spongepowered.asm.mixin.injection.struct.InjectionNodes.InjectionNode> nodes=new IdentityHashMap<>();
        for(var d:definitions){
            List<AbstractInsnNode> matches=new ArrayList<>();check(head.find(sample.desc,sample.instructions,matches),"HEAD found");
            check(matches.size()==1,"single HEAD");nodes.put(d,mixinTarget.addInjectionNode(matches.getFirst()));
        }
        check(nodes.get(nt)==nodes.get(tl),"both callbacks share the original HEAD anchor");
        TreeSet<Integer> orders=new TreeSet<>();definitions.forEach(d->orders.add(d.injectorOrder()));
        List<String> execution=new ArrayList<>();
        for(int order:orders)for(var definition:definitions)if(definition.injectorOrder()==order){
            MethodNode handler=cloneHandler(definition.handler(),"fixture$"+definition.name());target.methods.add(handler);
            mixinTarget.insertBefore(nodes.get(definition),callback(target.name,handler.name));
            execution.add(definition.name());
        }
        ClassWriter writer=new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);target.accept(writer);
        byte[] bytes=writer.toByteArray();Files.write(outputDirectory.resolve(label+".class"),bytes);
        Class<?> generated=new ClassLoader(OrderRegression.class.getClassLoader()) { Class<?> define(){return defineClass(target.name.replace('/','.'),bytes,0,bytes.length);} }.define();
        Object instance=generated.getDeclaredConstructor().newInstance();Method call=generated.getMethod("sample",boolean.class);
        String owned=(String)call.invoke(instance,true),other=(String)call.invoke(instance,false);
        check(owned.equals(expectedOwned),label+" owned returned "+owned+" expected "+expectedOwned);
        check(other.equals(expectedOther),label+" non-owned returned "+other+" expected "+expectedOther);
        System.out.println(label+": callbacks="+execution+", owned="+owned+", non-owned="+other);
    }
    static InsnList callback(String owner,String handler){
        String callback="org/spongepowered/asm/mixin/injection/callback/CallbackInfoReturnable";
        InsnList code=new InsnList();LabelNode notCancelled=new LabelNode();
        code.add(new TypeInsnNode(NEW,callback));code.add(new InsnNode(DUP));code.add(new LdcInsnNode("sample"));code.add(new InsnNode(ICONST_1));
        code.add(new MethodInsnNode(INVOKESPECIAL,callback,"<init>","(Ljava/lang/String;Z)V",false));code.add(new VarInsnNode(ASTORE,2));
        code.add(new VarInsnNode(ALOAD,0));code.add(new VarInsnNode(ILOAD,1));code.add(new VarInsnNode(ALOAD,2));
        code.add(new MethodInsnNode(INVOKESPECIAL,owner,handler,"(ZL"+callback+";)V",false));
        code.add(new VarInsnNode(ALOAD,2));code.add(new MethodInsnNode(INVOKEVIRTUAL,callback,"isCancelled","()Z",false));
        code.add(new JumpInsnNode(IFEQ,notCancelled));code.add(new VarInsnNode(ALOAD,2));
        code.add(new MethodInsnNode(INVOKEVIRTUAL,callback,"getReturnValue","()Ljava/lang/Object;",false));code.add(new TypeInsnNode(CHECKCAST,"java/lang/String"));
        code.add(new InsnNode(ARETURN));code.add(notCancelled);return code;
    }
    static Object info(Definition d)throws Exception{
        Class<?> type=Class.forName("org.spongepowered.asm.mixin.transformer.MixinInfo");Object info=UNSAFE.allocateInstance(type);
        for(var entry:Map.of("priority",d.priority(),"order",d.registrationOrder()).entrySet()) {
            Field f=type.getDeclaredField(entry.getKey());f.setAccessible(true);f.setInt(info,entry.getValue());
        }
        return info;
    }
    static int priority(ClassNode node){
        for(AnnotationNode a:node.invisibleAnnotations)if(a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))return (int)value(a,"priority",1000);
        throw new AssertionError("No Mixin annotation");
    }
    static int order(MethodNode node)throws Exception{
        int defaultOrder=(Integer)Inject.class.getMethod("order").getDefaultValue();
        for(AnnotationNode a:node.visibleAnnotations)if(a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;"))return (int)value(a,"order",defaultOrder);
        throw new AssertionError("No Inject annotation");
    }
    static Object value(AnnotationNode annotation,String name,Object fallback){
        if(annotation.values!=null)for(int i=0;i<annotation.values.size();i+=2)if(name.equals(annotation.values.get(i)))return annotation.values.get(i+1);
        return fallback;
    }
    static MethodNode cloneHandler(MethodNode source,String name){
        MethodNode clone=new MethodNode(ASM9,ACC_PRIVATE,name,source.desc,source.signature,null);source.accept(clone);clone.name=name;return clone;
    }
    static ClassNode readResource(String path)throws Exception{try(var input=OrderRegression.class.getClassLoader().getResourceAsStream(path)){ClassNode n=new ClassNode();new ClassReader(input).accept(n,0);return n;}}
    static ClassNode readJar(Path jar,String path)throws Exception{try(JarFile file=new JarFile(jar.toFile())){ClassNode n=new ClassNode();new ClassReader(file.getInputStream(file.getJarEntry(path))).accept(n,0);return n;}}
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);checks++;}
}

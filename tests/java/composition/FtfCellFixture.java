import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Test-only empty construction for explicitly supplied noise inputs. */
public final class FtfCellFixture {
    public static void main(String[] args) throws Exception {
        String name="etcodehome/freeterraforged/world/worldgen/cell/climate/ClimateModule";
        Path out=Path.of(args[1]);
        try(JarFile jar=new JarFile(args[0])) {
            ClassNode c=new ClassNode();
            new ClassReader(jar.getInputStream(jar.getJarEntry(name+".class"))).accept(c,0);
            if(!c.superName.equals("java/lang/Object"))throw new AssertionError("FTF module superclass changed");
            MethodNode init=new MethodNode(Opcodes.ACC_PUBLIC,"<init>","()V",null,null);
            init.instructions.add(new VarInsnNode(Opcodes.ALOAD,0));
            init.instructions.add(new MethodInsnNode(Opcodes.INVOKESPECIAL,"java/lang/Object","<init>","()V",false));
            init.instructions.add(new InsnNode(Opcodes.RETURN));c.methods.add(init);
            ClassWriter w=new ClassWriter(ClassWriter.COMPUTE_MAXS);c.accept(w);
            Path file=out.resolve(name+".class");Files.createDirectories(file.getParent());Files.write(file,w.toByteArray());
        }
        System.out.println("FTF cell fixture: public native API; test-only empty noise-module constructor. Native Levels/Cell code unchanged.");
    }
}

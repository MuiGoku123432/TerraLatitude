import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import org.objectweb.asm.tree.*;

/** Supply inputs to the compiled 0.2.1 composition; preserve its arithmetic bytecode. */
public final class LegacyCompositionFixture {
    private static final String P = "dev/fnf/terralatitude/";
    private static final String OLD = P + "IntegratedClimate";
    private static final String TEST = P + "CompositionCompatibilityTest";
    private static final String INPUT = TEST + "$Input";

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[1]);
        try (JarFile jar = new JarFile(args[0])) {
            for (String name : List.of(OLD, OLD + "$Evaluation", P + "ClimateMath")) {
                ClassNode c = new ClassNode();
                new ClassReader(jar.getInputStream(jar.getJarEntry(name + ".class")))
                        .accept(c, ClassReader.SKIP_FRAMES);
                if (name.equals(OLD)) injectInputs(c);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
                c.accept(new ClassRemapper(writer, new Remapper() {
                    @Override public String map(String type) {
                        if (type.equals(P + "ClimateMath")) return P + "LegacyClimateMath";
                        return type.equals(OLD) || type.startsWith(OLD + "$")
                                ? type.replace(OLD, P + "LegacyIntegratedClimate") : type;
                    }
                }));
                Path file = out.resolve(name.replace(OLD, P + "LegacyIntegratedClimate").replace(P + "ClimateMath", P + "LegacyClimateMath") + ".class");
                Files.createDirectories(file.getParent());
                Files.write(file, writer.toByteArray());
            }
        }
        System.out.println("Legacy fixture: compiled 0.2.1 composition and independent original ClimateMath retained; world ownership, native density and raw terrain acquisition supplied by test inputs.");
    }

    private static void injectInputs(ClassNode c) {
        MethodNode m = c.methods.stream().filter(n -> n.name.equals("evaluate")).findFirst().orElseThrow();
        MethodInsnNode profile = call(m, P + "WorldProfiles", "forSampler");
        int profileSlot = storeAfter(profile);
        int baseSlot = storeAfter(call(m, OLD, "nativeSample"));
        List<Integer> positions = new ArrayList<>();
        for (AbstractInsnNode n : m.instructions) {
            if (n instanceof MethodInsnNode call && call.owner.equals("net/minecraft/core/QuartPos")
                    && call.name.equals("toBlock")) positions.add(storeAfter(call));
        }
        if (positions.size() != 3) throw new AssertionError("Legacy coordinate shape changed");
        MethodInsnNode nt = call(m, P + "ClimateProfile", "naturalTemperature");
        AbstractInsnNode begin = nt.getPrevious();
        if (!(begin instanceof VarInsnNode v) || v.getOpcode() != Opcodes.ALOAD || v.var != profileSlot)
            throw new AssertionError("Legacy composition start changed");
        while (m.instructions.getFirst() != begin) m.instructions.remove(m.instructions.getFirst());
        int inputSlot = m.maxLocals++;
        InsnList prefix = new InsnList();
        prefix.add(new MethodInsnNode(Opcodes.INVOKESTATIC, TEST, "input", "()L" + INPUT + ";", false));
        prefix.add(new VarInsnNode(Opcodes.ASTORE, inputSlot));
        input(prefix, inputSlot, "profile", "L" + P + "ClimateProfile;", profileSlot, Opcodes.ASTORE);
        input(prefix, inputSlot, "base", "Lnet/minecraft/world/level/biome/Climate$TargetPoint;", baseSlot, Opcodes.ASTORE);
        for (int i = 0; i < 3; i++) input(prefix, inputSlot, new String[]{"x", "y", "z"}[i], "I", positions.get(i), Opcodes.ISTORE);
        m.instructions.insertBefore(begin, prefix);
        MethodInsnNode raw = call(m, P + "RawClimateAccess", "sample");
        // Only remove the original surfaceContext, X and Z argument preparation.
        AbstractInsnNode z = raw.getPrevious(), x = z.getPrevious(), context = x.getPrevious(), semantics = context.getPrevious();
        if (!(z instanceof VarInsnNode zv) || zv.var != positions.get(2)
                || !(x instanceof VarInsnNode xv) || xv.var != positions.get(0)
                || !(context instanceof MethodInsnNode mc) || !mc.name.equals("surfaceContext")
                || !(semantics instanceof VarInsnNode)) throw new AssertionError("Legacy raw input shape changed");
        for (AbstractInsnNode n : List.of(z, x, context, semantics)) m.instructions.remove(n);
        InsnList replacement = new InsnList();
        replacement.add(new VarInsnNode(Opcodes.ALOAD, inputSlot));
        replacement.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, INPUT, "raw", "()L" + P + "RawClimateAccess$Sample;", false));
        m.instructions.insertBefore(raw, replacement);
        m.instructions.remove(raw);
        m.localVariables = null;
    }

    private static void input(InsnList code, int inputSlot, String field, String desc, int slot, int opcode) {
        code.add(new VarInsnNode(Opcodes.ALOAD, inputSlot));
        code.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, INPUT, field, "()" + desc, false));
        code.add(new VarInsnNode(opcode, slot));
    }
    private static MethodInsnNode call(MethodNode m, String owner, String name) {
        for (AbstractInsnNode n : m.instructions)
            if (n instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name)) return c;
        throw new AssertionError("Missing legacy call " + owner + "." + name);
    }
    private static int storeAfter(AbstractInsnNode n) {
        if (!(n.getNext() instanceof VarInsnNode v)) throw new AssertionError("Legacy local store changed");
        return v.var;
    }
}

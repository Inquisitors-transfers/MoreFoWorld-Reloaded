package me.hsgamer.morefoworld.initializer;

import java.io.IOException;
import java.lang.classfile.ClassFile;
import java.lang.classfile.Opcode;
import java.lang.classfile.constantpool.ClassEntry;
import java.lang.classfile.constantpool.FieldRefEntry;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.classfile.instruction.FieldInstruction;
import java.lang.classfile.instruction.InvokeInstruction;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

/** Checks the compiled adapter itself, so probes cannot drift from its direct server dependencies. */
final class RuntimeAdapterCheck {
    private RuntimeAdapterCheck() {}

    static List<String> check(String adapter, ClassLoader loader) throws IOException {
        return check(adapter, loader, name -> name.startsWith("net.minecraft.")
                || name.startsWith("org.bukkit.") || name.startsWith("io.papermc.")
                || name.startsWith("ca.spottedleaf."));
    }

    static List<String> check(String adapter, ClassLoader loader, Predicate<String> serverClass) throws IOException {
        byte[] bytes;
        try (var input = loader.getResourceAsStream(adapter.replace('.', '/') + ".class")) {
            if (input == null) throw new IOException("Missing bundled adapter " + adapter);
            bytes = input.readAllBytes();
        }
        var model = ClassFile.of().parse(bytes);
        Set<String> problems = new LinkedHashSet<>();
        // Includes method-handle targets used by lambdas, not only ordinary invoke instructions.
        for (var entry : model.constantPool()) {
            if (entry instanceof MemberRefEntry reference && serverClass.test(className(reference.owner()))) {
                inspect(problems, reference, () -> resolve(reference, loader));
            } else if (entry instanceof ClassEntry type && serverClass.test(className(type))) {
                try {
                    Class.forName(className(type), false, loader);
                } catch (ClassNotFoundException | LinkageError exception) {
                    problems.add(className(type) + ": " + exception);
                }
            }
        }
        for (var method : model.methods()) {
            if (method.code().isEmpty()) continue;
            for (var element : method.code().get()) {
                if (element instanceof FieldInstruction field && serverClass.test(className(field.owner()))) {
                    inspect(problems, field.field(), () -> {
                        Member member = resolve(field.field(), loader);
                        boolean isStatic = field.opcode() == Opcode.GETSTATIC || field.opcode() == Opcode.PUTSTATIC;
                        checkStatic(member, isStatic);
                        if ((field.opcode() == Opcode.PUTFIELD || field.opcode() == Opcode.PUTSTATIC)
                                && Modifier.isFinal(member.getModifiers())) {
                            throw new IllegalAccessException("field became final");
                        }
                        return member;
                    });
                } else if (element instanceof InvokeInstruction invoke && serverClass.test(className(invoke.owner()))) {
                    inspect(problems, invoke.method(), () -> {
                        Class<?> owner = Class.forName(className(invoke.owner()), false, loader);
                        if (owner.isInterface() != invoke.isInterface()) {
                            throw new IncompatibleClassChangeError("class/interface kind changed");
                        }
                        Member member = resolve(invoke.method(), loader);
                        checkStatic(member, invoke.opcode() == Opcode.INVOKESTATIC);
                        return member;
                    });
                }
            }
        }
        return new ArrayList<>(problems);
    }

    private static String className(ClassEntry entry) {
        return entry.asInternalName().replace('/', '.');
    }

    private static void checkStatic(Member member, boolean expected) throws IllegalAccessException {
        if (Modifier.isStatic(member.getModifiers()) != expected) {
            throw new IllegalAccessException("static/instance kind changed");
        }
    }

    private static Member resolve(MemberRefEntry reference, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> owner = Class.forName(className(reference.owner()), false, loader);
        if (!Modifier.isPublic(owner.getModifiers())) throw new IllegalAccessException("owner is not public");
        String name = reference.name().stringValue();
        String descriptor = reference.type().stringValue();
        Member member;
        if (reference instanceof FieldRefEntry) {
            Class<?> type = MethodType.fromMethodDescriptorString("()" + descriptor, loader).returnType();
            member = findField(owner, name, type);
        } else {
            MethodType type = MethodType.fromMethodDescriptorString(descriptor, loader);
            member = name.equals("<init>") ? owner.getConstructor(type.parameterArray()) : findMethod(owner, name, type);
        }
        if (!Modifier.isPublic(member.getModifiers())) throw new IllegalAccessException("member is not public");
        return member;
    }

    private static Field findField(Class<?> owner, String name, Class<?> type) throws NoSuchFieldException {
        for (Field field : owner.getDeclaredFields()) {
            if (field.getName().equals(name) && field.getType() == type) return field;
        }
        for (Class<?> parent : owner.getInterfaces()) {
            try { return findField(parent, name, type); } catch (NoSuchFieldException ignored) { }
        }
        if (owner.getSuperclass() != null) return findField(owner.getSuperclass(), name, type);
        throw new NoSuchFieldException(name + ":" + type.getTypeName());
    }

    private static Method findMethod(Class<?> owner, String name, MethodType type) throws NoSuchMethodException {
        for (Method method : owner.getDeclaredMethods()) {
            if (method.getName().equals(name) && method.getReturnType() == type.returnType()
                    && Arrays.equals(method.getParameterTypes(), type.parameterArray())) return method;
        }
        if (owner.getSuperclass() != null) {
            try { return findMethod(owner.getSuperclass(), name, type); } catch (NoSuchMethodException ignored) { }
        }
        for (Class<?> parent : owner.getInterfaces()) {
            try { return findMethod(parent, name, type); } catch (NoSuchMethodException ignored) { }
        }
        throw new NoSuchMethodException(name + type);
    }

    private static void inspect(Set<String> problems, MemberRefEntry reference, Probe probe) {
        try {
            probe.run();
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            problems.add(className(reference.owner()) + "." + reference.name().stringValue()
                    + reference.type().stringValue() + ": " + exception);
        }
    }

    @FunctionalInterface
    private interface Probe {
        Member run() throws ReflectiveOperationException;
    }
}

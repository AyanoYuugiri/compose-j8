package me.yuugiri.ap.asm;

import me.yuugiri.ap.util.ClassUtil;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public final class ClassPatcher {

    public static byte[] doPatch(final byte[] in) {
        final ClassNode node = ClassUtil.readClass(in);

        node.version = 52; // downgrade to java 8
        AtomicInteger generated = new AtomicInteger();

        node.methods.stream().collect(Collectors.toList())
                .forEach(methodNode -> {
                    for (int i = 0; i < methodNode.instructions.size(); ++i) {
                        final AbstractInsnNode abstractInsnNode = methodNode.instructions.get(i);
                        if (abstractInsnNode instanceof MethodInsnNode) {
                            final MethodInsnNode min = (MethodInsnNode) abstractInsnNode;
                            if (min.owner.equals("java/nio/ByteBuffer") && min.name.equals("position") && min.desc.equals("(I)Ljava/nio/ByteBuffer;")) {
                                System.out.println("[!] Patched ByteBuffer-0");
                                min.desc = "(I)Ljava/nio/Buffer;";
                            }
                        } else if (abstractInsnNode instanceof InvokeDynamicInsnNode) {
                            final InvokeDynamicInsnNode idin = (InvokeDynamicInsnNode) abstractInsnNode;
                            if (idin.name.equals("makeConcatWithConstants")/* && idin.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")*/ &&
                                    idin.bsm != null && idin.bsm.getOwner().equals("java/lang/invoke/StringConcatFactory") && idin.bsm.getDesc().equals("(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/invoke/CallSite;")
                                    && idin.bsmArgs.length == 1 && idin.bsmArgs[0] instanceof String) {
                                String arg = (String) idin.bsmArgs[0];
                                if (!idin.desc.equals("(Ljava/lang/String;)Ljava/lang/String;")) {
                                    System.out.println(idin.desc);
                                    System.out.println(node.name + "." + methodNode.name);
                                }
                                String generatedName = "$StringConcatFactory" + generated;
                                generated.getAndIncrement();
                                methodNode.instructions.insert(idin, new MethodInsnNode(Opcodes.INVOKESTATIC, node.name, generatedName, idin.desc));
                                genMethodConcat(node, generatedName, idin.desc, arg);
                                methodNode.instructions.remove(idin);
                            }
                        }
                    }
                });

        return ClassUtil.writeClass(node);
    }

    private static void genMethodConcat(ClassNode node, String methodName, String methodDesc, String template) {
//        System.out.println(node.name);
        MethodNode method = new MethodNode(9, methodName, methodDesc, null, new String[0]);
        List<AbstractInsnNode> nodes = new ArrayList<>();
        nodes.add(new TypeInsnNode(Opcodes.NEW, "java/lang/StringBuilder"));
        nodes.add(new InsnNode(Opcodes.DUP));
        nodes.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/lang/StringBuilder", "<init>", "()V"));

        String[] args; {
            String[] a = methodDesc.substring(1, methodDesc.indexOf(")")).split(";");
            List<String> b = new ArrayList<>();
            for (int i = 0; i < a.length; i++) {
                String spice = a[i];
                while (spice.length() != 0) {
                    char c = spice.charAt(0);
                    if (c == 'L') {
                        b.add(spice);
                        spice = "";
                        break;
                    }
                    b.add("" + c);
                    spice = spice.substring(1);
                }
            }
            args = b.toArray(new String[0]);
        }
        String[] spice = template.split("\u0001");
        int argCount = 0;
        for (int i = 0; i < spice.length; i++) {
            if (!spice[i].equals("")) {
                nodes.add(new LdcInsnNode(spice[i]));
                nodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/String;)Ljava/lang/StringBuilder;"));
            }
            if (argCount != args.length) {
                appendArg(nodes, argCount, args[argCount]);
                argCount++;
            }
        }
//        System.out.println(argCount);
        while (argCount != args.length) {
            appendArg(nodes, argCount, args[argCount]);
            argCount++;
        }
        nodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "toString", "()Ljava/lang/String;"));
        nodes.add(new InsnNode(Opcodes.ARETURN));

        LabelNode ln = new LabelNode();
        method.instructions.insert(ln);
        method.instructions.insert(new LabelNode());
        for (AbstractInsnNode n : nodes) {
            method.instructions.insertBefore(ln, n);
        }

        node.methods.add(method);
    }

    private static void appendArg(List<AbstractInsnNode> nodes, int argCount, String spice) {
        System.out.println(spice);
        if (spice.startsWith("L")) {
            nodes.add(new VarInsnNode(Opcodes.ALOAD, argCount));
            nodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
        } else {
            nodes.add(new VarInsnNode(Opcodes.ILOAD, argCount));
            if (spice.length() == 1) {
                nodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "("+spice+")Ljava/lang/StringBuilder;"));
            } else {
                nodes.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "java/lang/StringBuilder", "append", "(Ljava/lang/Object;)Ljava/lang/StringBuilder;"));
            }
        }
    }

    private static String a(boolean a) {
        return "a" + a;
    }
}

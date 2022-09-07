package me.yuugiri.ap.asm;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;

public final class JarPatcher {

    public static byte[] doPatch(final byte[] in) throws IOException {
        final JarInputStream jis = new JarInputStream(new ByteArrayInputStream(in));
        final ByteArrayOutputStream out = new ByteArrayOutputStream();

        doPatch(jis, out);

        byte[] outArray = out.toByteArray();
        System.out.println("Patched binary (in="+in.length+", out="+out.size()+")");
        return outArray;
    }

    public static void doPatch(final InputStream src, final OutputStream dst) throws IOException {
        final JarInputStream jis = new JarInputStream(src);
        final JarOutputStream jos = new JarOutputStream(dst);

        JarEntry entry;
        byte[] buffer = new byte[1024];
        while ((entry = jis.getNextJarEntry()) != null) {
            if (entry.isDirectory()) continue;
            byte[] body;
            {
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                int n = -1;
                while ((n = jis.read(buffer)) != -1) {
                    bos.write(buffer, 0, n);
                }
                bos.close();
                body = bos.toByteArray();
            }
            if (entry.getName().endsWith(".class")) {
                body = ClassPatcher.doPatch(body);
            }
            jos.putNextEntry(new JarEntry(entry.getName()));
            jos.write(body);
            jos.closeEntry();
        }
        jos.close();
    }
}

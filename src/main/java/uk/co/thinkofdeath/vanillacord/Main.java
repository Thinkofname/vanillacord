package uk.co.thinkofdeath.vanillacord;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

import java.io.*;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Args: <server file>");
            return;
        }

        String filename = args[0];
        File in = new File(filename);
        addURL(in.toURI().toURL());

        File out = new File(in.getName().replace(".jar","")+"-bungee.jar");
        if (out.exists()) out.delete();

        try (ZipInputStream zip = new ZipInputStream(new FileInputStream(in));
             ZipOutputStream zop = new ZipOutputStream(new FileOutputStream(out));
             InputStream helper = Main.class.getResourceAsStream("/uk/co/thinkofdeath/vanillacord/util/BungeeHelper.class")) {

            HashMap<String, byte[]> classes = new HashMap<>();

            System.out.println("Loading");

            // So Mojang managed to include the same file
            // multiple times in the server jar. This
            // (not being valid) causes java to (correctly)
            // throw an exception so we need to track what
            // files have been added to a jar so that we
            // don't add them twice
            Set<String> mojangCantEvenJar = new HashSet<>();

            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".class")) {
                    if (mojangCantEvenJar.add(entry.getName())) {
                        zop.putNextEntry(entry);
                        ByteStreams.copy(zip, zop);
                    }
                    continue;
                }
                byte[] clazz = ByteStreams.toByteArray(zip);
                classes.put(entry.getName(), clazz);
            }

            String handshakePacket = null;
            String loginListener = null;
            String networkManager = null;

            for (Map.Entry<String, byte[]> e : new HashMap<>(classes).entrySet()) {
                byte[] clazz = e.getValue();
                ClassReader reader = new ClassReader(clazz);
                TypeChecker typeChecker = new TypeChecker();
                reader.accept(typeChecker, 0);

                if (typeChecker.isHandshakeListener()) {
                    System.out.println("Found the handshake listener in " + e.getKey());

                    reader = new ClassReader(clazz);
                    ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                    HandshakeListener hsl = new HandshakeListener(classWriter, typeChecker);
                    reader.accept(hsl, 0);
                    clazz = classWriter.toByteArray();

                    handshakePacket = hsl.getHandshake();
                    networkManager = hsl.getNetworkManager();
                } else if (typeChecker.isLoginListener()) {
                    System.out.println("Found the login listener in " + e.getKey());
                    loginListener = e.getKey();
                }
                classes.put(e.getKey(), clazz);
            }
            // Increase the hostname field size
            {
                byte[] clazz = classes.get(handshakePacket + ".class");
                ClassReader reader = new ClassReader(clazz);
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                reader.accept(new HandshakePacket(classWriter), 0);
                clazz = classWriter.toByteArray();
                classes.put(handshakePacket + ".class", clazz);
            }
            // Inject the profile injector and force offline mode
            {
                byte[] clazz = classes.get(loginListener);
                ClassReader reader = new ClassReader(clazz);
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                reader.accept(new LoginListener(classWriter, networkManager), 0);
                clazz = classWriter.toByteArray();
                classes.put(loginListener, clazz);
            }
            // Change the server brand
            {
                byte[] clazz = classes.get("net/minecraft/server/MinecraftServer.class");
                ClassReader classReader = new ClassReader(clazz);
                ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
                classReader.accept(new MCBrand(classWriter), 0);
                clazz = classWriter.toByteArray();
                classes.put("net/minecraft/server/MinecraftServer.class", clazz);
            }

            for (Map.Entry<String, byte[]> e : classes.entrySet()) {
                zop.putNextEntry(new ZipEntry(e.getKey()));
                zop.write(e.getValue());
            }

            System.out.println("Adding helper");
            zop.putNextEntry(new ZipEntry("uk/co/thinkofdeath/vanillacord/util/BungeeHelper.class"));
            ByteStreams.copy(helper, zop);
        }
    }


    public static void addURL(URL u) throws IOException {
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;
        try {
            Method method = sysclass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(sysloader, new Object[]{u});
        } catch (Throwable t) {
            t.printStackTrace();
            throw new IOException("Error, could not add URL to system classloader");
        }
    }
}

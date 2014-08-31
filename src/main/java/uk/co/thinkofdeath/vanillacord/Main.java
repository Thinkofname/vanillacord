package uk.co.thinkofdeath.vanillacord;

import com.google.common.io.ByteStreams;
import com.google.common.io.Resources;
import uk.co.thinkofdeath.patchtools.Patcher;
import uk.co.thinkofdeath.patchtools.wrappers.ClassPathWrapper;
import uk.co.thinkofdeath.patchtools.wrappers.ClassSet;

import java.io.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class Main {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Args: <version>");
            return;
        }

        String version = args[0];

        String url = String.format("http://s3.amazonaws.com/Minecraft.Download/versions/%1$s/minecraft_server.%1$s.jar", version);

        File in = new File("in/" + version + ".jar");
        in.getParentFile().mkdirs();
        if (!in.exists()) {
            System.out.println("Downloading");
            try (FileOutputStream fin = new FileOutputStream(in)) {
                Resources.copy(new URL(url), fin);
            }
        }

        File out = new File("out/" + version + "-bungee.jar");
        out.getParentFile().mkdirs();
        if (out.exists()) out.delete();


        System.out.println("Loading classes");

        ClassSet classSet = new ClassSet(new ClassPathWrapper(in));
        HashMap<String, byte[]> resources = new HashMap<>();

        try (ZipFile zipFile = new ZipFile(in)) {
            zipFile.stream()
                .forEach(c -> {
                    try (InputStream cin = zipFile.getInputStream(c)) {
                        if (c.getName().endsWith(".class") && (!c.getName().contains("/") || c.getName().startsWith("net/minecraft"))) {
                            classSet.add(cin, false);
                        } else {
                            resources.put(c.getName(), ByteStreams.toByteArray(cin));
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
        }
        classSet.simplify();

        long start = System.nanoTime();
        Patcher patcher = new Patcher(classSet);
        System.out.println(patcher.apply(Main.class.getResourceAsStream("/bungee.jpatch")));
        System.out.println("Time: " + (System.nanoTime() - start));

        String[] helpers = {
            "uk/co/thinkofdeath/vanillacord/util/BungeeHelper",
            "uk/co/thinkofdeath/vanillacord/util/INetworkManager",
            "uk/co/thinkofdeath/vanillacord/util/IHandshakePacket",
        };

        try (ZipOutputStream zop = new ZipOutputStream(new FileOutputStream(out))) {
            for (String cls : classSet.classes(true)) {
                System.out.println("Saving " + cls);
                ZipEntry zipEntry = new ZipEntry(cls + ".class");
                zop.putNextEntry(zipEntry);
                zop.write(classSet.getClass(cls));
            }
            for (Map.Entry<String, byte[]> e : resources.entrySet()) {
                ZipEntry zipEntry = new ZipEntry(e.getKey());
                zop.putNextEntry(zipEntry);
                zop.write(e.getValue());
            }

            System.out.println("Adding helpers");
            for (String helper : helpers) {
                zop.putNextEntry(new ZipEntry(helper + ".class"));
                try (InputStream he = Main.class.getResourceAsStream("/" + helper + ".class")) {
                    ByteStreams.copy(he, zop);
                }
            }
        }
        System.out.println("Done");
    }
}

package xyz.cirno.notocjk.xposed;

import android.content.Context;
import android.graphics.fonts.FontStyle;
import android.graphics.fonts.FontUpdateRequest;
import android.os.Build;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.SharedMemory;
import android.os.SystemProperties;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.text.FontConfig;
import android.util.ArrayMap;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class SystemServerHook {

    private Class<?> updatableFontDirClass;
    private MethodHandle installFontFileMethodHandle;
    private Map<String, Long> _packagedFontCrc;
    private MethodHandle getPostScriptMapMethodHandle;
    private final Set<String> upToDateFonts = new HashSet<>();
    private Set<String> fontsInstalled = new HashSet<>();

    private Object fontManagerService;

    public void handleLoadSystemServer(XC_LoadPackage.LoadPackageParam lpparam) {
        disableFontSignatureVerification(lpparam.classLoader);

        XposedHelpers.findAndHookConstructor("com.android.server.graphics.fonts.FontManagerService$Lifecycle", lpparam.classLoader, Context.class, boolean.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var safemode = (boolean) param.args[1];
                if (!safemode) {
                    installUpdatableFontDirHook(lpparam);
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                fontManagerService = XposedHelpers.getObjectField(param.thisObject, "mService");
            }
        });
    }

    private void installUpdatableFontDirHook(XC_LoadPackage.LoadPackageParam lpparam) {
        updatableFontDirClass = XposedHelpers.findClass("com.android.server.graphics.fonts.UpdatableFontDir", lpparam.classLoader);
        final var getPostScriptMapMethod = XposedHelpers.findMethodExact(updatableFontDirClass, "getPostScriptMap");
        final var installFontFileMethod = XposedHelpers.findMethodExact(updatableFontDirClass, "installFontFile", FileDescriptor.class, byte[].class);

        try {
            installFontFileMethodHandle = MethodHandles.lookup().unreflect(installFontFileMethod).asType(MethodType.methodType(void.class, Object.class, FileDescriptor.class, byte[].class));
            getPostScriptMapMethodHandle = MethodHandles.lookup().unreflect(getPostScriptMapMethod).asType(MethodType.methodType(Map.class, Object.class));
            XposedHelpers.findAndHookMethod(updatableFontDirClass, "getSystemFontConfig", new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    if (checkAndUpdateFont(param.thisObject)) {
                        var fc = (FontConfig) param.getResult();
                        injectFontConfig(param.thisObject, fc);
                    }
                }
            });
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized Map<String, Long> getPackagedFontCrc() {
        if (_packagedFontCrc != null) {
            return _packagedFontCrc;
        }
        var map = new ArrayMap<String, Long>();
        try (var zipfile = XposedInit.openModuleApk()) {
            var entries = zipfile.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                var name = entry.getName();
                if (entry.getName().startsWith("assets/fonts/") && entry.getName().endsWith(".ttf")) {
                    var stem = name.substring("assets/fonts/".length(), name.length() - ".ttf".length());
                    map.put(stem, entry.getCrc());
                }
            }
            _packagedFontCrc = map;
            return map;
        } catch (IOException e) {
            XposedBridge.log(e);
            return null;
        }
    }

    private Map<String, File> getPostScriptMap(Object updatableFontDir) {
        try {
            return (Map<String, File>) getPostScriptMapMethodHandle.invokeExact(updatableFontDir);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static long getFileCrc(File f) throws IOException {
        var crc = new CRC32();
        try (var fis = new FileInputStream(f)) {
            var buf = new byte[4096];
            var len = 0;
            while ((len = fis.read(buf)) != -1) {
                crc.update(buf, 0, len);
            }
            return crc.getValue();
        }
    }

    private synchronized boolean checkAndUpdateFont(Object updatableFontDir) {
        var psMap = getPostScriptMap(updatableFontDir);
        if (psMap == null) {
            return false;
        }
        var crcs = getPackagedFontCrc();
        if (crcs == null) {
            return false;
        }
        for (var key : crcs.keySet()) {
            if (upToDateFonts.contains(key)) {
                continue;
            }
            if (!psMap.containsKey(key)) {
                XposedBridge.log("Font " + key + " not installed, scheduling install");
                scheduleFontInstall(updatableFontDir, key);
                // install fonts one-by-one
                return false;
            }
            var f = psMap.get(key);
            try {
                if (getFileCrc(f) == crcs.get(key)) {
                    XposedBridge.log("Font " + key + " is up-to-date");
                    upToDateFonts.add(key);
                } else {
                    XposedBridge.log("Font " + key + " outdated, scheduling install");
                    scheduleFontInstall(updatableFontDir, key);
                    // install fonts one-by-one
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        return true;
    }

    private void injectFontConfig(Object updatableFontDir, FontConfig fc) {
        var psMap = getPostScriptMap(updatableFontDir);
        if (psMap == null) {
            return;
        }
        var families = fc.getFontFamilies();
        int offset = 0;
        var origSize = families.size();
        var injectedLocales = new HashSet<String>();
        for (var i = 0; i < origSize; i++) {
            var family = families.get(i + offset);
            if (family.getName() != null) {
                continue;
            }
            var locales = family.getLocaleList();
            var localesStr = locales.toLanguageTags();
            if (localesStr.contains("zh-Hans") && !injectedLocales.contains("zh-Hans")) {
                families.add(i + offset, createFamilyForLocale("zh-Hans", locales, psMap));
                offset++;
                continue;
            }
            if (localesStr.contains("zh-Hant") && !injectedLocales.contains("zh-Hant")) {
                families.add(i + offset, createFamilyForLocale("zh-Hant", locales, psMap));
                offset++;
                continue;
            }
            if (localesStr.startsWith("ja") && !injectedLocales.contains("ja")) {
                families.add(i + offset, createFamilyForLocale("ja", locales, psMap));
                offset++;
                continue;
            }
            if (localesStr.startsWith("ko") && !injectedLocales.contains("ko")) {
                families.add(i + offset, createFamilyForLocale("ko", locales, psMap));
                offset++;
                continue;
            }
        }

        if (Build.VERSION.SDK_INT >= 35) {
            var localeFallbacks = fc.getLocaleFallbackCustomizations();
            offset = 0;
            origSize = localeFallbacks.size();
            for (var i = 0; i < origSize; i++) {
                var fallback = localeFallbacks.get(i + offset);
                var tag = fallback.getLocale().toLanguageTag();
                var op = fallback.getOperation();
                if (op == FontConfig.Customization.LocaleFallback.OPERATION_PREPEND || op == FontConfig.Customization.LocaleFallback.OPERATION_REPLACE) {
                    if (tag.startsWith("zh") || tag.startsWith("ja") || tag.startsWith("ko")) {
                        localeFallbacks.remove(i + offset);
                        offset--;
                    }
                }
            }
        }
    }

    private static int mapTagToTtcIndex(String tag) {
        if (tag.startsWith("ko")) {
            return 1;
        } else if (tag.startsWith("zh-Hans")) {
            return 2;
        } else if (tag.startsWith("zh-Hant")) {
            return 3;
        } else /* if (tag.startsWith("ja")) */ {
            return 0;
        }
    }

    private static FontConfig.FontFamily createFamilyForLocale(String s, LocaleList locales, Map<String, File> psMap) {
        var fonts = new ArrayList<FontConfig.Font>();
        var index = mapTagToTtcIndex(s);
        addVariableFontsToList(fonts, "NotoSansCJKjp-Thin", psMap.get("NotoSansCJKjp-Thin"), index, null);
        addVariableFontsToList(fonts, "NotoSerifCJKjp-ExtraLight", psMap.get("NotoSerifCJKjp-ExtraLight"), index, "serif");
        return FontConfigWrapper.newFontFamily(fonts, locales, FontConfig.FontFamily.VARIANT_DEFAULT);
    }

    private static void addVariableFontsToList(List<FontConfig.Font> fonts, String psName, File f, int index, String family) {
        if (Build.VERSION.SDK_INT < 35) {
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(100, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 100", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(200, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 200", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(300, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 300", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(400, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 400", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(500, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 500", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(600, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 600", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(700, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 700", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(800, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 800", family));
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(900, FontStyle.FONT_SLANT_UPRIGHT), index, "'wght' 900", family));
        } else {
            fonts.add(new FontConfig.Font(f, null, psName, new FontStyle(FontStyle.FONT_WEIGHT_NORMAL, FontStyle.FONT_SLANT_UPRIGHT), index, "", family, FontConfig.Font.VAR_TYPE_AXES_WGHT));
        }
    }

    private void installFontFile(Object updatableFontDir, FileDescriptor fd, byte[] sig) {
        try {
            installFontFileMethodHandle.invokeExact(updatableFontDir, fd, sig);
        } catch (Throwable ignore) {
        }
    }

    private void scheduleFontInstall(Object updatableFontDir, String psName) {
        if (fontsInstalled.contains(psName)) {
            return;
        }
        new Thread(() -> {
            try {
                var zipfile = XposedInit.openModuleApk();
                var entry = zipfile.getEntry("assets/fonts/" + psName + ".ttf");
                if (entry == null) {
                    return;
                }
                var stream = zipfile.getInputStream(entry);
                var memfd = SharedMemory.create(null, (int)entry.getSize()); // may be ashmem on some devices

                var memfd_buf = memfd.mapReadWrite();
                var unzip_buf = new byte[1048576];
                while (true){
                    var len = stream.read(unzip_buf);
                    if (len == -1) {
                        break;
                    }
                    memfd_buf.put(unzip_buf, 0, len);
                }
                var p = Parcel.obtain();
                memfd.writeToParcel(p, 0);
                p.setDataPosition(0);
                var real_fd = p.readFileDescriptor();
                p.recycle();

                Os.lseek(real_fd.getFileDescriptor(), 0, OsConstants.SEEK_SET);

                var sig = new byte[0];
                var req = new FontUpdateRequest(real_fd, sig);

                XposedHelpers.callMethod(fontManagerService, "update", -1, Collections.singletonList(req));
                memfd.close();

                zipfile.close();
            } catch (Throwable e) {
                XposedBridge.log(e);
            }
        }).start();
        fontsInstalled.add(psName);
    }

    private void disableFontSignatureVerification(ClassLoader classLoader) {
        var fsverityUtilImpl = XposedHelpers.findClass("com.android.server.graphics.fonts.FontManagerService$FsverityUtilImpl", classLoader);

        if (Build.VERSION.SDK_INT <= 33) {
            // Android 12-13
            XposedHelpers.findAndHookMethod(fsverityUtilImpl, "hasFsverity", String.class, XC_MethodReplacement.returnConstant(Boolean.TRUE));
            XposedHelpers.findAndHookMethod(fsverityUtilImpl, "setUpFsverity", String.class, byte[].class, XC_MethodReplacement.returnConstant(null));
        } else {
            // Android 14+
            boolean hookVerityUtils = false;
            try {
                XposedHelpers.findAndHookMethod(fsverityUtilImpl, "isFromTrustedProvider", String.class, byte[].class, XC_MethodReplacement.returnConstant(Boolean.TRUE));
            } catch (NoSuchMethodError e) {
                if (Build.VERSION.SDK_INT >= 35) {
                    XposedBridge.log("com.android.server.graphics.fonts.FontManagerService$FsverityUtilImpl#isFromTrustedProvider(String, byte[]) not found, maybe inlined by R8?");
                    hookVerityUtils = true;
                }
            }

            try {
                XposedHelpers.findAndHookMethod(fsverityUtilImpl, "setUpFsverity", String.class, XC_MethodReplacement.returnConstant(null));
            } catch (NoSuchMethodError e) {
                if (Build.VERSION.SDK_INT >= 35) {
                    XposedBridge.log("com.android.server.graphics.fonts.FontManagerService$FsverityUtilImpl#setUpFsverity(String) not found, maybe inlined by R8?");
                    hookVerityUtils = true;
                }
            }

            if (hookVerityUtils) {
                XposedBridge.log("hook VerityUtils instead");
                hookVerityUtils(classLoader);
            }
        }
    }

    private static void hookVerityUtils(ClassLoader classLoader){
        var magic = new byte[32];
        new SecureRandom().nextBytes(magic);

        var verityUtils = XposedHelpers.findClass("com.android.internal.security.VerityUtils", classLoader);

        XposedHelpers.findAndHookMethod(verityUtils, "getFsverityDigest", String.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var path = (String) param.args[0];
                if (path != null && path.startsWith("/data/fonts/files/")){
                    param.setResult(magic.clone());
                }
            }
        });

        XposedHelpers.findAndHookMethod(verityUtils, "verifyPkcs7DetachedSignature", byte[].class, byte[].class, InputStream.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                var digest = (byte[]) param.args[1];
                if (Arrays.equals(digest, magic)){
                    param.setResult(Boolean.TRUE);
                }
            }
        });
    }
}

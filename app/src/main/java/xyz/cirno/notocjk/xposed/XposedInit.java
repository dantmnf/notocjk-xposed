package xyz.cirno.notocjk.xposed;

import java.io.IOException;
import java.util.zip.ZipFile;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class XposedInit implements IXposedHookLoadPackage, IXposedHookZygoteInit {
    public static String moduleApkPath;
    @Override
    public void initZygote(StartupParam startupParam) throws Throwable {
        moduleApkPath = startupParam.modulePath;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName) && "android".equals(lpparam.processName)){
            new SystemServerHook().handleLoadSystemServer(lpparam);
        }
    }

    public static ZipFile openModuleApk() throws IOException {
        return new ZipFile(moduleApkPath);
    }
}

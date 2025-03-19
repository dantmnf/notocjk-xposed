package xyz.cirno.notocjk.xposed;

import android.os.Build;
import android.os.LocaleList;
import android.text.FontConfig;

import java.util.List;

public class FontConfigWrapper {
    public static FontConfig.FontFamily newFontFamily(List<FontConfig.Font> fonts, LocaleList localeList, int variant) {
        if (Build.VERSION.SDK_INT <= 33) {
            return new FontConfig.FontFamily(fonts, null, localeList, variant);
        }
        return new FontConfig.FontFamily(fonts, localeList, variant);
    }
}

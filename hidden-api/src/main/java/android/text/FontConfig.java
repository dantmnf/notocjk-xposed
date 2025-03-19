package android.text;

import android.graphics.fonts.FontStyle;
import android.os.LocaleList;
import android.os.Parcel;
import android.os.Parcelable;

import java.io.File;
import java.util.List;
import java.util.Locale;

public final class FontConfig implements Parcelable {
    public static final Creator<FontConfig> CREATOR = null;

    @Override
    public int describeContents() {
        throw new RuntimeException("Stub!");
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the ordered list of font families available in the system.
     *
     * @return a list of font families.
     * @see FontFamily
     */
    public List<FontFamily> getFontFamilies() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns the list of aliases for mapping font families with other names.
     *
     * @return a list of font families.
     * @see Alias
     */
    public List<Alias> getAliases() {
        throw new RuntimeException("Stub!");
    }

    public List<NamedFamilyList> getNamedFamilyLists() {
        throw new RuntimeException("Stub!");
    }

    /**
     * Returns a locale fallback customizations.
     * <p>
     * This field is used for creating the system fallback in the system server. This field is
     * always empty in the application process.
     *
     * @hide
     */
    public List<Customization.LocaleFallback> getLocaleFallbackCustomizations() {
        throw new RuntimeException("Stub!");
    }

    public static final class Font {
        public Font(File file, File originalFile, String postScriptName,
                    FontStyle style, int index,
                    String fontVariationSettings, String fontFamilyName,
                    int varTypeAxes) {
            throw new RuntimeException("Stub!");
        }

        public Font(File file, File originalFile, String postScriptName,
                    FontStyle style, int index,
                    String fontVariationSettings, String fontFamilyName) {
            throw new RuntimeException("Stub!");
        }

        /** @hide */
        public static final int VAR_TYPE_AXES_NONE = 0;
        /** @hide */
        public static final int VAR_TYPE_AXES_WGHT = 1;
        /** @hide */
        public static final int VAR_TYPE_AXES_ITAL = 2;

        /**
         * Returns the font file.
         */
        public File getFile() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the original font file in the system directory.
         * <p>
         * If the font file is not updated, returns null.
         *
         * @return returns the original font file in the system if the font file is updated. Returns
         * null if the font file is not updated.
         * @hide
         */
        public File getOriginalFile() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the font style.
         */
        public FontStyle getStyle() {
            throw new RuntimeException("Stub!");
        }


        /**
         * Return a font variation settings.
         */
        public String getFontVariationSettings() {
            throw new RuntimeException("Stub!");
        }

        /**
         * A {@link Font} can be configured to be in the {@code Fallback List} for a
         * {@link FontFamily}.
         * <p>
         * For example a serif Hebrew [Font] can be defined in the {@code Fallback List} for
         * {@code "serif"} {@link FontFamily}.
         * <p>
         * If the return value is not {@code null}, then the font will be used in the
         * {@code Fallback List} of that {@link FontFamily}.
         * <p>
         * If the return value is {@code null}, then the font will be used in {@code Fallback List}
         * of all {@link FontFamily}s.
         */
        public String getFontFamilyName() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the index to be used to access this font when accessing a TTC file.
         */
        public int getTtcIndex() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the PostScript name of this font.
         */
        public String getPostScriptName() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the list of supported axes tags for variable family type resolution.
         *
         * @hide
         */
        public int getVarTypeAxes() {
            throw new RuntimeException("Stub!");
        }

    }

    public static final class Alias {
    }

    public static final class FontFamily {

        public FontFamily(List<Font> fonts, String name, LocaleList localeList, int variant) {
            throw new RuntimeException("Stub!");
        }

        public FontFamily(List<Font> fonts, LocaleList localeList, int variant) {
            throw new RuntimeException("Stub!");
        }


        /**
         * Value for font variant.
         * <p>
         * Indicates the font has no variant attribute.
         */
        public static final int VARIANT_DEFAULT = 0;

        /**
         * Value for font variant.
         * <p>
         * Indicates the font is for compact variant.
         *
         * @see android.graphics.Paint#setElegantTextHeight
         */
        public static final int VARIANT_COMPACT = 1;

        /**
         * Value for font variant.
         * <p>
         * Indicates the font is for elegant variant.
         *
         * @see android.graphics.Paint#setElegantTextHeight
         */
        public static final int VARIANT_ELEGANT = 2;

        /**
         * Returns the list of {@link Font}s in this {@link FontFamily}.
         *
         * @return a list of font files.
         */
        public List<Font> getFontList() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the name of the {@link FontFamily}.
         * <p>
         * When the name of a {@link FontFamily} is not null, this name is used to create a new
         * {@code Fallback List}, and that {@code Fallback List}. Fallback List is the
         * main building block for a {@link android.graphics.Typeface}.
         * <p>
         * For example, if the {@link FontFamily} has the name "serif", then the system will create
         * a “serif” {@code Fallback List} and it can be used by creating a Typeface via
         * {@code Typeface.create("serif", Typeface.NORMAL);}
         * <p>
         * When the name of a {@link FontFamily} is null, it will be appended to all of the
         * {@code Fallback List}s.
         *
         * @deprecated From API 34, this function always returns null. All font families which have
         * name attribute will be reported as a {@link NamedFamilyList}.
         */
        @Deprecated
        public String getName() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the locale list if available.
         * <p>
         * The locale list will be used for deciding which font family should be used in fallback
         * list.
         */
        public LocaleList getLocaleList() {
            throw new RuntimeException("Stub!");
        }

        /**
         * Returns the text height variant.
         */
        public int getVariant() {
            throw new RuntimeException("Stub!");
        }

    }


    public static final class NamedFamilyList {
    }

    public static class Customization {
        public static final class LocaleFallback {
            public static final int OPERATION_PREPEND = 0;
            public static final int OPERATION_APPEND = 1;
            public static final int OPERATION_REPLACE = 2;
            public Locale getLocale() {
                throw new RuntimeException("Stub!");
            }
            public int getOperation() {
                throw new RuntimeException("Stub!");
            }
        }
    }
}

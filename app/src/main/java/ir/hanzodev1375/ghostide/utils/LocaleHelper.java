package ir.hanzodev1375.ghostide.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;
import android.os.LocaleList;
import ir.hanzodev1375.ghostide.R;
import java.util.Locale;

public class LocaleHelper {

  private static final String PREF_NAME = "locale_pref";
  private static final String PREF_KEY = "selected_language";

  /**
   * Language codes offered in the language dialog. Every code except {@code "default"} must have a
   * matching {@code values-XX} resource folder, otherwise choosing it silently shows English.
   */
  public static final String[] LANGUAGE_CODES = {
    "default", "en", "fa", "ar", "hi", "pt", "ru", "zh"
  };

  private static final String[] NATIVE_LANGUAGE_NAMES = {
    null, "English", "فارسی", "العربية", "हिन्दी", "Português", "Русский", "中文"
  };

  /**
   * Display names for {@link #LANGUAGE_CODES}, in the same order. Languages are shown in their own
   * script; only the first entry (follow the system) is localized.
   */
  public static String[] languageNames(Context context) {
    String[] names = NATIVE_LANGUAGE_NAMES.clone();
    names[0] = context.getString(R.string.language_system_default);
    return names;
  }

  /**
   * Call from attachBaseContext of every Activity:
   * {@code super.attachBaseContext(LocaleHelper.applyLocale(newBase))}. A saved code that is no
   * longer offered (for example one without translations) is treated as the system default.
   */
  public static Context applyLocale(Context context) {
    String code = getSavedLanguage(context);
    return isOffered(code) ? localized(context, code) : context;
  }

  public static Context setLocale(Context context, String langCode) {
    saveLanguage(context, langCode);
    return localized(context, langCode);
  }

  private static boolean isOffered(String langCode) {
    for (String code : LANGUAGE_CODES) {
      if (code.equals(langCode)) return true;
    }
    return false;
  }

  private static Context localized(Context context, String langCode) {
    if ("default".equals(langCode)) return context;

    Locale locale = new Locale(langCode);
    Locale.setDefault(locale);
    Configuration config = new Configuration(context.getResources().getConfiguration());
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
      config.setLocales(new LocaleList(locale));
    } else {
      config.setLocale(locale);
    }
    return context.createConfigurationContext(config);
  }

  public static void saveLanguage(Context context, String langCode) {
    context
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .edit()
        .putString(PREF_KEY, langCode)
        .apply();
  }

  public static String getSavedLanguage(Context context) {
    return context
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        .getString(PREF_KEY, "default");
  }

  public static int getSavedLanguageIndex(Context context) {
    String saved = getSavedLanguage(context);
    for (int i = 0; i < LANGUAGE_CODES.length; i++) {
      if (LANGUAGE_CODES[i].equals(saved)) return i;
    }
    return 0;
  }
}

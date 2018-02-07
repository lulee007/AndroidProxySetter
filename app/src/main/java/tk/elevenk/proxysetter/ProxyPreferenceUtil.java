package tk.elevenk.proxysetter;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.Map;

/**
 * Created by luxiaohui on 2018/2/2.
 */

public class ProxyPreferenceUtil {

    private final static String PREFERENCE_PROXY_KEY = "PREFERENCE_PROXY_KEY";
    private final static String PROFILES_KEY = "PROFILES_KEY";
    private final static String CURRENT_PROFILE_KEY = "CURRENT_PROFILE_KEY";
    private static ProxyPreferenceUtil _util;

    private ProxyPreferenceUtil() {
    }

    public static ProxyPreferenceUtil getInstance() {
        if (_util == null) {
            _util = new ProxyPreferenceUtil();
        }
        return _util;
    }

    private Map<String, ProxyProfile> cachedProfiles = null;


    public Map<String, ProxyProfile> getProfiles(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        String jsonString = sharedPref.getString(PROFILES_KEY, "{}");
        Map<String, ProxyProfile> profiles = new Gson().fromJson(jsonString, new TypeToken<Map<String, ProxyProfile>>() {
        }.getType());
        cachedProfiles = profiles;
        return profiles;
    }

    public Map<String, ProxyProfile> saveProfile(Context context, ProxyProfile proxyProfile) {
        while (cachedProfiles.containsKey(proxyProfile.getProfileName())){
            proxyProfile.setProfileName(proxyProfile.getProfileName()+"-1");
        }
        cachedProfiles.put(proxyProfile.getProfileName(), proxyProfile);
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PROFILES_KEY, new Gson().toJson(cachedProfiles));
        editor.commit();
        return cachedProfiles;
    }

    public Map<String, ProxyProfile> deleteProfile(Context context, String profileName) {
        cachedProfiles.remove(profileName);
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PROFILES_KEY, new Gson().toJson(cachedProfiles));
        editor.putString(CURRENT_PROFILE_KEY,"无");
        editor.commit();
        return cachedProfiles;
    }


    public void saveCurrentProfile(Context context, String profileName) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(CURRENT_PROFILE_KEY, profileName);
        editor.commit();
    }

    public String getCurrentProfile(Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        return sharedPref.getString(CURRENT_PROFILE_KEY, "无");
    }

    public Map<String, ProxyProfile> renameProfile(Context context, String oldName, String newName) {
        ProxyProfile profile = cachedProfiles.remove(oldName);
        profile.setProfileName(newName);
        while (cachedProfiles.containsKey(profile.getProfileName())){
            profile.setProfileName(profile.getProfileName()+"-1");
        }
        cachedProfiles.put(profile.getProfileName(), profile);
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCE_PROXY_KEY, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(PROFILES_KEY, new Gson().toJson(cachedProfiles));
        editor.putString(CURRENT_PROFILE_KEY, profile.getProfileName());
        editor.commit();
        return cachedProfiles;
    }
}

package tk.elevenk.proxysetter;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.SwitchPreference;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.orhanobut.logger.Logger;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Function;
import io.reactivex.functions.Predicate;
import io.reactivex.schedulers.Schedulers;
import tk.elevenk.proxysetter.proxy.ProxyChangeAsync;
import tk.elevenk.proxysetter.rxbus.RxBus;
import tk.elevenk.proxysetter.rxbus.RxBusEvent;

import static android.content.Context.MODE_WORLD_READABLE;
import static tk.elevenk.proxysetter.proxy.ProxyChangeParams.CLEAR;
import static tk.elevenk.proxysetter.proxy.ProxyChangeParams.HOST;
import static tk.elevenk.proxysetter.proxy.ProxyChangeParams.KEY;
import static tk.elevenk.proxysetter.proxy.ProxyChangeParams.PORT;
import static tk.elevenk.proxysetter.proxy.ProxyChangeParams.SSID;


/**
 * A simple {@link Fragment} subclass.
 * Activities that contain this fragment must implement the
 * Use the {@link ProxySetterFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class ProxySetterFragment extends PreferenceFragment implements Preference.OnPreferenceClickListener, Preference.OnPreferenceChangeListener {

    private static final String defaultSSID = "请先打开WiFi";

    private final String PROXY_SWITCH = "proxy_switch";
    private final String PROXY_PROFILES = "proxy_profiles";
    private final String PROXY_HOST = "proxy_host";
    private final String PROXY_PORT = "proxy_port";
    private final String PROXY_WIFI_NAME = "proxy_wifi_name";
    private final String PROXY_WIFI_PWD = "proxy_wifi_pwd";

    private ProxyProfile currentProfile = new ProxyProfile();

    private Map<String, ProxyProfile> profiles;
    ListPreference proxyProfiles;
    Preference proxyHost;
    Preference proxyPort;
    ListPreference proxyWifiName;
    Preference proxyWifiPwd;
    SwitchPreference proxySwitch;

    private CompositeDisposable disposableBag;


    public ProxySetterFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @return A new instance of fragment ProxySetterFragment.
     */
    public static ProxySetterFragment newInstance() {
        ProxySetterFragment fragment = new ProxySetterFragment();
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getPreferenceManager().setSharedPreferencesMode(MODE_WORLD_READABLE);
        addPreferencesFromResource(R.xml.pref_general);

        loadData();

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_proxy_setter, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        configViews();
        bindEvents();
        loadProfiles();
        loadWiFiSSIDs();

        proxySwitchToggled(proxySwitch, ((SwitchPreference) proxySwitch).isChecked());
    }

    private void loadData() {
        profiles = ProxyPreferenceUtil.getInstance().getProfiles(getActivity());
        String currentProfileName = ProxyPreferenceUtil.getInstance().getCurrentProfile(getActivity());
        currentProfile = profiles.get(currentProfileName);

    }

    private void configViews() {
        proxySwitch = (SwitchPreference) findPreference(PROXY_SWITCH);
        proxyHost = findPreference(PROXY_HOST);
        proxyPort = findPreference(PROXY_PORT);
        proxyWifiName = (ListPreference) findPreference(PROXY_WIFI_NAME);
        proxyWifiPwd = findPreference(PROXY_WIFI_PWD);
        proxyProfiles = (ListPreference) findPreference(PROXY_PROFILES);
        if (currentProfile != null) {
            proxyHost.setSummary(currentProfile.getHostName());
            proxyPort.setSummary(currentProfile.getHostPort() + "");
            proxyWifiName.setSummary(currentProfile.getWifiName());
            proxyWifiName.setValue(currentProfile.getWifiName());
            proxyWifiPwd.setSummary("******");
            proxyProfiles.setSummary(currentProfile.getProfileName());
            proxyProfiles.setValue(currentProfile.getProfileName());
        }

    }

    private void bindEvents() {
        proxySwitch.setOnPreferenceChangeListener(this);
        proxyProfiles.setOnPreferenceChangeListener(this);
        proxyWifiName.setOnPreferenceChangeListener(this);

        proxyHost.setOnPreferenceClickListener(this);
        proxyPort.setOnPreferenceClickListener(this);
        proxyWifiPwd.setOnPreferenceClickListener(this);

        disposableBag = new CompositeDisposable();

        RxBus.get().toObservable(RxBusEvent.class)
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .filter(new Predicate<RxBusEvent>() {
                    @Override
                    public boolean test(RxBusEvent rxBusEvent) throws Exception {
                        return rxBusEvent.getType() == RxBusEvent.TYPE_PROXY_DELETE ||
                                rxBusEvent.getType() == RxBusEvent.TYPE_PROXY_RENAME;
                    }
                })
                .subscribe(new Consumer<RxBusEvent>() {
                    @Override
                    public void accept(RxBusEvent rxBusEvent) throws Exception {
                        switch (rxBusEvent.getType()) {
                            case RxBusEvent.TYPE_PROXY_DELETE:
                                deleteCurrentProfile();
                                break;
                            case RxBusEvent.TYPE_PROXY_RENAME:
                                renameCurrentProfile();
                                break;
                        }
                    }
                });

        RxBus.get().toObservable(RxBusEvent.class)
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .filter(new Predicate<RxBusEvent>() {
                    @Override
                    public boolean test(RxBusEvent rxBusEvent) throws Exception {
                        return rxBusEvent.getType() == RxBusEvent.TYPE_PROXY_LOAD_WIFI;
                    }
                })
                .subscribe(new Consumer<RxBusEvent>() {
                    @Override
                    public void accept(RxBusEvent rxBusEvent) throws Exception {
                        loadWiFiSSIDs();
                    }
                });
    }


    private void renameCurrentProfile() {
        if (currentProfile == null) {
            Toast.makeText(getActivity(), "当前未选中任何配置文件", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialDialog.Builder(getActivity())
                .title("请输入新的名称")
                .input("", currentProfile.getProfileName(), false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        renameProfile(input.toString());
                    }
                })
                .autoDismiss(true)
                .show();
    }

    private void renameProfile(String newName) {
        String oldName = currentProfile.getProfileName();
        currentProfile.setProfileName(newName);
        profiles = ProxyPreferenceUtil.getInstance().renameProfile(getActivity(), oldName, newName);
        loadProfiles();
    }

    private void deleteCurrentProfile() {
        if (currentProfile == null) {
            Toast.makeText(getActivity(), "当前未选中任何配置文件", Toast.LENGTH_SHORT).show();
            return;
        }
        new MaterialDialog.Builder(getActivity())
                .title("删除配置文件")
                .content("确定要删除当前配置文件:【" + currentProfile.getProfileName() + "】？")
                .negativeText("取消")
                .positiveText("删除")
                .onPositive(new MaterialDialog.SingleButtonCallback() {
                    @Override
                    public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                        profiles = ProxyPreferenceUtil.getInstance().deleteProfile(getActivity(), currentProfile.getProfileName());
                        loadProfiles();
                        proxyProfiles.setValue("");
                        proxyProfiles.setSummary("无");
                        currentProfile = null;

                    }
                })
                .show();
    }


    private void loadProfiles() {
        proxyProfiles.setEntries(getProfileNames());
        proxyProfiles.setEntryValues(getProfileValues());
        String currentProfileName = ProxyPreferenceUtil.getInstance().getCurrentProfile(getActivity());
        proxyProfiles.setValue(currentProfileName);
        proxyProfiles.setSummary(currentProfileName);

    }

    private void loadWiFiSSIDs() {

        WifiManager wifiManager = (WifiManager) getActivity().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        List<ScanResult> results = wifiManager.getScanResults();

        Logger.d(results);
        Observable.fromIterable(results)
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .subscribeOn(Schedulers.io())
                .sorted(new Comparator<ScanResult>() {
                    @Override
                    public int compare(ScanResult o1, ScanResult o2) {
                        return o1.SSID.compareTo(o2.SSID);
                    }
                })
                .map(new Function<ScanResult, String>() {
                    @Override
                    public String apply(ScanResult scanResult) throws Exception {
                        return scanResult.SSID;
                    }
                })
                .defaultIfEmpty(defaultSSID)
                .toList()
                .onErrorReturnItem(Collections.singletonList(defaultSSID))

                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<List<String>>() {
                    @Override
                    public void accept(List<String> strings) throws Exception {
                        proxyWifiName.setEntryValues(strings.toArray(new String[]{}));
                        proxyWifiName.setEntries(strings.toArray(new String[]{}));
                        if(currentProfile.getWifiName()!= null && !currentProfile.getWifiName().isEmpty()) {
                            proxyWifiName.setValue(currentProfile.getWifiName());
                        }
                    }
                });
    }


    private String[] getProfileNames() {
        List<String> names = new ArrayList<>();

        if (profiles != null) {

            names.addAll(profiles.keySet());
        }
        names.add("新建");
        return names.toArray(new String[]{});
    }

    private String[] getProfileValues() {
        List<String> values = new ArrayList<>();
        int count = 0;

        if (profiles != null) {
            for (String key : profiles.keySet()) {
                if (key.startsWith("新建")) {
                    count++;
                }
            }
            values.addAll(profiles.keySet());
        }
        values.add(-count + "");
        return values.toArray(new String[]{});
    }

    @Override
    public void onDetach() {
        super.onDetach();
        disposableBag.dispose();
        disposableBag = new CompositeDisposable();
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        switch (preference.getKey()) {
            case PROXY_HOST:
                showSetProxyHostDialog(preference);
                break;
            case PROXY_PORT:
                showSetProxyPortDialog(preference);
                break;
            case PROXY_WIFI_PWD:
                showSetWifiPwdDialog(preference);
                break;
        }
        return true;
    }

    private void showSetWifiPwdDialog(final Preference preference) {
        new MaterialDialog.Builder(getActivity())
                .inputType(InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD)
                .input("请输入 WiFi 密码", "", false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        preference.setSummary("******");
                        currentProfile.setWifiPwd(input.toString());
                        saveCurrentProfile();
                    }
                })
                .title(preference.getTitle())
                .autoDismiss(true)
                .show();
    }

    private void showSetProxyPortDialog(final Preference preference) {
        new MaterialDialog.Builder(getActivity())
                .inputType(InputType.TYPE_CLASS_NUMBER)
                .input("请输入代理服务器的端口号", preference.getSummary(), false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        preference.setSummary(input);
                        currentProfile.setHostPort(Integer.parseInt(input.toString()));
                        saveCurrentProfile();
                    }
                })
                .title(preference.getTitle())
                .autoDismiss(true)
                .show();
    }

    private void showSetProxyHostDialog(final Preference preference) {
        String perfill = "";
        if (preference.getSummary() != null) {
            perfill = preference.getSummary().toString().equalsIgnoreCase("代理服务器的IP地址") ? "" : preference.getSummary().toString();
        }
        new MaterialDialog.Builder(getActivity())
                .inputType(InputType.TYPE_TEXT_VARIATION_URI)
                .input("请输入代理服务器的IP地址", perfill, false, new MaterialDialog.InputCallback() {
                    @Override
                    public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                        preference.setSummary(input);
                        currentProfile.setHostName(input.toString());
                        saveCurrentProfile();
                    }
                })
                .title(preference.getTitle())
                .autoDismiss(true)
                .show();

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        switch (preference.getKey()) {
            case PROXY_SWITCH:
                proxySwitchToggled(preference, newValue);
                break;
            case PROXY_PROFILES:
                selectProxyProfile((ListPreference) preference, newValue);
                break;
            case PROXY_WIFI_NAME:
                selectWiFiSSID((ListPreference) preference, newValue);
        }
        return true;
    }

    private void selectWiFiSSID(ListPreference preference, Object newValue) {
        String ssid = newValue.toString();
        preference.setSummary(ssid);
        if (ssid.equalsIgnoreCase(defaultSSID)) {
            return;
        } else {
            currentProfile.setWifiName(ssid);
            saveCurrentProfile();
        }

    }

    private void proxySwitchToggled(final Preference preference, final Object newValue) {
        Logger.d("开关状态变化了" + newValue);
        if (!proxySwitch.isEnabled()) {
            Logger.d("正在处理上一次的状态，取消本次处理");
            return;
        }

        proxySwitch.setEnabled(false);
        Logger.d("设置开关为不可点击");
        final Boolean enable = !(Boolean) newValue;
        Boolean checked = false;
        if (currentProfile != null) {
            proxyProfiles.setEnabled(enable);
            proxyHost.setEnabled(enable);
            proxyPort.setEnabled(enable);
            proxyWifiName.setEnabled(enable);
            proxyWifiPwd.setEnabled(enable);
            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_PROXY_ENABLE, enable));
            if (!enable && !proxySwitch.isChecked() ||
                    (enable && proxySwitch.isChecked())) {
                setProxy(!enable);
            }
            if (!enable) {
                checked = true;
            }
        } else {
            checked = false;
            Toast.makeText(getActivity(), "当前配置文件为空", Toast.LENGTH_SHORT).show();
        }
        final Boolean finalChecked = checked;
        Observable.just("")
                .subscribeOn(Schedulers.newThread())
                .delay(1, TimeUnit.SECONDS)
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .observeOn(AndroidSchedulers.mainThread())
                .doOnNext(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        if ((Boolean) newValue && !finalChecked) {
                            Logger.d("还原设置开关为关闭");
                            proxySwitch.setChecked(false);
                        }
                    }
                })
                .observeOn(Schedulers.newThread())
                .delay(1, TimeUnit.SECONDS)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<String>() {
                    @Override
                    public void accept(String s) throws Exception {
                        Logger.d("设置开关为可点击");
                        proxySwitch.setEnabled(true);
                    }
                });

    }

    private void selectProxyProfile(ListPreference preference, Object newValue) {
        ProxyProfile profile = profiles.get(newValue.toString());
        if (profile != null) {
            preference.setSummary(profile.getProfileName());
            currentProfile = profile;
        } else {
            // 点击了新建，新建一个配置
            Integer value = Integer.parseInt(newValue.toString());
            if (value < 1) {
                currentProfile = new ProxyProfile();
                currentProfile.setProfileName("新建" + Math.abs(value));
                profiles = ProxyPreferenceUtil.getInstance().saveProfile(getActivity(), currentProfile);
                loadProfiles();
                preference.setSummary(currentProfile.getProfileName());
                // 异步在主线程中 延迟设置选中当前新建的配置文件
                Observable.just(currentProfile.getProfileName())
                        .doOnSubscribe(new Consumer<Disposable>() {
                            @Override
                            public void accept(Disposable disposable) throws Exception {
                                disposableBag.add(disposable);
                            }
                        })
                        .delay(200, TimeUnit.MILLISECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Consumer<String>() {
                            @Override
                            public void accept(String s) throws Exception {
                                proxyProfiles.setValue(s);
                            }
                        });

            }
        }
        ProxyPreferenceUtil.getInstance().saveCurrentProfile(getActivity(), currentProfile.getProfileName());

    }

    private void saveCurrentProfile(){
        ProxyPreferenceUtil.getInstance().saveCurrentProfile(getActivity(),currentProfile);
    }

    private void setProxy(Boolean enable) {
        Intent intent = new Intent();
        intent.putExtra(SSID, currentProfile.getWifiName());
        intent.putExtra(KEY, currentProfile.getWifiPwd());
//        intent.putExtra(RESET_WIFI,"true");
        if (!enable) {
            //关闭代理
            intent.putExtra(CLEAR, "true");
        } else {
            intent.putExtra(HOST, currentProfile.getHostName());
            intent.putExtra(PORT, String.valueOf(currentProfile.getHostPort()));
        }
        if (validateIntent(intent)) {
            new ProxyChangeAsync(getActivity()).execute(intent);
        }
    }

    private boolean validateIntent(Intent intent) {
        if (!intent.hasExtra(HOST) && !intent.hasExtra(CLEAR)) {
            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_TOAST_MAIN, "Error: No HOST given or not clearing proxy"));
            return false;
        }
        if (!intent.hasExtra(SSID)) {
            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_TOAST_MAIN, "Warning: No SSID given, setting on the fist one available"));
            return false;
        }
        return true;
    }

}

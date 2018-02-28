/*
 * Copyright (c) 2016 John Paul Krause.
 * MainActivity.java is part of AndroidProxySetter.
 *
 * AndroidProxySetter is free software: you can redistribute it and/or modify
 * iit under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * AndroidProxySetter is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AndroidProxySetter.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package tk.elevenk.proxysetter;

import android.Manifest;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.design.widget.AppBarLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.pgyersdk.update.PgyUpdateManager;
import com.tbruyelle.rxpermissions2.RxPermissions;

import butterknife.BindView;
import butterknife.ButterKnife;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.functions.Consumer;
import io.reactivex.functions.Predicate;
import tk.elevenk.proxysetter.rxbus.RxBus;
import tk.elevenk.proxysetter.rxbus.RxBusEvent;


public class MainActivity extends AppCompatActivity {
    private static final String TAG = "ProxySetterApp";


    @BindView(R.id.toolbar)
    Toolbar toolbar;
    @BindView(R.id.app_bar)
    AppBarLayout appBar;
    @BindView(R.id.activity_main)
    RelativeLayout activityMain;

    private CompositeDisposable disposableBag;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        disposableBag = new CompositeDisposable();

        configViews();
        bindEvents();
        PgyUpdateManager.register(this, "proxysetter");

    }



    private void configViews() {
        setSupportActionBar(toolbar);

        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.fragment_main, ProxySetterFragment.newInstance()).commit();
        new RxPermissions(this)
                .request(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                )
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .subscribe(new Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        if (!aBoolean) {
                            Toast.makeText(MainActivity.this, "需要该读取Wifi列表", Toast.LENGTH_SHORT).show();
                            finish();
                        }else{
                            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_PROXY_LOAD_WIFI,""));
                        }
                    }
                });
    }

    private void bindEvents() {
        RxBus.get().toObservable(RxBusEvent.class)
                .filter(new Predicate<RxBusEvent>() {
                    @Override
                    public boolean test(RxBusEvent rxBusEvent) throws Exception {
                        return rxBusEvent.getType() == RxBusEvent.TYPE_PROXY_ENABLE;
                    }
                })
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<RxBusEvent>() {
                    @Override
                    public void accept(RxBusEvent rxBusEvent) throws Exception {
                        Boolean enable = rxBusEvent.getValue();
                        toolbar.getMenu().getItem(0).setEnabled(enable);
                        toolbar.getMenu().getItem(1).setEnabled(enable);
                    }
                });

        RxBus.get().toObservable(RxBusEvent.class)
                .filter(new Predicate<RxBusEvent>() {
                    @Override
                    public boolean test(RxBusEvent rxBusEvent) throws Exception {
                        return rxBusEvent.getType() == RxBusEvent.TYPE_TOAST_MAIN;
                    }
                })
                .doOnSubscribe(new Consumer<Disposable>() {
                    @Override
                    public void accept(Disposable disposable) throws Exception {
                        disposableBag.add(disposable);
                    }
                })
                .subscribeOn(AndroidSchedulers.mainThread())
                .subscribe(new Consumer<RxBusEvent>() {
                    @Override
                    public void accept(RxBusEvent rxBusEvent) throws Exception {
                        String msg = rxBusEvent.getValue();
                        showPopup(msg);
                    }
                });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        //noinspection SimplifiableIfStatement
        if (id == R.id.action_rename) {
            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_PROXY_RENAME, ""));
            return true;
        } else if (id == R.id.action_delete) {
            RxBus.get().post(new RxBusEvent(RxBusEvent.TYPE_PROXY_DELETE, ""));
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        disposableBag.dispose();
    }


    /**
     * Shows a toast and logs to logcat
     *
     * @param msg Message to show/log
     */
    private void showPopup(final String msg) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show();
                Log.d(TAG, msg);
            }
        });
    }
}

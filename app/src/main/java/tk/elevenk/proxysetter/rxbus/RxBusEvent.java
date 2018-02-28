package tk.elevenk.proxysetter.rxbus;

import com.orhanobut.logger.Logger;

/**
 * Created by luxiaohui on 2017/11/2.
 */

public class RxBusEvent {
    public static final int TYPE_PROXY_RENAME = 0x100;
    public static final int TYPE_PROXY_DELETE = 0x101;
    public static final int TYPE_PROXY_ENABLE = 0x102;
    public static final int TYPE_PROXY_LOAD_WIFI = 0x103;
    public static final int TYPE_TOAST_MAIN = 0x200;

    private int type;
    private Object value;

    public <T> T getValue() {
        try {
            return (T) value;
        } catch (Exception e) {
            Logger.e(e, "RxBusEvent");
        }
        return null;
    }

    public int getType() {
        return type;
    }

    public RxBusEvent(int type, Object value) {
        this.type = type;
        this.value = value;
    }
}

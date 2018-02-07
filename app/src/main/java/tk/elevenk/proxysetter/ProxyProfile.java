package tk.elevenk.proxysetter;

/**
 * Created by luxiaohui on 2018/2/2.
 */

public class ProxyProfile {
    private String profileName;
    private String hostName;
    private int hostPort;
    private String wifiName;
    private String wifiPwd;

    public String getProfileName() {
        return profileName;
    }

    public void setProfileName(String profileName) {
        this.profileName = profileName;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public int getHostPort() {
        return hostPort;
    }

    public void setHostPort(int hostPort) {
        this.hostPort = hostPort;
    }

    public String getWifiName() {
        return wifiName;
    }

    public void setWifiName(String wifiName) {
        this.wifiName = wifiName;
    }

    public String getWifiPwd() {
        return wifiPwd;
    }

    public void setWifiPwd(String wifiPwd) {
        this.wifiPwd = wifiPwd;
    }


    public ProxyProfile() {
        this.hostPort = 8888;
    }
}

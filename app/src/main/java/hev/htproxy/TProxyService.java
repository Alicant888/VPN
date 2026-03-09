package hev.htproxy;

public class TProxyService {
    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    public native void TProxyStartService(String configPath, int fd);

    public native void TProxyStopService();

    public native long[] TProxyGetStats();

    public native boolean TProxyIsRunning();

    public native int TProxyGetLastResult();
}
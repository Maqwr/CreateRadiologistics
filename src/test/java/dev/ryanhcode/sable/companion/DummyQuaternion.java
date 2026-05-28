package dev.ryanhcode.sable.companion;

public class DummyQuaternion {
    public double x;
    public double y;
    public double z;
    public double w;

    public DummyQuaternion(double x, double y, double z, double w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }
    
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double w() { return w; }
}

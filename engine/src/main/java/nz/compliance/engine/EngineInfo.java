package nz.compliance.engine;

/** Build/identity info for the pure compliance engine module. */
public final class EngineInfo {

    public static final String NAME = "compliance-engine";
    public static final String VERSION = "0.1.0";

    private EngineInfo() {
    }

    public static String describe() {
        return NAME + " " + VERSION;
    }
}

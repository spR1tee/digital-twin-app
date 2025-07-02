package hu.digital_twin.context;

/**
 * TenantContext osztály a multi-tenant alkalmazásokhoz.
 * Egy ThreadLocal változót használ, hogy minden szálnak külön bérlő-azonosítója lehessen.
 */
public class TenantContext {
    // ThreadLocal változó, amely minden szálon elkülönítve tárolja az aktuális tenant azonosítót
    private static final ThreadLocal<String> currentTenant = new ThreadLocal<>();

    /**
     * Beállítja az aktuális szál tenant azonosítóját.
     * @param tenantId a bérlő azonosítója
     */
    public static void setTenantId(String tenantId) {
        currentTenant.set(tenantId);
    }

    /**
     * Lekéri az aktuális szál tenant azonosítóját.
     * @return tenant azonosító vagy null, ha nincs beállítva
     */
    public static String getTenantId() {
        return currentTenant.get();
    }

    /**
     * Kitörli az aktuális szál tenant információját,
     * hogy elkerülje a memória szivárgást és a hibás tenant-összekeveredést.
     */
    public static void clear() {
        currentTenant.remove();
    }
}


package hu.digital_twin.config;

import hu.digital_twin.context.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

// Az interceptor egy Spring-komponens, amely minden HTTP kérés előtt és után meghívódik
@Component
public class TenantInterceptor implements HandlerInterceptor {
    // Ez a metódus a controller meghívása előtt fut le
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Kinyeri az 'X-Tenant-ID' HTTP fejlécet, amely az aktuális tenant azonosítót tartalmazza
        String tenantId = request.getHeader("X-Tenant-ID");

        // Beállítja a tenant ID-t a ThreadLocal alapú TenantContext-be
        // Ha nincs megadva fejléc, akkor "default" tenant-ot használ
        TenantContext.setTenantId(tenantId != null ? tenantId : "default");
        return true;
    }

    // Ez a metódus akkor hívódik meg, amikor a kérés feldolgozása befejeződik
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // Törli a tenant ID-t a ThreadLocal tárolóból, hogy ne szivárogjon át másik kérésre
        TenantContext.clear();
    }
}

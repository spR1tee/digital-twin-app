package hu.digital_twin.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    // A TenantInterceptor automatikus bekötése (a Spring kezelje a példányt)
    @Autowired
    private TenantInterceptor tenantInterceptor;

    // Itt történik az interceptor regisztrálása a Spring Web MVC feldolgozási láncába
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Hozzáadja a tenantInterceptor-t, amely minden beérkező HTTP kérés előtt lefut
        registry.addInterceptor(tenantInterceptor);
    }
}

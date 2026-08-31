package kz.afm.kendala.stage1;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import kz.afm.kendala.config.WebConfig;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class WebConfigTest {

    @Test
    void originsAreTrimmedOnConstruction() {
        WebConfig config = new WebConfig("http://localhost:5173 , http://localhost:3000 ");
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(mockRequest("http://localhost:5173"));
        assertThat(cors).isNotNull();
        List<String> origins = cors.getAllowedOrigins();
        assertThat(origins).containsExactlyInAnyOrder("http://localhost:5173", "http://localhost:3000");
    }

    @Test
    void emptyEntriesAfterSplitAreIgnored() {
        WebConfig config = new WebConfig("http://localhost:5173,,http://localhost:3000");
        CorsConfigurationSource source = config.corsConfigurationSource();

        CorsConfiguration cors = source.getCorsConfiguration(mockRequest("http://localhost:5173"));
        assertThat(cors).isNotNull();
        assertThat(cors.getAllowedOrigins()).hasSize(2);
    }

    private jakarta.servlet.http.HttpServletRequest mockRequest(String origin) {
        org.springframework.mock.web.MockHttpServletRequest req = new org.springframework.mock.web.MockHttpServletRequest();
        req.setRequestURI("/api/test");
        req.addHeader("Origin", origin);
        return req;
    }
}

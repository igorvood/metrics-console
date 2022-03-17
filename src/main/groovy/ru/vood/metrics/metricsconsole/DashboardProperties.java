package ru.vood.metrics.metricsconsole;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;

@Data
@ConfigurationProperties(prefix = "dashboard")
public class DashboardProperties {
    private String letterSignatureUrl;
    private String letterGrabberUrl;
    private String guardUrl;

    private HashMap<String, String> serviceMap;
}

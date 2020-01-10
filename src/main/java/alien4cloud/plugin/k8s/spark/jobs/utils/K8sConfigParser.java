package alien4cloud.plugin.k8s.spark.jobs.utils;

import alien4cloud.plugin.k8s.spark.jobs.model.K8sConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Slf4j
public class K8sConfigParser {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public K8sConfig parse(String yaml) {
        K8sConfig result = null;
        try {
            result = mapper.readValue(yaml, K8sConfig.class);
        } catch (IOException e) {
            log.error("Can't parse kube config:",e);
        }
        return result;
    }
}

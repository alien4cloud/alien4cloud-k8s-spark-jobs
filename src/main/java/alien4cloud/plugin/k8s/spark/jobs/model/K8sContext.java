package alien4cloud.plugin.k8s.spark.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sContext {

    private String cluster;

    private String user;

    private String namespace;
}

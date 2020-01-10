package alien4cloud.plugin.k8s.spark.jobs.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class K8sCluster {

    private String server;

    @JsonProperty("certificate-authority-data")
    private String certificateAuthorityData;
}

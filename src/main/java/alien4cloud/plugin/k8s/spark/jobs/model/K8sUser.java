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
public class K8sUser {

    @JsonProperty("client-certificate-data")
    private String clientCertificateData;

    @JsonProperty("client-key-data")
    private String clientKeyData;
}

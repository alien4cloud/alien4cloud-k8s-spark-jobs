package alien4cloud.plugin.k8s.spark.jobs.csar;

import alien4cloud.plugin.archives.AbstractArchiveProviderPlugin;
import org.springframework.stereotype.Component;

@Component("spark-jobs-archive-provider")
public class ArchivesProvider extends AbstractArchiveProviderPlugin {

    @Override
    protected String[] getArchivesPaths() {
        return new String[] { "csar" };
    }
}

package com.example.product.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "storage")
public class StorageProperties {

    private S3Properties s3 = new S3Properties();
    private CdnProperties cdn = new CdnProperties();
    private BucketProperties buckets = new BucketProperties();

    @Getter
    @Setter
    public static class S3Properties {
        private String endpoint = "http://localhost:9000";
        private String region = "us-east-1";
        private String accessKey;
        private String secretKey;
        private boolean pathStyleAccess = true;
        private int presignedUrlExpirationMinutes = 15;
    }

    @Getter
    @Setter
    public static class CdnProperties {
        private String baseUrl = "http://localhost:9000";
    }

    @Getter
    @Setter
    public static class BucketProperties {
        private String productImages = "product-images";
    }
}

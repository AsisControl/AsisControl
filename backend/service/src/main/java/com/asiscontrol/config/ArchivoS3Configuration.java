package com.asiscontrol.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import java.time.Duration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "s3")
public class ArchivoS3Configuration {
    @Bean(destroyMethod = "close")
    public S3Client archivoS3Client(@Value("${app.storage.s3.region:}") String region) {
        if (region.isBlank()) throw new IllegalStateException("Configura FILE_STORAGE_S3_REGION para utilizar S3.");
        // La cadena de credenciales predeterminada utiliza el rol de EC2.
        return S3Client.builder().region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
                .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(45))
                        .apiCallAttemptTimeout(Duration.ofSeconds(25)))
                .build();
    }
}

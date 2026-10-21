package studio.ai.omni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Configuration
public class OmniRuntimeConfig {
    @Bean
    HttpClient omniHttpClient(OmniProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Bean(destroyMethod = "shutdown")
    ExecutorService omniJobExecutor(OmniProperties properties) {
        return Executors.newFixedThreadPool(
                Math.max(1, properties.getMaxConcurrentJobs()),
                Thread.ofPlatform().name("omni-job-", 0).factory()
        );
    }

}

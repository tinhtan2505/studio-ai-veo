package studio.ai.omni.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "app.omni")
public class OmniProperties {
    public static final String MODEL = "gemini-omni-1.1-flash";

    private String apiKey = "";
    private String baseUrl = "https://generativelanguage.googleapis.com";
    private Path outputDir = Path.of("data", "omni-output");
    private long maxUploadBytes = 2_147_483_648L;
    private int maxConcurrentJobs = 2;
    private int jobRetentionHours = 24;
    private int requestTimeoutMinutes = 30;
    private int outputPollSeconds = 5;
    private int outputPollTimeoutMinutes = 30;

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public Path getOutputDir() { return outputDir; }
    public void setOutputDir(Path outputDir) { this.outputDir = outputDir; }
    public long getMaxUploadBytes() { return maxUploadBytes; }
    public void setMaxUploadBytes(long maxUploadBytes) { this.maxUploadBytes = maxUploadBytes; }
    public int getMaxConcurrentJobs() { return maxConcurrentJobs; }
    public void setMaxConcurrentJobs(int maxConcurrentJobs) { this.maxConcurrentJobs = maxConcurrentJobs; }
    public int getJobRetentionHours() { return jobRetentionHours; }
    public void setJobRetentionHours(int jobRetentionHours) { this.jobRetentionHours = jobRetentionHours; }
    public int getRequestTimeoutMinutes() { return requestTimeoutMinutes; }
    public void setRequestTimeoutMinutes(int requestTimeoutMinutes) { this.requestTimeoutMinutes = requestTimeoutMinutes; }
    public int getOutputPollSeconds() { return outputPollSeconds; }
    public void setOutputPollSeconds(int outputPollSeconds) { this.outputPollSeconds = outputPollSeconds; }
    public int getOutputPollTimeoutMinutes() { return outputPollTimeoutMinutes; }
    public void setOutputPollTimeoutMinutes(int outputPollTimeoutMinutes) { this.outputPollTimeoutMinutes = outputPollTimeoutMinutes; }
}

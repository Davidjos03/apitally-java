package io.apitally.common;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;

import io.apitally.common.dto.Path;
import io.apitally.common.dto.StartupData;
import io.apitally.common.dto.SyncData;

public class ApitallyClient {
    public static class RetryableHubRequestException extends Exception {
        public RetryableHubRequestException(String message) {
            super(message);
        }
    }

    public enum HubRequestStatus {
        OK,
        VALIDATION_ERROR,
        INVALID_CLIENT_ID,
        PAYMENT_REQUIRED,
        RETRYABLE_ERROR
    }

    private static final int SYNC_INTERVAL_SECONDS = 60;
    private static final int INITIAL_SYNC_INTERVAL_SECONDS = 10;
    private static final int INITIAL_PERIOD_SECONDS = 3600;
    private static final int MAX_QUEUE_TIME_SECONDS = 3600;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;
    private static final String HUB_BASE_URL = Optional.ofNullable(System.getenv("APITALLY_HUB_BASE_URL"))
            .filter(s -> !s.trim().isEmpty())
            .orElse("https://hub.apitally.io");

    private static final Logger logger = LoggerFactory.getLogger(ApitallyClient.class);
    private static final RetryTemplate retryTemplate = RetryTemplate.builder()
            .maxAttempts(3)
            .exponentialBackoff(Duration.ofSeconds(1), 2, Duration.ofSeconds(4), true)
            .retryOn(RetryableHubRequestException.class)
            .build();

    private final String clientId;
    private final String env;
    private final UUID instanceUuid;
    private final HttpClient httpClient;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> syncTask;
    private StartupData startupData;
    private boolean startupDataSent = false;
    private boolean enabled = true;

    public final RequestCounter requestCounter;
    public final RequestLogger requestLogger;
    public final ValidationErrorCounter validationErrorCounter;
    public final ServerErrorCounter serverErrorCounter;
    public final ConsumerRegistry consumerRegistry;

    private final Queue<SyncData> syncDataQueue = new ConcurrentLinkedQueue<SyncData>();
    private final Random random = new Random();

    public ApitallyClient(String clientId, String env, RequestLoggingConfig requestLoggingConfig) {
        this.clientId = clientId;
        this.env = env;
        this.instanceUuid = java.util.UUID.randomUUID();
        this.httpClient = createHttpClient();

        this.requestCounter = new RequestCounter();
        this.requestLogger = new RequestLogger(requestLoggingConfig);
        this.validationErrorCounter = new ValidationErrorCounter();
        this.serverErrorCounter = new ServerErrorCounter();
        this.consumerRegistry = new ConsumerRegistry();
    }

    public boolean isEnabled() {
        return enabled;
    }

    private HttpClient createHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
                .build();
    }

    private URI getHubUrl(String endpoint) {
        return getHubUrl(endpoint, "");
    }

    private URI getHubUrl(String endpoint, String query) {
        String baseUrl = HUB_BASE_URL.replaceAll("/+$", "");
        if (!query.isEmpty() && !query.startsWith("?")) {
            query = "?" + query;
        }
        return URI.create(baseUrl + "/v2/" + clientId + "/" + env + "/" + endpoint + query);
    }

    public void setStartupData(List<Path> paths, Map<String, String> versions, String client) {
        startupData = new StartupData(instanceUuid, paths, versions, client);
    }

    private void sendStartupData() {
        if (startupData == null) {
            return;
        }
        HttpRequest request = HttpRequest.newBuilder()
                .uri(getHubUrl("startup"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(startupData.toJSON()))
                .build();
        sendHubRequest(request).thenAccept(status -> {
            if (status == HubRequestStatus.OK) {
                startupDataSent = true;
                startupData = null;
            } else if (status == HubRequestStatus.VALIDATION_ERROR) {
                startupDataSent = false;
                startupData = null;
            } else {
                startupDataSent = false;
            }
        });
    }

    private void sendSyncData() {
        SyncData data = new SyncData(
                instanceUuid,
                requestCounter.getAndResetRequests(),
                validationErrorCounter.getAndResetValidationErrors(),
                serverErrorCounter.getAndResetServerErrors(),
                consumerRegistry.getAndResetConsumers());
        syncDataQueue.offer(data);

        int i = 0;
        while (!syncDataQueue.isEmpty()) {
            SyncData payload = syncDataQueue.poll();
            if (payload != null) {
                try {
                    if (payload.getAgeInSeconds() <= MAX_QUEUE_TIME_SECONDS) {
                        if (i > 0) {
                            // Add random delay between retries
                            Thread.sleep(100 + random.nextInt(400));
                        }
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(getHubUrl("sync"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(payload.toJSON()))
                                .build();
                        HubRequestStatus status = sendHubRequest(request).join();
                        if (status == HubRequestStatus.RETRYABLE_ERROR) {
                            syncDataQueue.offer(payload);
                            break;
                        }
                        i++;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void sendLogData() {
        requestLogger.rotateFile();
        int i = 0;
        TempGzipFile logFile;
        while ((logFile = requestLogger.getFile()) != null) {
            if (i > 0) {
                try {
                    Thread.sleep(100 + random.nextInt(400));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            try (InputStream inputStream = logFile.getInputStream()) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(getHubUrl("log", "uuid=" + logFile.getUuid().toString()))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofInputStream(() -> inputStream))
                        .build();
                HubRequestStatus status = sendHubRequest(request).join();
                if (status == HubRequestStatus.PAYMENT_REQUIRED) {
                    requestLogger.clear();
                    requestLogger.setSuspendUntil(System.currentTimeMillis() + (3600 * 1000L));
                } else if (status == HubRequestStatus.RETRYABLE_ERROR) {
                    requestLogger.retryFileLater(logFile);
                    break;
                } else {
                    logFile.delete();
                }
            } catch (Exception e) {
                logFile.delete();
            }
            i++;
            if (i >= 10) {
                break;
            }
        }
    }

    public CompletableFuture<HubRequestStatus> sendHubRequest(HttpRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return retryTemplate.execute(context -> {
                    try {
                        logger.debug("Sending request to Apitally hub: {}", request.uri());
                        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return HubRequestStatus.OK;
                        } else if (response.statusCode() == 402) {
                            return HubRequestStatus.PAYMENT_REQUIRED;
                        } else if (response.statusCode() == 404) {
                            enabled = false;
                            stopSync();
                            requestLogger.close();
                            logger.error("Invalid Apitally client ID: {}", clientId);
                            return HubRequestStatus.INVALID_CLIENT_ID;
                        } else if (response.statusCode() == 422) {
                            logger.error("Received validation error from Apitally hub: {}", response.body());
                            return HubRequestStatus.VALIDATION_ERROR;
                        } else {
                            throw new RetryableHubRequestException(
                                    "Hub request failed with status code " + response.statusCode());
                        }
                    } catch (Exception e) {
                        throw new RetryableHubRequestException(
                                "Hub request failed with exception: " + e.getMessage());
                    }
                });
            } catch (Exception e) {
                logger.error("Error sending request to Apitally hub", e);
                return HubRequestStatus.RETRYABLE_ERROR;
            }
        });
    }

    public void startSync() {
        if (scheduler == null) {
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread thread = new Thread(r, "apitally-sync");
                thread.setDaemon(true);
                return thread;
            });
        }

        if (syncTask != null) {
            syncTask.cancel(false);
        }

        // Start with shorter initial sync interval
        syncTask = scheduler.scheduleAtFixedRate(
                this::sync,
                0,
                INITIAL_SYNC_INTERVAL_SECONDS,
                TimeUnit.SECONDS);

        // Schedule a one-time task to switch to regular sync interval
        scheduler.schedule(() -> {
            syncTask.cancel(false);
            syncTask = scheduler.scheduleAtFixedRate(
                    this::sync,
                    SYNC_INTERVAL_SECONDS,
                    SYNC_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }, INITIAL_PERIOD_SECONDS, TimeUnit.SECONDS);
    }

    public void stopSync() {
        if (syncTask != null) {
            syncTask.cancel(false);
            syncTask = null;
        }
    }

    private void sync() {
        if (!startupDataSent) {
            sendStartupData();
        }
        sendSyncData();
        sendLogData();
    }

    public void shutdown() {
        try {
            enabled = false;
            boolean syncRunning = syncTask != null;
            stopSync();
            if (syncRunning) {
                // Final sync to ensure all data is sent
                sync();
            }
            if (scheduler != null) {
                scheduler.shutdown();
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            }
        } catch (InterruptedException e) {
            if (scheduler != null) {
                scheduler.shutdownNow();
            }
            Thread.currentThread().interrupt();
        }
    }
}

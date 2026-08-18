package com.cmacgm.gbs.rst.api.graph;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.cmacgm.gbs.rst.api.common.error.ApiException;
import com.cmacgm.gbs.rst.api.graph.MicrosoftGraphModels.TokenResponse;

import tools.jackson.databind.json.JsonMapper;

/**
 * HTTP client for Microsoft identity tokens and Graph REST calls.
 */
@Component
public class MicrosoftGraphClient {

    private static final Logger log = LoggerFactory.getLogger(MicrosoftGraphClient.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration TOKEN_SKEW = Duration.ofSeconds(60);

    private final MicrosoftGraphProperties properties;
    private final Clock clock;
    private final RestClient tokenClient;
    private final RestClient graphClient;
    private final AtomicReference<CachedToken> cachedToken = new AtomicReference<>();

    /**
     * @param properties Graph settings
     * @param jsonMapper Jackson 3 mapper
     * @param clock clock used for token expiry
     */
    public MicrosoftGraphClient(
            MicrosoftGraphProperties properties,
            JsonMapper jsonMapper,
            Clock clock) {
        this.properties = properties;
        this.clock = clock;
        JdkClientHttpRequestFactory requestFactory = requestFactory();
        JacksonJsonHttpMessageConverter jsonConverter = new JacksonJsonHttpMessageConverter(jsonMapper);
        this.tokenClient = RestClient.builder()
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(jsonConverter))
                .build();
        this.graphClient = RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.graphHost()))
                .requestFactory(requestFactory)
                .configureMessageConverters(converters -> converters.withJsonConverter(jsonConverter))
                .build();
    }

    /**
     * GET a Graph JSON resource.
     *
     * @param uri absolute Graph URI
     * @param type response type
     * @param <T> response type
     * @return parsed body
     */
    public <T> T get(URI uri, Class<T> type) {
        return get(uri, ParameterizedTypeReference.forType(type));
    }

    /**
     * GET a Graph JSON resource with generic type information.
     *
     * @param uri absolute Graph URI
     * @param type parameterized response type
     * @param <T> response type
     * @return parsed body
     */
    public <T> T get(URI uri, ParameterizedTypeReference<T> type) {
        try {
            T body = graphClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(type);
            if (body == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "graph-empty-response",
                        "Microsoft Graph returned an empty response for " + uri);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw graphHttpFailure("Graph request failed for " + uri, ex);
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-unreachable",
                    "Microsoft Graph is unreachable: " + ex.getMessage());
        }
    }

    /**
     * GET Graph file content.
     *
     * @param uri absolute Graph content URI
     * @return content stream
     */
    public InputStream getContent(URI uri) {
        try {
            InputStream body = graphClient.get()
                    .uri(uri)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
                    .retrieve()
                    .body(InputStream.class);
            if (body == null) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "graph-empty-content",
                        "Microsoft Graph returned no content for " + uri);
            }
            return body;
        } catch (ApiException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            throw graphHttpFailure("Graph download failed for " + uri, ex);
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-unreachable",
                    "Microsoft Graph is unreachable: " + ex.getMessage());
        }
    }

    /**
     * Builds an absolute Graph URI from a path that already starts with {@code /}.
     *
     * @param path Graph path including query string
     * @return absolute URI
     */
    public URI graphUri(String path) {
        String host = trimTrailingSlash(properties.graphHost());
        String suffix = path.startsWith("/") ? path : "/" + path;
        return URI.create(host + suffix);
    }

    private String accessToken() {
        ensureConfigured();
        CachedToken current = cachedToken.get();
        Instant now = clock.instant();
        if (current != null && current.expiresAt().minus(TOKEN_SKEW).isAfter(now)) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken.get();
            now = clock.instant();
            if (current != null && current.expiresAt().minus(TOKEN_SKEW).isAfter(now)) {
                return current.value();
            }
            TokenResponse token = requestToken();
            if (token == null || token.accessToken() == null || token.accessToken().isBlank()) {
                throw new ApiException(
                        HttpStatus.BAD_GATEWAY,
                        "graph-token-empty",
                        "Microsoft identity returned an empty access token.");
            }
            long expiresIn = token.expiresIn() > 0 ? token.expiresIn() : 3600;
            cachedToken.set(new CachedToken(token.accessToken(), now.plusSeconds(expiresIn)));
            log.info(
                    "Microsoft Graph token acquired: secretName={} clientId={} expiresIn={}s",
                    properties.secretName(),
                    properties.clientId(),
                    expiresIn);
            return token.accessToken();
        }
    }

    private TokenResponse requestToken() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("scope", properties.scope());
        form.add("grant_type", "client_credentials");
        try {
            return tokenClient.post()
                    .uri(URI.create(properties.authUri()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(TokenResponse.class);
        } catch (RestClientResponseException ex) {
            throw graphHttpFailure("Microsoft identity token request failed", ex);
        } catch (RestClientException ex) {
            throw new ApiException(
                    HttpStatus.BAD_GATEWAY,
                    "graph-token-unreachable",
                    "Microsoft identity is unreachable: " + ex.getMessage());
        }
    }

    private void ensureConfigured() {
        if (!properties.enabled()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "graph-disabled",
                    "Microsoft Graph is disabled. Set MS_GRAPH_ENABLED=true to use SharePoint.");
        }
        if (!properties.hasCredentials()) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "graph-not-configured",
                    "Microsoft Graph credentials are missing. Set AZURE_TENANT_ID, "
                            + "MS_GRAPH_CLIENT_ID and MS_GRAPH_CLIENT_SECRET "
                            + "(secret name " + properties.secretName() + ").");
        }
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return requestFactory;
    }

    private static String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "https://graph.microsoft.com/v1.0";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static ApiException graphHttpFailure(String detail, RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        String suffix = body == null || body.isBlank() ? "" : ": " + truncate(body);
        return new ApiException(
                HttpStatus.BAD_GATEWAY,
                "graph-request-failed",
                detail + " (HTTP " + ex.getStatusCode().value() + ")" + suffix);
    }

    private static String truncate(String body) {
        String trimmed = body.trim().replace('\n', ' ');
        return trimmed.length() <= 240 ? trimmed : trimmed.substring(0, 240) + "…";
    }

    private record CachedToken(String value, Instant expiresAt) {
    }
}

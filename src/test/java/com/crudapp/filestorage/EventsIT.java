package com.crudapp.filestorage;

import com.crudapp.filestorage.dto.EventDto;
import com.crudapp.filestorage.dto.FileDto;
import com.crudapp.filestorage.dto.PageResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EventsIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("filestorage")
            .withUsername("fs_user")
            .withPassword("fs_pass_123");

    @Container
    static LocalStackContainer localstack =
            new LocalStackContainer(DockerImageName.parse("localstack/localstack:3"))
                    .withServices(LocalStackContainer.Service.S3);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);

        r.add("s3.endpoint", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());
        r.add("s3.accessKey", localstack::getAccessKey);
        r.add("s3.secretKey", localstack::getSecretKey);
        r.add("s3.region", () -> "us-east-1");
        r.add("s3.bucket", () -> "it-bucket");
        r.add("s3.publicBaseUrl", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());

        r.add("spring.main.web-application-type", () -> "reactive");
        r.add("spring.codec.multipart.enabled", () -> "true");
        r.add("logging.level.org.springframework.web", () -> "WARN");
    }

    @Autowired WebClient.Builder builder;
    @LocalServerPort int port;

    WebClient client;
    ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setup() {
        client = builder.baseUrl("http://localhost:" + port).build();

        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.US_EAST_1)
                .build();
        s3.createBucket(b -> b.bucket("it-bucket"));
    }

    private String registerAndLogin(String username, String password) throws Exception {
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String login = client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", username, "password", password))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return om.readTree(login).get("token").asText();
    }

    private Integer meId(String jwt) {
        String json = client.get().uri("/users/me")
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            var node = om.readTree(json);
            Integer id = null;
            if (node.hasNonNull("id")) id = node.get("id").asInt();
            else if (node.hasNonNull("userId")) id = node.get("userId").asInt();

            assertThat(id)
                    .as("/users/me не содержит id. Payload: %s", json)
                    .isNotNull();
            return id;
        } catch (Exception e) {
            throw new AssertionError("Не смог распарсить ответ /users/me. Raw: " + json, e);
        }
    }

    private FileDto uploadWithToken(String jwt, String filename, byte[] data) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", new ByteArrayResource(data) {
            @Override public String getFilename() { return filename; }
        });

        return client.post().uri("/files")
                .headers(h -> h.setBearerAuth(jwt))
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .bodyToMono(FileDto.class)
                .block();
    }

    private void renameWithToken(String jwt, int fileId, String newName) {
        client.put().uri("/files/{id}", fileId)
                .headers(h -> h.setBearerAuth(jwt))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", newName))
                .retrieve()
                .bodyToMono(FileDto.class)
                .block();
    }

    private void deleteWithToken(String jwt, int fileId) {
        client.delete().uri("/files/{id}", fileId)
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .toBodilessEntity()
                .block();
    }

    private PageResponse<EventDto> listEventsPaged(String jwt, int page, int size) {
        return client.get().uri(uriBuilder ->
                        uriBuilder.path("/events/paged")
                                .queryParam("page", page)
                                .queryParam("size", size)
                                .build())
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .bodyToMono(new org.springframework.core.ParameterizedTypeReference<PageResponse<EventDto>>() {})
                .block();
    }

    @Test
    void events_flow_for_user_created_updated_deleted() throws Exception {
        String jwt = registerAndLogin("ev_user", "pass123");
        Integer userId = meId(jwt);

        FileDto uploaded = uploadWithToken(jwt, "ev.txt", "hello".getBytes());
        renameWithToken(jwt, uploaded.id(), "renamed.txt");
        deleteWithToken(jwt, uploaded.id());

        PageResponse<EventDto> page = listEventsPaged(jwt, 0, 20);

        assertThat(page).as("page из /events/paged не должен быть null").isNotNull();
        List<EventDto> content = page.content();
        assertThat(content).as("content не должен быть null").isNotNull();

        List<EventDto> forFile = content.stream()
                .filter(e -> Objects.equals(e.fileId(), uploaded.id()))
                .toList();

        List<String> statuses = forFile.stream().map(EventDto::status).toList();
        assertThat(statuses)
                .as("Ожидаем статусы CREATED/UPDATED/DELETED по файлу %s, фактически: %s", uploaded.id(), statuses)
                .contains("CREATED", "UPDATED", "DELETED");

        Set<Integer> userIds = forFile.stream().map(EventDto::userId).collect(Collectors.toSet());
        assertThat(userIds)
                .as("userId-ы в событиях по файлу должны быть только %s, а получились: %s", userId, userIds)
                .containsOnly(userId);
    }

    @Test
    void user_sees_only_his_events() throws Exception {
        String jwt1 = registerAndLogin("owner1", "pass123");
        uploadWithToken(jwt1, "one.txt", "1".getBytes());

        String jwt2 = registerAndLogin("owner2", "pass123");

        PageResponse<EventDto> page2 = listEventsPaged(jwt2, 0, 20);
        assertThat(page2).isNotNull();

        if (!page2.content().isEmpty()) {
            Integer u2id = meId(jwt2);
            boolean allMine = page2.content().stream()
                    .allMatch(e -> Objects.equals(e.userId(), u2id));
            assertThat(allMine)
                    .as("Все события в выборке для второго пользователя должны принадлежать ему (userId=%s)", u2id)
                    .isTrue();
        }
    }
}
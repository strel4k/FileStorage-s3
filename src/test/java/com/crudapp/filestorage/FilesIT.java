package com.crudapp.filestorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.MediaType;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilesIT {

    record FileDto(Integer id, String name, String location, String status) {}

    private static final String BUCKET = "it-bucket";

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
        r.add("s3.bucket", () -> BUCKET);
        r.add("s3.publicBaseUrl", () -> localstack.getEndpointOverride(LocalStackContainer.Service.S3).toString());

        r.add("spring.codec.multipart.enabled", () -> "true");
        r.add("spring.main.web-application-type", () -> "reactive");
    }

    @Autowired
    WebClient.Builder webClientBuilder;

    WebClient client;

    @LocalServerPort
    int port;

    final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() throws Exception {
        client = webClientBuilder
                .baseUrl("http://localhost:" + port)
                .build();

        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.US_EAST_1)
                .build();
        try {
            s3.createBucket(b -> b.bucket(BUCKET));
        } catch (Exception ignore) {}
    }

    @Test
    void register_login_upload_and_get() throws Exception {
        var registerJson = client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ituser", "password", "pass123"))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        final String tokenJson = client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", "ituser", "password", "pass123"))
                .retrieve()
                .bodyToMono(String.class)
                .block();
        String jwt = om.readTree(tokenJson).get("token").asText();
        assertThat(jwt).isNotBlank();

        Path tmp = Files.createTempFile("hello", ".txt");
        Files.writeString(tmp, "hello");
        FileSystemResource fsr = new FileSystemResource(tmp.toFile());

        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", fsr);

        String uploadRespJson = client.post().uri("/files")
                .headers(h -> h.setBearerAuth(jwt))
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .bodyToMono(String.class)
                .block(Duration.ofSeconds(30));

        FileDto uploaded = om.readValue(uploadRespJson, FileDto.class);
        assertThat(uploaded.name()).isEqualTo(fsr.getFilename());
        assertThat(uploaded.status()).isEqualTo("ACTIVE");
        assertThat(uploaded.location()).contains(BUCKET + "/u");

        FileDto fetched = client.get().uri("/files/{id}", uploaded.id())
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .bodyToMono(FileDto.class)
                .block(Duration.ofSeconds(30));
        assertThat(fetched.id()).isEqualTo(uploaded.id());
        assertThat(fetched.status()).isNotBlank();

        var clientResp = client.get()
                .uri("/files/{id}/download", uploaded.id())
                .headers(h -> h.setBearerAuth(jwt))
                .exchangeToMono(Mono::just)
                .block(Duration.ofSeconds(30));
        assertThat(clientResp.statusCode().is3xxRedirection()).isTrue();
        var loc = clientResp.headers().header(HttpHeaders.LOCATION);
        assertThat(loc).isNotEmpty();
        assertThat(loc.get(0)).contains("http");

        String newName = "hello-renamed.txt";
        FileDto renamed = client.put()
                .uri("/files/{id}", uploaded.id())
                .headers(h -> h.setBearerAuth(jwt))
                .bodyValue(Map.of("name", newName))
                .retrieve()
                .bodyToMono(FileDto.class)
                .block(Duration.ofSeconds(30));
        assertThat(renamed.name()).isEqualTo(newName);

        client.delete()
                .uri("/files/{id}", uploaded.id())
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .toBodilessEntity()
                .block(Duration.ofSeconds(30));

        FileDto afterDelete = client.get().uri("/files/{id}", uploaded.id())
                .headers(h -> h.setBearerAuth(jwt))
                .retrieve()
                .bodyToMono(FileDto.class)
                .block(Duration.ofSeconds(30));
        assertThat(afterDelete.status()).isEqualTo("ARCHIVED");
    }
}

package com.crudapp.filestorage;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
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
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class FilesPermissionsIT {

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

    @BeforeEach
    void setup() {
        client = builder.baseUrl("http://localhost:" + port).build();
        S3Client s3 = S3Client.builder()
                .endpointOverride(localstack.getEndpointOverride(LocalStackContainer.Service.S3))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())))
                .region(Region.US_EAST_1).build();
        s3.createBucket(b -> b.bucket("it-bucket"));
    }

    private String registerAndLogin(String u, String p) throws Exception {
        client.post().uri("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", u, "password", p))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        String login = client.post().uri("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("username", u, "password", p))
                .retrieve()
                .bodyToMono(String.class)
                .block();

        return new ObjectMapper().readTree(login).get("token").asText();
    }

    private Integer uploadWithToken(String jwt, String name) {
        MultipartBodyBuilder mb = new MultipartBodyBuilder();
        mb.part("file", new ByteArrayResource("hello".getBytes()) {
            @Override public String getFilename() { return name; }
        });

        var dto = client.post().uri("/files")
                .headers(h -> h.setBearerAuth(jwt))
                .body(BodyInserters.fromMultipartData(mb.build()))
                .retrieve()
                .bodyToMono(com.crudapp.filestorage.dto.FileDto.class)
                .block();

        assertThat(dto).isNotNull();
        return dto.id();
    }

    @Test
    void user_cannot_rename_someone_elses_file() throws Exception {
        String jwt1 = registerAndLogin("uA", "pass123");
        String jwt2 = registerAndLogin("uB", "pass123");

        Integer fileId = uploadWithToken(jwt1, "a.txt");

        int status = client.put().uri("/files/{id}", fileId)
                .headers(h -> h.setBearerAuth(jwt2))
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("name", "new.txt"))
                .exchangeToMono(resp -> resp.toBodilessEntity().map(e -> resp.statusCode().value()))
                .block();

        assertThat(status).isEqualTo(403);
    }
}

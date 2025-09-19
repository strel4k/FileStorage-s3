plugins {
    id("org.springframework.boot") version "3.3.3"
    id("io.spring.dependency-management") version "1.1.5"
    id("org.flywaydb.flyway") version "10.17.0"
    java
}

java {
    toolchain { languageVersion.set(JavaLanguageVersion.of(17)) }
}

repositories {
    mavenCentral()
}

configurations {
    create("flywayMigration")
}

dependencies {
    // Reactive web + security
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // JPA + Flyway
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-mysql")
    runtimeOnly("com.mysql:mysql-connector-j")

    add("flywayMigration", "org.flywaydb:flyway-mysql:10.17.0")
    add("flywayMigration", "com.mysql:mysql-connector-j:8.4.0")


    // OpenAPI (Swagger UI) для WebFlux
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.6.0")

    // AWS SDK v2
    implementation(platform("software.amazon.awssdk:bom:2.25.66"))

    implementation("software.amazon.awssdk:s3")
    implementation("software.amazon.awssdk:netty-nio-client")


    implementation("software.amazon.awssdk:auth")
    implementation("software.amazon.awssdk:regions")

    // JWT (Nimbus JOSE + JWT)
    implementation("com.nimbusds:nimbus-jose-jwt:9.40")

    // Lombok
    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")

    // Тесты
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.mockito:mockito-core:5.12.0")
    testImplementation("org.testcontainers:localstack:1.20.1")
    testImplementation("org.testcontainers:junit-jupiter:1.20.1")
    testImplementation("org.testcontainers:mysql:1.20.1")
}

flyway {
    url = "jdbc:mysql://localhost:${System.getenv("MYSQL_PORT") ?: "3307"}/filestorage" +
            "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=utf8"
    user = System.getenv("MYSQL_USER") ?: "fs_user"
    password = System.getenv("MYSQL_PASSWORD") ?: "fs_pass_123"
    driver = "com.mysql.cj.jdbc.Driver"
    locations = arrayOf("filesystem:src/main/resources/db/migration")
    cleanDisabled = true

    configurations = arrayOf("flywayMigration")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

#📂FileStorage-s3

🪄## Функциональность

**👤 Пользователи и роли**

- Регистрация / Логин (/auth/register, /auth/login)

- JWT-аутентификация (Bearer)

- Роли: ADMIN, MODERATOR, USER

- Текущий пользователь (/users/me)

**📁 Файлы

- Загрузка в S3 (multipart /files)

- Переименование (PUT /files/{id})

- Удаление (DELETE /files/{id})
↳ S3 удаляется best-effort, в БД файл помечается ARCHIVED

- Списки и постраничный вывод

- Выдача presigned URL для скачивания

**📜 События (Audit Trail)

- Фиксируются статусы: CREATED, UPDATED, DELETED

- Поля: userId, fileId, status, message?, createdAt

- Просмотр c пагинацией (GET /events/paged)

- Обычный пользователь видит только свои события

- MODERATOR/ADMIN могут видеть события всех

##🪜 Технологии

- Java 17, Gradle

- Spring Boot 3.3: WebFlux, Security, Data JPA

- Hibernate 6.5, Flyway

- MySQL 8

- AWS SDK v2 (S3), LocalStack

- Testcontainers (MySQL + LocalStack)

##🔐 Безопасность (коротко)

- permitAll: /auth/**, /v3/api-docs/**, /swagger-ui/**

- /users/me — только аутентифицированным

- /users/**:

- GET — ADMIN или MODERATOR

- другие методы — только ADMIN

- Остальное — authenticated()

- Аутентификация — Bearer JWT

##🧪 Тесты (Testcontainers)

- Нужен запущенный Docker.

./gradlew test

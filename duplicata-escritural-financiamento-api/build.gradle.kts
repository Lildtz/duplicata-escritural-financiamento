plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

group = "br.com.duplicataescritural"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // A validação vertical usa somente o artefato executável, sem importar classes da Signatária.
    dependsOn(":duplicata-escritural-signataria-api:bootJar")
    val jarSignataria = providers.provider {
        project(":duplicata-escritural-signataria-api")
            .tasks.named<Jar>("bootJar").get().archiveFile.get().asFile
    }
    inputs.file(jarSignataria).withPropertyName("signatariaBootJar").withPathSensitivity(PathSensitivity.NONE)
    doFirst {
        systemProperty("signataria.jar", jarSignataria.get().absolutePath)
    }
}

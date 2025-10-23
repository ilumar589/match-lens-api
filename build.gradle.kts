import com.google.protobuf.gradle.id

plugins {
	java
	id("org.springframework.boot") version "3.5.6"
	id("io.spring.dependency-management") version "1.1.7"
	id("org.graalvm.buildtools.native") version "0.10.6"
	id("com.google.protobuf") version "0.9.4"
}

group = "org.jstats"
version = "0.0.1-SNAPSHOT"
description = "Demo project for sports predictions"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

extra["springGrpcVersion"] = "0.11.0"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web") {
		exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
	}
    implementation("org.springframework.boot:spring-boot-starter-log4j2")
    configurations.all {
        // Enforce logging stack: Log4j2 only
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-logging") // removes logback starter
        // Ensure Logback is not pulled transitively by test utilities
        exclude(group = "ch.qos.logback", module = "logback-classic")
        exclude(group = "ch.qos.logback", module = "logback-core")

        // Enforce Jetty by removing any Tomcat artifacts that may come transitively from other starters
        exclude(group = "org.springframework.boot", module = "spring-boot-starter-tomcat")
        exclude(group = "org.apache.tomcat.embed", module = "tomcat-embed-core")
        exclude(group = "org.apache.tomcat.embed", module = "tomcat-embed-el")
        exclude(group = "org.apache.tomcat.embed", module = "tomcat-embed-websocket")
    }
    implementation("com.lmax:disruptor:4.0.0")
    implementation("org.springframework.boot:spring-boot-starter-jetty")
    // Add Jetty HTTP/2 server module for h2/h2c support
    implementation("org.eclipse.jetty.http2:jetty-http2-server")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // Jakarta EL needed by Hibernate Validator when Tomcat EL is excluded; Jetty does not provide it
    implementation("org.glassfish:jakarta.el:4.0.2")
    implementation("org.springframework.boot:spring-boot-starter-aop")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.13")
    implementation("io.grpc:grpc-services")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
    implementation("org.postgresql:postgresql")
	implementation("org.springframework.grpc:spring-grpc-server-web-spring-boot-starter")
	implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    compileOnly("org.projectlombok:lombok")
    compileOnly("org.jspecify:jspecify:1.0.0")
    implementation("org.apiguardian:apiguardian-api:1.1.2")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	developmentOnly("org.springframework.boot:spring-boot-docker-compose")
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")
	annotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.springframework.grpc:spring-grpc-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.testcontainers:postgresql")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.grpc:spring-grpc-dependencies:${property("springGrpcVersion")}")
	}
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc"
	}
	plugins {
		id("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java"
		}
	}
	generateProtoTasks {
		all().forEach {
			it.plugins {
				id("grpc") {
					option("@generated=omit")
				}
			}
		}
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Ensure FOOTBALL_DATA_API_KEY is visible to the app even when Gradle daemon caches env vars
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    // forward current env var as a JVM system property; safe if null
    val apiKey: String? = System.getenv("FOOTBALL_DATA_API_KEY")
    if (!apiKey.isNullOrBlank()) {
        systemProperty("FOOTBALL_DATA_API_KEY", apiKey)
    }
}

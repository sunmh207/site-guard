plugins {
	// 应用 Java 插件，提供编译、测试、打包等基础能力
	id("java")
	// Spring Boot 插件：用于构建 Spring Boot 应用（打包为可执行 jar、内置容器等）
	id("org.springframework.boot") version "4.0.5"
	// Spring 依赖管理插件：统一依赖版本，避免手动指定版本冲突
	id("io.spring.dependency-management") version "1.1.7"
	// 在构建阶段自动生成 OpenAPI（Swagger）接口文档，并与 Spring Boot 应用集成。
	id("org.springdoc.openapi-gradle-plugin") version "1.9.0"
}

group = "com.siteguard"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// Spring Boot
	implementation("org.springframework.boot:spring-boot-starter")
	// Actuator：用于健康检查（/actuator/health），Docker 部署时供探活使用
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-validation")

	// MySQL JDBC 驱动
	runtimeOnly("com.mysql:mysql-connector-j")

	// Flyway 数据库迁移工具
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	implementation("org.flywaydb:flyway-mysql")

	// 定时任务
	implementation("org.springframework.boot:spring-boot-starter-quartz")

	// JWT 支持
//	implementation("com.auth0:java-jwt:4.4.0")
	implementation ("io.jsonwebtoken:jjwt-api:0.13.0")
	runtimeOnly ("io.jsonwebtoken:jjwt-impl:0.13.0")
	runtimeOnly ("io.jsonwebtoken:jjwt-jackson:0.13.0")

	// SpringDoc OpenAPI (Swagger UI)
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

	// Querydsl
	implementation("com.querydsl:querydsl-jpa:5.1.0:jakarta")
	annotationProcessor("com.querydsl:querydsl-apt:5.1.0:jakarta")
	annotationProcessor("jakarta.annotation:jakarta.annotation-api")
	annotationProcessor("jakarta.persistence:jakarta.persistence-api")

	// Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

	// MapStruct
	implementation("org.mapstruct:mapstruct:1.6.3")
	annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

	// Hutool 工具集（钉钉/飞书签名 + HTTP 客户端）
	implementation("cn.hutool:hutool-core:5.8.28")
	implementation("cn.hutool:hutool-http:5.8.28")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	// H2 嵌入式数据库用于 @DataJpaTest（Repository 层集成测试）
	// H2 支持 MySQL 兼容模式以减少 Flyway 迁移中的方言差异
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// BouncyCastle 测试用：运行时生成各种异常证书（过期/自签/域名不匹配/链不完整）覆盖 lenient 分级判定
	testImplementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
	testRuntimeOnly("org.bouncycastle:bcprov-jdk18on:1.78.1")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

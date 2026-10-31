plugins {
    java
}

group = "io.github.tadzi00"
version = providers.gradleProperty("pluginVersion").get()

val paperApiVersion: String = providers.gradleProperty("paperApiVersion").get()
val minecraftVersion: String = providers.gradleProperty("minecraftVersion").get()

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")

    // Pure logic tests only: Paper API is deliberately NOT on the test classpath so that
    // anything under the `core` package that accidentally imports Bukkit fails to compile tests.
    testImplementation(platform("org.junit:junit-bom:5.11.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release = 21
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.processResources {
    val props = mapOf("version" to project.version, "minecraftVersion" to minecraftVersion)
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName = "RealisticSeasons"
}

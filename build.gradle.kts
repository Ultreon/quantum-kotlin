import org.jetbrains.gradle.ext.Application
import org.jetbrains.gradle.ext.GradleTask
import org.jetbrains.gradle.ext.runConfigurations
import org.jetbrains.gradle.ext.settings
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

plugins {
    idea
    `maven-publish`
    kotlin("jvm") version "1.9.22"
    id("org.jetbrains.gradle.plugin.idea-ext") version "+"
}

group = "io.github.ultreon.craftmods"
version = "0.1.0+snapshot." + DateTimeFormatter.ofPattern("yyyy.MM.dd.HH.mm").format(Instant.now().atOffset(ZoneOffset.UTC))

repositories {
    mavenCentral()

    maven {
        name = "UltracraftGitHub"
        url = uri("https://maven.pkg.github.com/Ultreon/ultracraft")
        credentials {
            username = (project.findProperty("gpr.user") ?: System.getenv("USERNAME")) as String
            password = (project.findProperty("gpr.key") ?: System.getenv("TOKEN")) as String
        }
    }

    maven {
        name = "Jitpack"
        url = uri("https://jitpack.io")

        content {
            includeGroup("space.earlygrey")
            includeGroup("com.github.mgsx-dev.gdx-gltf")
            includeGroup("com.github.Ultreon")
            includeGroup("com.github.jagrosh")
            includeGroup("com.github.JnCrMx")
        }
    }

    maven("https://maven.fabricmc.net/")
}

configurations {
    create("include") {
        isCanBeResolved = true
    }
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")

    implementation("io.github.ultreon.craft:ultracraft-desktop:0.1.0+snapshot.2024.03.24.23.50")
    
    configurations["include"](compileOnly("org.jetbrains.kotlin:kotlin-stdlib-jdk8")!!)
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(21)
}

tasks.jar {
    from(configurations["include"].map { if (it.isDirectory) it else zipTree(it) })

    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

fun setupIdea() {
    mkdir("$projectDir/build/gameutils")
    mkdir("$projectDir/run")
    mkdir("$projectDir/run/client")
    mkdir("$projectDir/run/client/alt")
    mkdir("$projectDir/run/client/main")
    mkdir("$projectDir/run/server")

    val ps = File.pathSeparator!!
    val files = configurations["runtimeClasspath"]!!

    val classPath = files.asSequence()
        .filter { it != null }
        .map { it.path }
        .joinToString(ps)

    //language=TEXT
    val conf = """
commonProperties
	fabric.development=true
	log4j2.formatMsgNoLookups=true
	fabric.log.disableAnsi=false
	log4j.configurationFile=$projectDir/log4j.xml
    """.trimIndent()
    val launchFile = file("$projectDir/build/gameutils/launch.cfg")
    Files.writeString(
        launchFile.toPath(),
        conf,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
    )

    val cpFile = file("$projectDir/build/gameutils/classpath.txt")
    Files.writeString(
        cpFile.toPath(),
        classPath,
        StandardOpenOption.CREATE,
        StandardOpenOption.TRUNCATE_EXISTING,
        StandardOpenOption.WRITE
    )

    rootProject.idea {
        project {
            settings {
                withIDEADir {
                    println("Callback 1 executed with: $absolutePath")
                }

                runConfigurations {
                    create(
                        "Ultracraft Client",
                        Application::class.java
                    ) {                       // Create new run configuration "MyApp" that will run class foo.App
                        jvmArgs =
                            "-Xmx4g -Dfabric.skipMcProvider=true -Dfabric.dli.config=${launchFile.path} -Dfabric.dli.env=CLIENT -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -Dfabric.zipfs.use_temp_file=false"
                        mainClass = "net.fabricmc.devlaunchinjector.Main"
                        moduleName = idea.module.name + ".main"
                        workingDirectory = "$projectDir/run/client/main/"
                        programParameters = "--gameDir=."
                        beforeRun {
                            create("Clear Quilt Cache", GradleTask::class.java) {
                                this.task = tasks.named("clearClientMainQuiltCache").get()
                            }
                        }
                    }
                    create(
                        "Ultracraft Client Alt",
                        Application::class.java
                    ) {                       // Create new run configuration "MyApp" that will run class foo.App
                        jvmArgs =
                            "-Xmx4g -Dfabric.skipMcProvider=true -Dfabric.dli.config=${launchFile.path} -Dfabric.dli.env=CLIENT -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -Dfabric.zipfs.use_temp_file=false"
                        mainClass = "net.fabricmc.devlaunchinjector.Main"
                        moduleName = idea.module.name + ".main"
                        workingDirectory = "$projectDir/run/client/alt/"
                        programParameters = "--gameDir=."
                        beforeRun {
                            create("Clear Quilt Cache", GradleTask::class.java) {
                                this.task = tasks.named("clearClientAltQuiltCache").get()
                            }
                        }
                    }
                    create(
                        "Ultracraft Server",
                        Application::class.java
                    ) {                       // Create new run configuration "MyApp" that will run class foo.App
                        jvmArgs =
                            "-Xmx4g -Dfabric.skipMcProvider=true -Dfabric.dli.config=${launchFile.path} -Dfabric.dli.env=SERVER -Dfabric.dli.main=net.fabricmc.loader.impl.launch.knot.KnotClient -Dfabric.zipfs.use_temp_file=false"
                        mainClass = "net.fabricmc.devlaunchinjector.Main"
                        moduleName = idea.module.name + ".main"
                        workingDirectory = "$projectDir/run/server/"
                        programParameters = "--gameDir=."
                        beforeRun {
                            create("Clear Quilt Cache", GradleTask::class.java) {
                                this.task = tasks.named("clearServerQuiltCache").get()
                            }
                        }
                    }
                }
            }
        }
    }
    rootProject.idea {
        module {
            isDownloadJavadoc = true
            isDownloadSources = true
        }
    }
}

beforeEvaluate {
    setupIdea()
}

publishing {
    publications {
        create("mavenKotlin", MavenPublication::class) {
            //noinspection GrUnresolvedAccess
            from(components["kotlin"])
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/Ultreon/ultracraft-kotlin")
            credentials {
                username = (project.findProperty("gpr.user") ?: System.getenv("USERNAME")) as String
                password = (project.findProperty("gpr.key") ?: System.getenv("TOKEN")) as String
            }
        }
    }
}

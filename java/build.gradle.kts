plugins {
    `java-library`
    id("com.github.gmazzo.buildconfig") version "3.0.3"
}

group = "com.github.furrrlo"

java {
    sourceCompatibility = JavaVersion.VERSION_16
    targetCompatibility = JavaVersion.VERSION_16
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

val binariesResourcesLocation = "/${group.toString().replace('.', '/')}/winelevator/bin/"
buildConfig {
    className("WinElevatorConstants")
    packageName("$group.winelevator")
    useJavaOutput()

    buildConfigField("String", "VERSION", "\"${project.version}\"")
    buildConfigField("String", "BINARIES_LOCATION", "\"${binariesResourcesLocation}\"")
    buildConfigField("String", "LAUNCHER_BINARY", "\"launcher.exe\"")
    buildConfigField("String[]", "BINARIES", "new String[] { LAUNCHER_BINARY, \"elevator.exe\" }")
    buildConfigField("String", "TEMP_BINARY_PREFIX", "\"winelevator\"")
}

repositories {
    mavenCentral()
}

dependencies {
    val junit = "5.8.1"
    testImplementation("org.junit.jupiter:junit-jupiter-api:$junit")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:$junit")
    testImplementation("org.junit.jupiter:junit-jupiter-params:$junit")
}

tasks.processResources {
    // x86
    from(project(":elevator").tasks
        .named<LinkExecutable>("linkReleaseX86")
        .map { it.linkedFile }) { rename(".*", "${binariesResourcesLocation}win32-x86/elevator.exe") }
    from(project(":launcher").tasks
        .named<LinkExecutable>("linkReleaseX86")
        .map { it.linkedFile }) { rename(".*", "${binariesResourcesLocation}win32-x86/launcher.exe") }
    // x86-64
    from(project(":elevator").tasks
        .named<LinkExecutable>("linkReleaseX86-64")
        .map { it.linkedFile }) { rename(".*", "${binariesResourcesLocation}win32-amd64/elevator.exe") }
    from(project(":launcher").tasks
        .named<LinkExecutable>("linkReleaseX86-64")
        .map { it.linkedFile }) { rename(".*", "${binariesResourcesLocation}win32-amd64/launcher.exe") }
}

tasks.test {
    useJUnitPlatform()
}

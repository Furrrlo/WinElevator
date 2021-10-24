import org.gradle.kotlin.dsl.execution.ProgramText.Companion.from
import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.ToolType

version = "1.0.0.1"
ext {
    set("productName", "IntelliJ Platform")
    set("companyName", "JetBrains s.r.o.")
    set("description", "Part of elevator/launcher UAC IntelliJ kit. ")
    set("copyright", "Copyright (C) 2017 JetBrains s.r.o.")
    set("iconPath", "..\\\\jb.ico")
}

tasks.register<Copy>("install") {
    group = "build"

    into(File(buildDir, "install"))
    // x86
    from(project(":elevator").tasks
        .named<LinkExecutable>("linkReleaseX86")
        .map { it.linkedFile }) { rename(".*", "x86/elevator.exe") }
    from(project(":launcher").tasks
        .named<LinkExecutable>("linkReleaseX86")
        .map { it.linkedFile }) { rename(".*", "x86/launcher.exe") }
    // x86-64
    from(project(":elevator").tasks
        .named<LinkExecutable>("linkReleaseX86-64")
        .map { it.linkedFile }) { rename(".*", "x86-64/elevator.exe") }
    from(project(":launcher").tasks
        .named<LinkExecutable>("linkReleaseX86-64")
        .map { it.linkedFile }) { rename(".*", "x86-64/launcher.exe") }
}

subprojects {
    version = rootProject.version

    if(project.name != "elevator" && project.name != "launcher")
        return@subprojects;

    apply(plugin = "cpp-application")
    apply(plugin = "windows-resources")

    val srcDir = when(project.name) {
        "elevator" -> rootProject.file("elevator")
        "launcher" -> rootProject.file("launcher")
        else -> AssertionError("Invalid subproject ${project.name}")
    }

    extensions.configure<CppApplication>("application") {
        val application = this

        val machines = extensions.getByName("machines") as TargetMachineFactory
        targetMachines.set(listOf(machines.windows.x86, machines.windows.x86_64))

        source.from(rootProject.fileTree(srcDir) { include(
            "**/*.cpp", "**/*.c", "**/*.cc", "**/*.cxx", "def", "**/*.odl",
            "**/*.idl", "**/*.hpj", "**/*.bat", "**/*.asm", "**/*.asmx") })
        privateHeaders.from(srcDir)
        privateHeaders.from(rootProject.file("elevShared"))

        binaries.whenElementFinalized {
            if(this !is CppExecutable)
                return@whenElementFinalized

            val binary = this
            // https://github.com/gradle/native-samples/blob/49caeb5c31ba0b412239eb77564755c0eabeab82/c/application/build.gradle
            compileTask.get().apply {
                source.from(application.source)

                when(binary.toolChain) {
                    is VisualCpp -> compilerArgs.set(listOf("/TC"))
                    is GccCompatibleToolChain -> compilerArgs.set(listOf("-x", "c", "-std=c11"))
                }

                macros["_WINDOWS"] = null
                if(binary.targetMachine.architecture.name == MachineArchitecture.X86)
                    macros["WIN32"] = null

                if(binary.isDebuggable)
                    macros["_DEBUG"] = null
                else
                    macros["NDEBUG"] = null

                macros["UNICODE"] = null
                macros["_UNICODE"] = null
            }

            linkTask.get().apply {
                when(binary.toolChain) {
                    is GccCompatibleToolChain -> linkerArgs.set(listOf("-nodefaultlibs", "-lc"))
                }
            }

            // https://github.com/gradle/native-samples/blob/49caeb5c31ba0b412239eb77564755c0eabeab82/cpp/windows-resources/build.gradle
            val processResources = project.tasks.register<Sync>("processResources${name.capitalize()}") {
                from(rootProject.fileTree(srcDir) { include("**/*.rc") })
                into(File(buildDir, "processed-windows-resources"))

                filteringCharset = "UTF-16LE"
                filter(
                    org.apache.tools.ant.filters.ReplaceTokens::class,
                    "tokens" to java.util.Hashtable(mapOf(
                        "VERSION" to version.toString(),
                        "COMMA_VERSION" to version.toString().replace('.', ','),
                        "PRODUCT_NAME" to rootProject.ext["productName"].toString(),
                        "COMPANY_NAME" to rootProject.ext["companyName"].toString(),
                        "DESCRIPTION" to rootProject.ext["description"].toString(),
                        "COPYRIGHT" to rootProject.ext["copyright"].toString(),
                        "ICON_PATH" to rootProject.ext["iconPath"].toString(),
                    ))
                )
            }

            val compileResources = project.tasks.register<WindowsResourceCompile>("compileResources${name.capitalize()}") {
                targetPlatform.set(binary.compileTask.map { it.targetPlatform.get() })
                toolChain.set(binary.toolChain)

                includes.from(srcDir)
                // https://github.com/gradle/gradle/issues/3662
                // https://github.com/gradle/gradle/blob/5ec3f672ed600a86280be490395d70b7bc634862/subprojects/language-native/src/main/java/org/gradle/language/rc/plugins/internal/WindowsResourcesCompileTaskConfig.java#L73
                includes.from(toolChain
                    .map { it as NativeToolChainInternal }
                    .map { it.select(targetPlatform.get() as NativePlatformInternal) }
                    .map { it.getSystemLibraries(ToolType.WINDOW_RESOURCES_COMPILER).includeDirs })

                source.from(processResources.map { fileTree(it.destinationDir) })
                outputDir = File(project.buildDir, "windows-resources/$name")

                compilerArgs.addAll(toolChain.map { toolChain ->
                    if (toolChain is VisualCpp) listOf("/v")
                    else listOf()
                })
            }

            linkTask.get().apply {
                source.from(compileResources.map { fileTree(it.outputDir) { include(listOf("**/*.res", "**/*.obj")) } })

                linkerArgs.addAll(when(binary.toolChain) {
                    is VisualCpp -> listOf("user32.lib")
                    else -> listOf()
                })
            }
        }
    }
}

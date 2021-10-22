import org.gradle.nativeplatform.platform.internal.NativePlatformInternal
import org.gradle.nativeplatform.toolchain.internal.NativeToolChainInternal
import org.gradle.nativeplatform.toolchain.internal.ToolType

version = "1.0.0.1"

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
    apply(plugin = "cpp-application")
    apply(plugin = "windows-resources")

    version = rootProject.version

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
                when(this@whenElementFinalized.toolChain) {
                    is GccCompatibleToolChain -> linkerArgs.set(listOf("-nodefaultlibs", "-lc"))
                }
            }

            // https://github.com/gradle/native-samples/blob/49caeb5c31ba0b412239eb77564755c0eabeab82/cpp/windows-resources/build.gradle
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

                source.from(rootProject.fileTree(srcDir) { include("**/*.rc") })
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
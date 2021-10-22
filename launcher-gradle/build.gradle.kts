application {
    binaries.configureEach(CppExecutable::class.java) {
        val compileTask = compileTask.get()
        if(toolChain is VisualCpp)
            compileTask.compilerArgs.add(if(!isDebuggable) "/MT" else "/MTd")

        val linkTask = linkTask.get()
        if(toolChain is VisualCpp)
            linkTask.linkerArgs.addAll(
                "/SUBSYSTEM:CONSOLE",
                "/MANIFEST:EMBED", "/MANIFESTUAC:level='asInvoker' uiAccess='false'",
                "advapi32.lib", "shell32.lib")
    }
}

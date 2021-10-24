rootProject.name = "WinElevator"

include(":elevator")
project(":elevator").projectDir = File(rootProject.projectDir, "elevator-gradle")

include(":launcher")
project(":launcher").projectDir = File(rootProject.projectDir, "launcher-gradle")

include(":java")
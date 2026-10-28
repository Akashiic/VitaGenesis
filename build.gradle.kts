plugins {
    id("com.gtnewhorizons.gtnhconvention")
}

// Headless verification: ./gradlew runServer -Pvita.selftest=true (see tools/selftest.sh)
if (project.findProperty("vita.selftest") == "true") {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runServer")) {
            systemProperty("vitagenesis.selftest", "true")
            systemProperty("vitagenesis.selftest.exit", "true")
            systemProperty("fml.queryResult", "confirm")
            systemProperty("vitagenesis.selftest.phase", project.findProperty("vita.phase") ?: "full")
        }
    }
}

// Headless client verification (needs a display, e.g. xvfb-run): ./gradlew runClient21 -Pvita.clienttest=true
if (project.findProperty("vita.clienttest") == "true") {
    tasks.withType<JavaExec>().configureEach {
        if (name.startsWith("runClient")) {
            systemProperty("vitagenesis.clienttest", "true")
            systemProperty("fml.queryResult", "confirm")
        }
    }
}

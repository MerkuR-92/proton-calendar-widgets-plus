rootProject.name = "ProtonCalendar"

plugins {
    id("me.proton.core.gradle-plugins.include-core-build") version "1.1.2"
}

includeCoreBuild {
    branch.set("main")
    includeBuild("gopenpgp")
}

include(":app")
include(":week-view-core")

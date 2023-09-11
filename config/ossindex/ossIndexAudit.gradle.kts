buildscript {
    fun readExclusions(): Array<String> {
        return rootProject.file("config/ossindex/exclusions.txt").readLines()
                .stream()
                .toList()
                .filter { it.isNotBlank() }
                .toTypedArray()
    }

    extra.apply {
        set("ossIndexExclusions", readExclusions())
    }
}

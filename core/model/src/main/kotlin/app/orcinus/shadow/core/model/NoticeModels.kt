package app.orcinus.shadow.core.model

/** The build the user runs, as its About page presents it. */
data class AppInfo(
    val name: String,
    val version: String,
    /** The OrcaSlicer release whose engine and profiles the build bundles. */
    val orcaRelease: String,
    /** Where the source code of this build is published; null for a build without a public source. */
    val sourceUrl: String?,
    /** The license the app is distributed under. */
    val license: LicenseId,
)

@JvmInline
value class LicenseId(val value: String)

data class License(
    val id: LicenseId,
    val name: String,
    val url: String?,
    /** Full text; null when only the name and link are known. */
    val text: String?,
)

@JvmInline
value class ComponentId(val value: String)

/** A library, engine, or font the app is built from, with the licenses it is used under. */
data class ThirdPartyComponent(
    val id: ComponentId,
    val name: String,
    val version: String?,
    val description: String?,
    val website: String?,
    val licenses: List<License>,
)

package com.compagnon.courses.feature.about

/** Where a component is shipped: both apps, or one of them. */
enum class NoticePlatform {
    ALL,
    ANDROID,
    IOS,
}

/** An open-source component of the apps, under the license [licenseId] (see [OpenSourceNotices.licenses]). */
data class OpenSourceComponent(
    val name: String,
    val owner: String,
    val licenseId: String,
    val platform: NoticePlatform,
)

/** The full text of a license, as it must accompany the components that use it. */
data class LicenseText(
    val id: String,
    val title: String,
    val text: String,
)

/** A license and the components of one app that use it, in the order of [OpenSourceNotices.licenses]. */
data class LicenseGroup(
    val license: LicenseText,
    val components: List<OpenSourceComponent>,
)

/** The notices shown by the app running on [platform] (`ANDROID` or `IOS`): its components, grouped by license. */
fun OpenSourceNotices.groupsFor(platform: NoticePlatform): List<LicenseGroup> {
    val shipped = components.filter { it.platform == NoticePlatform.ALL || it.platform == platform }
    return licenses.mapNotNull { license ->
        shipped.filter { it.licenseId == license.id }
            .takeIf { it.isNotEmpty() }
            ?.let { LicenseGroup(license, it) }
    }
}

/**
 * The public links of « À propos ». Empty until they exist: the app then hides the matching row. Fill them in before
 * publishing (docs/PUBLICATION.md): the stores require a privacy policy URL.
 */
object AboutLinks {
    /** Public page of the privacy policy (store/privacy-policy.md, published). */
    const val PRIVACY_POLICY_URL: String = ""

    /** Contact address, opened with `mailto:`. */
    const val SUPPORT_EMAIL: String = ""
}

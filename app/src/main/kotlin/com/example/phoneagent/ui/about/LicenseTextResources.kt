package com.example.phoneagent.ui.about

import android.content.res.Resources
import com.example.phoneagent.R

internal object LicenseTextResources {
    val textResourceIds =
        mapOf(
            LicenseKind.APACHE_2_0 to R.raw.license_apache_2_0,
            LicenseKind.OFL_1_1 to R.raw.license_ofl_1_1,
            LicenseKind.BSD_3_CLAUSE to R.raw.license_bsd_3_clause,
        )

    val nameResourceIds =
        mapOf(
            LicenseKind.APACHE_2_0 to R.string.license_name_apache_2_0,
            LicenseKind.OFL_1_1 to R.string.license_name_ofl_1_1,
            LicenseKind.BSD_3_CLAUSE to R.string.license_name_bsd_3_clause,
        )

    fun readAll(resources: Resources): Map<LicenseKind, String> =
        textResourceIds.mapValues { (_, resourceId) ->
            resources.openRawResource(resourceId).bufferedReader().use { it.readText() }
        }
}

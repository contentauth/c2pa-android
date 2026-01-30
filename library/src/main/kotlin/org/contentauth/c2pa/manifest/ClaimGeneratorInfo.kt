/*
This file is licensed to you under the Apache License, Version 2.0
(http://www.apache.org/licenses/LICENSE-2.0) or the MIT license
(http://opensource.org/licenses/MIT), at your option.

Unless required by applicable law or agreed to in writing, this software is
distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR REPRESENTATIONS OF
ANY KIND, either express or implied. See the LICENSE-MIT and LICENSE-APACHE
files for the specific language governing permissions and limitations under
each license.
*/

package org.contentauth.c2pa.manifest

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Information about the software that created the manifest claim.
 *
 * @property name The name of the software or tool.
 * @property version The version of the software.
 * @property operatingSystem The operating system the software is running on.
 * @property icon An optional icon for the software.
 * @see ManifestDefinition
 */
@Serializable
data class ClaimGeneratorInfo(
    val name: String,
    val version: String? = null,
    @SerialName("operating_system")
    val operatingSystem: String? = null,
    val icon: UriOrResource? = null,
) {
    companion object {
        /**
         * Creates a ClaimGeneratorInfo using the current app's information.
         *
         * @param context The Android context to get app info from.
         * @param name Optional override for the app name.
         * @return ClaimGeneratorInfo populated with app details.
         */
        fun fromContext(
            context: Context,
            name: String? = null,
        ): ClaimGeneratorInfo {
            val packageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        PackageManager.PackageInfoFlags.of(0),
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                null
            }

            val appName = name ?: try {
                context.applicationInfo.loadLabel(context.packageManager).toString()
            } catch (e: Exception) {
                context.packageName
            }

            val appVersion = packageInfo?.versionName

            return ClaimGeneratorInfo(
                name = appName,
                version = appVersion,
                operatingSystem = operatingSystem,
            )
        }

        /**
         * The operating system string for the current device.
         */
        val operatingSystem: String
            get() = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
    }
}

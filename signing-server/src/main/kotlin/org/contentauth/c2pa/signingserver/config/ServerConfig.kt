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

package org.contentauth.c2pa.signingserver.config

data class ServerConfig(
    val port: Int = 8080,
    val host: String = "0.0.0.0",
    val maxRequestSize: Long = 52428800L, // 50MB
)

data class CertificateConfig(
    val rootCAValidityDays: Int = 3650, // 10 years
    val intermediateCAValidityDays: Int = 1825, // 5 years
    val endEntityValidityDays: Int = 365, // 1 year
    val tempCertValidityDays: Int = 1, // 1 day
)

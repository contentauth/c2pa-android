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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Validation status codes as defined in the C2PA specification.
 *
 * These codes indicate the result of various validation checks performed on manifests.
 *
 * @see ValidationStatus
 */
@Serializable
enum class ValidationStatusCode {
    // Success codes
    @SerialName("claimSignature.validated")
    CLAIM_SIGNATURE_VALIDATED,

    @SerialName("signingCredential.trusted")
    SIGNING_CREDENTIAL_TRUSTED,

    @SerialName("timeStamp.trusted")
    TIMESTAMP_TRUSTED,

    @SerialName("assertion.dataHash.match")
    ASSERTION_DATA_HASH_MATCH,

    @SerialName("assertion.bmffHash.match")
    ASSERTION_BMFF_HASH_MATCH,

    @SerialName("assertion.boxesHash.match")
    ASSERTION_BOXES_HASH_MATCH,

    @SerialName("assertion.collectionHash.match")
    ASSERTION_COLLECTION_HASH_MATCH,

    @SerialName("assertion.hashedURI.match")
    ASSERTION_HASHED_URI_MATCH,

    @SerialName("assertion.ingredientMatch")
    ASSERTION_INGREDIENT_MATCH,

    @SerialName("assertion.accessible")
    ASSERTION_ACCESSIBLE,

    // Failure codes
    @SerialName("assertion.dataHash.mismatch")
    ASSERTION_DATA_HASH_MISMATCH,

    @SerialName("assertion.bmffHash.mismatch")
    ASSERTION_BMFF_HASH_MISMATCH,

    @SerialName("assertion.boxesHash.mismatch")
    ASSERTION_BOXES_HASH_MISMATCH,

    @SerialName("assertion.collectionHash.mismatch")
    ASSERTION_COLLECTION_HASH_MISMATCH,

    @SerialName("assertion.hashedURI.mismatch")
    ASSERTION_HASHED_URI_MISMATCH,

    @SerialName("assertion.missing")
    ASSERTION_MISSING,

    @SerialName("assertion.multipleHardBindings")
    ASSERTION_MULTIPLE_HARD_BINDINGS,

    @SerialName("assertion.undeclaredHashedURI")
    ASSERTION_UNDECLARED_HASHED_URI,

    @SerialName("assertion.requiredMissing")
    ASSERTION_REQUIRED_MISSING,

    @SerialName("assertion.inaccessible")
    ASSERTION_INACCESSIBLE,

    @SerialName("assertion.cloudData.hardBinding")
    ASSERTION_CLOUD_DATA_HARD_BINDING,

    @SerialName("assertion.cloudData.actions")
    ASSERTION_CLOUD_DATA_ACTIONS,

    @SerialName("assertion.cloudData.mismatch")
    ASSERTION_CLOUD_DATA_MISMATCH,

    @SerialName("assertion.json.invalid")
    ASSERTION_JSON_INVALID,

    @SerialName("assertion.cbor.invalid")
    ASSERTION_CBOR_INVALID,

    @SerialName("assertion.action.ingredientMismatch")
    ASSERTION_ACTION_INGREDIENT_MISMATCH,

    @SerialName("assertion.action.missing")
    ASSERTION_ACTION_MISSING,

    @SerialName("assertion.action.redactionMissing")
    ASSERTION_ACTION_REDACTION_MISSING,

    @SerialName("assertion.selfRedacted")
    ASSERTION_SELF_REDACTED,

    @SerialName("claim.missing")
    CLAIM_MISSING,

    @SerialName("claim.multiple")
    CLAIM_MULTIPLE,

    @SerialName("claim.hardBindings.missing")
    CLAIM_HARD_BINDINGS_MISSING,

    @SerialName("claim.required.missing")
    CLAIM_REQUIRED_MISSING,

    @SerialName("claim.cbor.invalid")
    CLAIM_CBOR_INVALID,

    @SerialName("claimSignature.mismatch")
    CLAIM_SIGNATURE_MISMATCH,

    @SerialName("claimSignature.missing")
    CLAIM_SIGNATURE_MISSING,

    @SerialName("manifest.missing")
    MANIFEST_MISSING,

    @SerialName("manifest.multipleParents")
    MANIFEST_MULTIPLE_PARENTS,

    @SerialName("manifest.updateWrongParents")
    MANIFEST_UPDATE_WRONG_PARENTS,

    @SerialName("manifest.inaccessible")
    MANIFEST_INACCESSIBLE,

    @SerialName("ingredient.hashedURI.mismatch")
    INGREDIENT_HASHED_URI_MISMATCH,

    @SerialName("signingCredential.untrusted")
    SIGNING_CREDENTIAL_UNTRUSTED,

    @SerialName("signingCredential.invalid")
    SIGNING_CREDENTIAL_INVALID,

    @SerialName("signingCredential.revoked")
    SIGNING_CREDENTIAL_REVOKED,

    @SerialName("signingCredential.expired")
    SIGNING_CREDENTIAL_EXPIRED,

    @SerialName("timeStamp.mismatch")
    TIMESTAMP_MISMATCH,

    @SerialName("timeStamp.untrusted")
    TIMESTAMP_UNTRUSTED,

    @SerialName("timeStamp.outsideValidity")
    TIMESTAMP_OUTSIDE_VALIDITY,

    @SerialName("algorithm.unsupported")
    ALGORITHM_UNSUPPORTED,

    @SerialName("general.error")
    GENERAL_ERROR,

    // Additional status codes
    @SerialName("assertion.redactedUriMismatch")
    ASSERTION_REDACTED_URI_MISMATCH,

    @SerialName("assertion.notRedactable")
    ASSERTION_NOT_REDACTABLE,
}

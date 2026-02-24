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
 * Validation status codes as defined in the C2PA 2.3 specification (Section 15).
 *
 * These codes indicate the result of various validation checks performed on manifests.
 * Codes are organized into three categories: success, informational, and failure.
 *
 * @see ValidationStatus
 */
@Serializable
enum class ValidationStatusCode {

    // --- Success codes ---

    /** The assertion is accessible for validation. */
    @SerialName("assertion.accessible")
    ASSERTION_ACCESSIBLE,

    /** The alternative content representation hash matches. */
    @SerialName("assertion.alternativeContentRepresentation.match")
    ASSERTION_ALT_CONTENT_MATCH,

    /** The BMFF hash matches the asset. */
    @SerialName("assertion.bmffHash.match")
    ASSERTION_BMFF_HASH_MATCH,

    /** The box hash matches the asset. */
    @SerialName("assertion.boxesHash.match")
    ASSERTION_BOXES_HASH_MATCH,

    /** The collection hash matches. */
    @SerialName("assertion.collectionHash.match")
    ASSERTION_COLLECTION_HASH_MATCH,

    /** The data hash matches the asset. */
    @SerialName("assertion.dataHash.match")
    ASSERTION_DATA_HASH_MATCH,

    /** The hashed URI reference matches. */
    @SerialName("assertion.hashedURI.match")
    ASSERTION_HASHED_URI_MATCH,

    /** The multi-asset hash matches. */
    @SerialName("assertion.multiAssetHash.match")
    ASSERTION_MULTI_ASSET_HASH_MATCH,

    /** The claim signature is within its validity period. */
    @SerialName("claimSignature.insideValidity")
    CLAIM_SIGNATURE_INSIDE_VALIDITY,

    /** The claim signature has been validated. */
    @SerialName("claimSignature.validated")
    CLAIM_SIGNATURE_VALIDATED,

    /** The ingredient's claim signature has been validated. */
    @SerialName("ingredient.claimSignature.validated")
    INGREDIENT_CLAIM_SIGNATURE_VALIDATED,

    /** The ingredient's manifest has been validated. */
    @SerialName("ingredient.manifest.validated")
    INGREDIENT_MANIFEST_VALIDATED,

    /** The signing credential's OCSP status is not revoked. */
    @SerialName("signingCredential.ocsp.notRevoked")
    SIGNING_CREDENTIAL_OCSP_NOT_REVOKED,

    /** The signing credential is trusted. */
    @SerialName("signingCredential.trusted")
    SIGNING_CREDENTIAL_TRUSTED,

    /** The timestamp is trusted. */
    @SerialName("timeStamp.trusted")
    TIMESTAMP_TRUSTED,

    /** The timestamp has been validated. */
    @SerialName("timeStamp.validated")
    TIMESTAMP_VALIDATED,

    // --- Informational codes ---

    /** The algorithm used is deprecated. */
    @SerialName("algorithm.deprecated")
    ALGORITHM_DEPRECATED,

    /** The BMFF hash has additional exclusions present. */
    @SerialName("assertion.bmffHash.additionalExclusionsPresent")
    ASSERTION_BMFF_HASH_ADDITIONAL_EXCLUSIONS,

    /** The box hash has additional exclusions present. */
    @SerialName("assertion.boxesHash.additionalExclusionsPresent")
    ASSERTION_BOXES_HASH_ADDITIONAL_EXCLUSIONS,

    /** The data hash has additional exclusions present. */
    @SerialName("assertion.dataHash.additionalExclusionsPresent")
    ASSERTION_DATA_HASH_ADDITIONAL_EXCLUSIONS,

    /** The ingredient has unknown provenance. */
    @SerialName("ingredient.unknownProvenance")
    INGREDIENT_UNKNOWN_PROVENANCE,

    /** The OCSP responder for the signing credential is inaccessible. */
    @SerialName("signingCredential.ocsp.inaccessible")
    SIGNING_CREDENTIAL_OCSP_INACCESSIBLE,

    /** OCSP checking was skipped for the signing credential. */
    @SerialName("signingCredential.ocsp.skipped")
    SIGNING_CREDENTIAL_OCSP_SKIPPED,

    /** The OCSP status of the signing credential is unknown. */
    @SerialName("signingCredential.ocsp.unknown")
    SIGNING_CREDENTIAL_OCSP_UNKNOWN,

    /** The time of signing is within the credential validity period. */
    @SerialName("timeOfSigning.insideValidity")
    TIME_OF_SIGNING_INSIDE_VALIDITY,

    /** The time of signing is outside the credential validity period. */
    @SerialName("timeOfSigning.outsideValidity")
    TIME_OF_SIGNING_OUTSIDE_VALIDITY,

    /** The timestamp credential is invalid. */
    @SerialName("timeStamp.credentialInvalid")
    TIMESTAMP_CREDENTIAL_INVALID,

    /** The timestamp is malformed. */
    @SerialName("timeStamp.malformed")
    TIMESTAMP_MALFORMED,

    /** The timestamp does not match. */
    @SerialName("timeStamp.mismatch")
    TIMESTAMP_MISMATCH,

    /** The timestamp is outside its validity period. */
    @SerialName("timeStamp.outsideValidity")
    TIMESTAMP_OUTSIDE_VALIDITY,

    /** The timestamp is untrusted. */
    @SerialName("timeStamp.untrusted")
    TIMESTAMP_UNTRUSTED,

    // --- Failure codes ---

    /** The algorithm used is unsupported. */
    @SerialName("algorithm.unsupported")
    ALGORITHM_UNSUPPORTED,

    /** The action assertion has an ingredient mismatch. */
    @SerialName("assertion.action.ingredientMismatch")
    ASSERTION_ACTION_INGREDIENT_MISMATCH,

    /** The action assertion is malformed. */
    @SerialName("assertion.action.malformed")
    ASSERTION_ACTION_MALFORMED,

    /** The action assertion has missing information. */
    @SerialName("assertion.action.missing")
    ASSERTION_ACTION_MISSING,

    /** An action assertion was redacted. */
    @SerialName("assertion.action.redacted")
    ASSERTION_ACTION_REDACTED,

    /** The action assertion has a redaction mismatch. */
    @SerialName("assertion.action.redactionMismatch")
    ASSERTION_ACTION_REDACTION_MISMATCH,

    /** The action assertion is missing a required soft binding. */
    @SerialName("assertion.action.softBindingMissing")
    ASSERTION_ACTION_SOFT_BINDING_MISSING,

    /** The alternative content representation is malformed. */
    @SerialName("assertion.alternativeContentRepresentation.malformed")
    ASSERTION_ALT_CONTENT_MALFORMED,

    /** The alternative content representation hash does not match. */
    @SerialName("assertion.alternativeContentRepresentation.hashMismatch")
    ASSERTION_ALT_CONTENT_HASH_MISMATCH,

    /** The alternative content representation is missing. */
    @SerialName("assertion.alternativeContentRepresentation.missing")
    ASSERTION_ALT_CONTENT_MISSING,

    /** The BMFF hash is malformed. */
    @SerialName("assertion.bmffHash.malformed")
    ASSERTION_BMFF_HASH_MALFORMED,

    /** The BMFF hash does not match. */
    @SerialName("assertion.bmffHash.mismatch")
    ASSERTION_BMFF_HASH_MISMATCH,

    /** The box hash is malformed. */
    @SerialName("assertion.boxesHash.malformed")
    ASSERTION_BOXES_HASH_MALFORMED,

    /** The box hash does not match. */
    @SerialName("assertion.boxesHash.mismatch")
    ASSERTION_BOXES_HASH_MISMATCH,

    /** An unknown box was encountered in the box hash. */
    @SerialName("assertion.boxesHash.unknownBox")
    ASSERTION_BOXES_HASH_UNKNOWN_BOX,

    /** The CBOR assertion data is invalid. */
    @SerialName("assertion.cbor.invalid")
    ASSERTION_CBOR_INVALID,

    /** Cloud data assertion has incorrect actions reference. */
    @SerialName("assertion.cloud-data.actions")
    ASSERTION_CLOUD_DATA_ACTIONS,

    /** Cloud data assertion has incorrect hard binding reference. */
    @SerialName("assertion.cloud-data.hardBinding")
    ASSERTION_CLOUD_DATA_HARD_BINDING,

    /** Cloud data assertion label does not match. */
    @SerialName("assertion.cloud-data.labelMismatch")
    ASSERTION_CLOUD_DATA_LABEL_MISMATCH,

    /** Cloud data assertion is malformed. */
    @SerialName("assertion.cloud-data.malformed")
    ASSERTION_CLOUD_DATA_MALFORMED,

    /** The collection hash has an incorrect file count. */
    @SerialName("assertion.collectionHash.incorrectFileCount")
    ASSERTION_COLLECTION_HASH_INCORRECT_FILE_COUNT,

    /** The collection hash has an invalid URI. */
    @SerialName("assertion.collectionHash.invalidURI")
    ASSERTION_COLLECTION_HASH_INVALID_URI,

    /** The collection hash is malformed. */
    @SerialName("assertion.collectionHash.malformed")
    ASSERTION_COLLECTION_HASH_MALFORMED,

    /** The collection hash does not match. */
    @SerialName("assertion.collectionHash.mismatch")
    ASSERTION_COLLECTION_HASH_MISMATCH,

    /** The data hash is malformed. */
    @SerialName("assertion.dataHash.malformed")
    ASSERTION_DATA_HASH_MALFORMED,

    /** The data hash does not match. */
    @SerialName("assertion.dataHash.mismatch")
    ASSERTION_DATA_HASH_MISMATCH,

    /** The external reference has incorrect actions. */
    @SerialName("assertion.external-reference.actions")
    ASSERTION_EXTERNAL_REFERENCE_ACTIONS,

    /** The external reference was created incorrectly. */
    @SerialName("assertion.external-reference.created")
    ASSERTION_EXTERNAL_REFERENCE_CREATED,

    /** The external reference has incorrect hard binding. */
    @SerialName("assertion.external-reference.hardBinding")
    ASSERTION_EXTERNAL_REFERENCE_HARD_BINDING,

    /** The external reference is malformed. */
    @SerialName("assertion.external-reference.malformed")
    ASSERTION_EXTERNAL_REFERENCE_MALFORMED,

    /** A hard binding assertion was redacted. */
    @SerialName("assertion.hardBinding.redacted")
    ASSERTION_HARD_BINDING_REDACTED,

    /** The hashed URI does not match. */
    @SerialName("assertion.hashedURI.mismatch")
    ASSERTION_HASHED_URI_MISMATCH,

    /** The assertion is inaccessible. */
    @SerialName("assertion.inaccessible")
    ASSERTION_INACCESSIBLE,

    /** The ingredient assertion is malformed. */
    @SerialName("assertion.ingredient.malformed")
    ASSERTION_INGREDIENT_MALFORMED,

    /** The JSON assertion data is invalid. */
    @SerialName("assertion.json.invalid")
    ASSERTION_JSON_INVALID,

    /** The assertion is missing. */
    @SerialName("assertion.missing")
    ASSERTION_MISSING,

    /** The multi-asset hash is malformed. */
    @SerialName("assertion.multiAssetHash.malformed")
    ASSERTION_MULTI_ASSET_HASH_MALFORMED,

    /** The multi-asset hash has a missing part. */
    @SerialName("assertion.multiAssetHash.missingPart")
    ASSERTION_MULTI_ASSET_HASH_MISSING_PART,

    /** The multi-asset hash does not match. */
    @SerialName("assertion.multiAssetHash.mismatch")
    ASSERTION_MULTI_ASSET_HASH_MISMATCH,

    /** Multiple hard bindings were found. */
    @SerialName("assertion.multipleHardBindings")
    ASSERTION_MULTIPLE_HARD_BINDINGS,

    /** The assertion was not redacted as expected. */
    @SerialName("assertion.notRedacted")
    ASSERTION_NOT_REDACTED,

    /** The assertion is outside the manifest. */
    @SerialName("assertion.outsideManifest")
    ASSERTION_OUTSIDE_MANIFEST,

    /** An assertion attempted to redact itself. */
    @SerialName("assertion.selfRedacted")
    ASSERTION_SELF_REDACTED,

    /** The assertion timestamp is malformed. */
    @SerialName("assertion.timestamp.malformed")
    ASSERTION_TIMESTAMP_MALFORMED,

    /** An undeclared assertion was found. */
    @SerialName("assertion.undeclared")
    ASSERTION_UNDECLARED,

    /** The claim CBOR data is invalid. */
    @SerialName("claim.cbor.invalid")
    CLAIM_CBOR_INVALID,

    /** The claim is missing required hard bindings. */
    @SerialName("claim.hardBindings.missing")
    CLAIM_HARD_BINDINGS_MISSING,

    /** The claim is malformed. */
    @SerialName("claim.malformed")
    CLAIM_MALFORMED,

    /** The claim is missing. */
    @SerialName("claim.missing")
    CLAIM_MISSING,

    /** Multiple claims were found. */
    @SerialName("claim.multiple")
    CLAIM_MULTIPLE,

    /** The claim signature does not match. */
    @SerialName("claimSignature.mismatch")
    CLAIM_SIGNATURE_MISMATCH,

    /** The claim signature is missing. */
    @SerialName("claimSignature.missing")
    CLAIM_SIGNATURE_MISSING,

    /** The claim signature is outside its validity period. */
    @SerialName("claimSignature.outsideValidity")
    CLAIM_SIGNATURE_OUTSIDE_VALIDITY,

    /** A general error occurred. */
    @SerialName("general.error")
    GENERAL_ERROR,

    /** A hashed URI reference is missing. */
    @SerialName("hashedURI.missing")
    HASHED_URI_MISSING,

    /** A hashed URI reference does not match. */
    @SerialName("hashedURI.mismatch")
    HASHED_URI_MISMATCH,

    /** The ingredient's claim signature is missing. */
    @SerialName("ingredient.claimSignature.missing")
    INGREDIENT_CLAIM_SIGNATURE_MISSING,

    /** The ingredient's claim signature does not match. */
    @SerialName("ingredient.claimSignature.mismatch")
    INGREDIENT_CLAIM_SIGNATURE_MISMATCH,

    /** The ingredient's hashed URI does not match. */
    @SerialName("ingredient.hashedURI.mismatch")
    INGREDIENT_HASHED_URI_MISMATCH,

    /** The ingredient's manifest is missing. */
    @SerialName("ingredient.manifest.missing")
    INGREDIENT_MANIFEST_MISSING,

    /** The ingredient's manifest does not match. */
    @SerialName("ingredient.manifest.mismatch")
    INGREDIENT_MANIFEST_MISMATCH,

    /** A compressed manifest is invalid. */
    @SerialName("manifest.compressed.invalid")
    MANIFEST_COMPRESSED_INVALID,

    /** The manifest is inaccessible. */
    @SerialName("manifest.inaccessible")
    MANIFEST_INACCESSIBLE,

    /** The manifest is missing. */
    @SerialName("manifest.missing")
    MANIFEST_MISSING,

    /** The manifest has multiple parents. */
    @SerialName("manifest.multipleParents")
    MANIFEST_MULTIPLE_PARENTS,

    /** The manifest timestamp is invalid. */
    @SerialName("manifest.timestamp.invalid")
    MANIFEST_TIMESTAMP_INVALID,

    /** The manifest timestamp has wrong parents. */
    @SerialName("manifest.timestamp.wrongParents")
    MANIFEST_TIMESTAMP_WRONG_PARENTS,

    /** The manifest update is invalid. */
    @SerialName("manifest.update.invalid")
    MANIFEST_UPDATE_INVALID,

    /** The manifest update has wrong parents. */
    @SerialName("manifest.update.wrongParents")
    MANIFEST_UPDATE_WRONG_PARENTS,

    /** The signing credential is expired. */
    @SerialName("signingCredential.expired")
    SIGNING_CREDENTIAL_EXPIRED,

    /** The signing credential is invalid. */
    @SerialName("signingCredential.invalid")
    SIGNING_CREDENTIAL_INVALID,

    /** The signing credential has been revoked (via OCSP). */
    @SerialName("signingCredential.ocsp.revoked")
    SIGNING_CREDENTIAL_OCSP_REVOKED,

    /** The signing credential's OCSP revocation status (legacy code). */
    @SerialName("signingCredential.revoked")
    SIGNING_CREDENTIAL_REVOKED,

    /** The signing credential is untrusted. */
    @SerialName("signingCredential.untrusted")
    SIGNING_CREDENTIAL_UNTRUSTED,
}

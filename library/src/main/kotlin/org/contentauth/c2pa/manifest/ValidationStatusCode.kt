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

/**
 * Validation status codes that c2pa-rs reports, as defined by the C2PA specification and the
 * CAWG identity assertion specification.
 *
 * These codes indicate the result of various validation checks performed on manifests.
 * Codes are organized into three categories: success, informational, and failure.
 *
 * The set of codes is open-ended: a newer SDK or another validator can report a code that is
 * not listed here. [ValidationStatus.code] therefore holds the code as a string, and
 * [ValidationStatus.statusCode] resolves it to an entry of this enum when one exists.
 *
 * @property code The status code as it appears in manifest JSON.
 * @see ValidationStatus
 */
enum class ValidationStatusCode(val code: String) {

    // --- Success codes ---

    /** The assertion is accessible for validation. */
    ASSERTION_ACCESSIBLE("assertion.accessible"),

    /** The alternative content representation hash matches. */
    ASSERTION_ALT_CONTENT_MATCH("assertion.alternativeContentRepresentation.match"),

    /** The BMFF hash matches the asset. */
    ASSERTION_BMFF_HASH_MATCH("assertion.bmffHash.match"),

    /** The box hash matches the asset. */
    ASSERTION_BOXES_HASH_MATCH("assertion.boxesHash.match"),

    /** The collection hash matches. */
    ASSERTION_COLLECTION_HASH_MATCH("assertion.collectionHash.match"),

    /** The data hash matches the asset. */
    ASSERTION_DATA_HASH_MATCH("assertion.dataHash.match"),

    /** The hashed URI reference matches. */
    ASSERTION_HASHED_URI_MATCH("assertion.hashedURI.match"),

    /** The multi-asset hash matches. */
    ASSERTION_MULTI_ASSET_HASH_MATCH("assertion.multiAssetHash.match"),

    /** The claim signature is within its validity period. */
    CLAIM_SIGNATURE_INSIDE_VALIDITY("claimSignature.insideValidity"),

    /** The claim signature has been validated. */
    CLAIM_SIGNATURE_VALIDATED("claimSignature.validated"),

    /** The ingredient's claim signature has been validated. */
    INGREDIENT_CLAIM_SIGNATURE_VALIDATED("ingredient.claimSignature.validated"),

    /** The ingredient's manifest has been validated. */
    INGREDIENT_MANIFEST_VALIDATED("ingredient.manifest.validated"),

    /** The signing credential's OCSP status is not revoked. */
    SIGNING_CREDENTIAL_OCSP_NOT_REVOKED("signingCredential.ocsp.notRevoked"),

    /** The signing credential is trusted. */
    SIGNING_CREDENTIAL_TRUSTED("signingCredential.trusted"),

    /** The timestamp is trusted. */
    TIMESTAMP_TRUSTED("timeStamp.trusted"),

    /** The timestamp has been validated. */
    TIMESTAMP_VALIDATED("timeStamp.validated"),

    /** A CAWG identity claims aggregation credential was validated. */
    CAWG_ICA_CREDENTIAL_VALID("cawg.ica.credential_valid"),

    /** The CAWG identity claims aggregation time stamp was validated. */
    CAWG_ICA_TIME_STAMP_VALIDATED("cawg.ica.time_stamp.validated"),

    /** The CAWG identity assertion is well formed. */
    CAWG_IDENTITY_WELL_FORMED("cawg.identity.well-formed"),

    /** The CAWG identity assertion's X.509 signing certificate chains to a trust anchor. */
    CAWG_X509_CREDENTIAL_TRUSTED("cawg.x509.credential.trusted"),

    /** The CAWG identity assertion's X.509 signature was validated. */
    CAWG_X509_SIGNATURE_VALIDATED("cawg.x509.signature.validated"),

    // --- Informational codes ---

    /** The algorithm used is deprecated. */
    ALGORITHM_DEPRECATED("algorithm.deprecated"),

    /** The BMFF hash has additional exclusions present. */
    ASSERTION_BMFF_HASH_ADDITIONAL_EXCLUSIONS("assertion.bmffHash.additionalExclusionsPresent"),

    /** The box hash has additional exclusions present. */
    ASSERTION_BOXES_HASH_ADDITIONAL_EXCLUSIONS("assertion.boxesHash.additionalExclusionsPresent"),

    /** The data hash has additional exclusions present. */
    ASSERTION_DATA_HASH_ADDITIONAL_EXCLUSIONS("assertion.dataHash.additionalExclusionsPresent"),

    /** The ingredient has unknown provenance. */
    INGREDIENT_UNKNOWN_PROVENANCE("ingredient.unknownProvenance"),

    /** The OCSP responder for the signing credential is inaccessible. */
    SIGNING_CREDENTIAL_OCSP_INACCESSIBLE("signingCredential.ocsp.inaccessible"),

    /** OCSP checking was skipped for the signing credential. */
    SIGNING_CREDENTIAL_OCSP_SKIPPED("signingCredential.ocsp.skipped"),

    /** The OCSP status of the signing credential is unknown. */
    SIGNING_CREDENTIAL_OCSP_UNKNOWN("signingCredential.ocsp.unknown"),

    /** The time of signing is within the credential validity period. */
    TIME_OF_SIGNING_INSIDE_VALIDITY("timeOfSigning.insideValidity"),

    /** The time of signing is outside the credential validity period. */
    TIME_OF_SIGNING_OUTSIDE_VALIDITY("timeOfSigning.outsideValidity"),

    /** The timestamp credential is invalid. */
    TIMESTAMP_CREDENTIAL_INVALID("timeStamp.credentialInvalid"),

    /** The timestamp is malformed. */
    TIMESTAMP_MALFORMED("timeStamp.malformed"),

    /** The timestamp does not match. */
    TIMESTAMP_MISMATCH("timeStamp.mismatch"),

    /** The timestamp is outside its validity period. */
    TIMESTAMP_OUTSIDE_VALIDITY("timeStamp.outsideValidity"),

    /** The timestamp is untrusted. */
    TIMESTAMP_UNTRUSTED("timeStamp.untrusted"),

    /** The CAWG identity claims aggregation issuer is not on the trusted issuer list. */
    CAWG_ICA_UNTRUSTED_ISSUER("cawg.ica.untrusted_issuer"),

    /** The manifest cannot be interpreted by this version of the SDK. */
    MANIFEST_UNKNOWN_PROVENANCE("manifest.unknownProvenance"),

    // --- Failure codes ---

    /** The algorithm used is unsupported. */
    ALGORITHM_UNSUPPORTED("algorithm.unsupported"),

    /** The action assertion has an ingredient mismatch. */
    ASSERTION_ACTION_INGREDIENT_MISMATCH("assertion.action.ingredientMismatch"),

    /** The action assertion is malformed. */
    ASSERTION_ACTION_MALFORMED("assertion.action.malformed"),

    /** The action assertion has missing information. */
    ASSERTION_ACTION_MISSING("assertion.action.missing"),

    /** An action assertion was redacted. */
    ASSERTION_ACTION_REDACTED("assertion.action.redacted"),

    /** The action assertion has a redaction mismatch. */
    ASSERTION_ACTION_REDACTION_MISMATCH("assertion.action.redactionMismatch"),

    /** The action assertion is missing a required soft binding. */
    ASSERTION_ACTION_SOFT_BINDING_MISSING("assertion.action.softBindingMissing"),

    /** The alternative content representation is malformed. */
    ASSERTION_ALT_CONTENT_MALFORMED("assertion.alternativeContentRepresentation.malformed"),

    /** The alternative content representation hash does not match. */
    ASSERTION_ALT_CONTENT_HASH_MISMATCH("assertion.alternativeContentRepresentation.hashMismatch"),

    /** The alternative content representation is missing. */
    ASSERTION_ALT_CONTENT_MISSING("assertion.alternativeContentRepresentation.missing"),

    /** The BMFF hash is malformed. */
    ASSERTION_BMFF_HASH_MALFORMED("assertion.bmffHash.malformed"),

    /** The BMFF hash does not match. */
    ASSERTION_BMFF_HASH_MISMATCH("assertion.bmffHash.mismatch"),

    /** The box hash is malformed. */
    ASSERTION_BOXES_HASH_MALFORMED("assertion.boxesHash.malformed"),

    /** The box hash does not match. */
    ASSERTION_BOXES_HASH_MISMATCH("assertion.boxesHash.mismatch"),

    /** An unknown box was encountered in the box hash. */
    ASSERTION_BOXES_HASH_UNKNOWN_BOX("assertion.boxesHash.unknownBox"),

    /** The CBOR assertion data is invalid. */
    ASSERTION_CBOR_INVALID("assertion.cbor.invalid"),

    /** Cloud data assertion has incorrect actions reference. */
    ASSERTION_CLOUD_DATA_ACTIONS("assertion.cloud-data.actions"),

    /** Cloud data assertion has incorrect hard binding reference. */
    ASSERTION_CLOUD_DATA_HARD_BINDING("assertion.cloud-data.hardBinding"),

    /** Cloud data assertion label does not match. */
    ASSERTION_CLOUD_DATA_LABEL_MISMATCH("assertion.cloud-data.labelMismatch"),

    /** Cloud data assertion is malformed. */
    ASSERTION_CLOUD_DATA_MALFORMED("assertion.cloud-data.malformed"),

    /** The collection hash has an incorrect file count. */
    ASSERTION_COLLECTION_HASH_INCORRECT_FILE_COUNT("assertion.collectionHash.incorrectFileCount"),

    /** The collection hash has an invalid URI. */
    ASSERTION_COLLECTION_HASH_INVALID_URI("assertion.collectionHash.invalidURI"),

    /** The collection hash is malformed. */
    ASSERTION_COLLECTION_HASH_MALFORMED("assertion.collectionHash.malformed"),

    /** The collection hash does not match. */
    ASSERTION_COLLECTION_HASH_MISMATCH("assertion.collectionHash.mismatch"),

    /** The data hash is malformed. */
    ASSERTION_DATA_HASH_MALFORMED("assertion.dataHash.malformed"),

    /** The data hash does not match. */
    ASSERTION_DATA_HASH_MISMATCH("assertion.dataHash.mismatch"),

    /** The external reference has incorrect actions. */
    ASSERTION_EXTERNAL_REFERENCE_ACTIONS("assertion.external-reference.actions"),

    /** The external reference was created incorrectly. */
    ASSERTION_EXTERNAL_REFERENCE_CREATED("assertion.external-reference.created"),

    /** The external reference has incorrect hard binding. */
    ASSERTION_EXTERNAL_REFERENCE_HARD_BINDING("assertion.external-reference.hardBinding"),

    /** The external reference is malformed. */
    ASSERTION_EXTERNAL_REFERENCE_MALFORMED("assertion.external-reference.malformed"),

    /** A hard binding assertion was redacted. */
    ASSERTION_HARD_BINDING_REDACTED("assertion.hardBinding.redacted"),

    /** The hashed URI does not match. */
    ASSERTION_HASHED_URI_MISMATCH("assertion.hashedURI.mismatch"),

    /** The assertion is inaccessible. */
    ASSERTION_INACCESSIBLE("assertion.inaccessible"),

    /** The ingredient assertion is malformed. */
    ASSERTION_INGREDIENT_MALFORMED("assertion.ingredient.malformed"),

    /** The JSON assertion data is invalid. */
    ASSERTION_JSON_INVALID("assertion.json.invalid"),

    /** The assertion is missing. */
    ASSERTION_MISSING("assertion.missing"),

    /** The multi-asset hash is malformed. */
    ASSERTION_MULTI_ASSET_HASH_MALFORMED("assertion.multiAssetHash.malformed"),

    /** The multi-asset hash has a missing part. */
    ASSERTION_MULTI_ASSET_HASH_MISSING_PART("assertion.multiAssetHash.missingPart"),

    /** The multi-asset hash does not match. */
    ASSERTION_MULTI_ASSET_HASH_MISMATCH("assertion.multiAssetHash.mismatch"),

    /** Multiple hard bindings were found. */
    ASSERTION_MULTIPLE_HARD_BINDINGS("assertion.multipleHardBindings"),

    /** The assertion was not redacted as expected. */
    ASSERTION_NOT_REDACTED("assertion.notRedacted"),

    /** The assertion is outside the manifest. */
    ASSERTION_OUTSIDE_MANIFEST("assertion.outsideManifest"),

    /** An assertion attempted to redact itself. */
    ASSERTION_SELF_REDACTED("assertion.selfRedacted"),

    /** The assertion timestamp is malformed. */
    ASSERTION_TIMESTAMP_MALFORMED("assertion.timestamp.malformed"),

    /** An undeclared assertion was found. */
    ASSERTION_UNDECLARED("assertion.undeclared"),

    /** The claim CBOR data is invalid. */
    CLAIM_CBOR_INVALID("claim.cbor.invalid"),

    /** The claim is missing required hard bindings. */
    CLAIM_HARD_BINDINGS_MISSING("claim.hardBindings.missing"),

    /** The claim is malformed. */
    CLAIM_MALFORMED("claim.malformed"),

    /** The claim is missing. */
    CLAIM_MISSING("claim.missing"),

    /** Multiple claims were found. */
    CLAIM_MULTIPLE("claim.multiple"),

    /** The claim signature does not match. */
    CLAIM_SIGNATURE_MISMATCH("claimSignature.mismatch"),

    /** The claim signature is missing. */
    CLAIM_SIGNATURE_MISSING("claimSignature.missing"),

    /** The claim signature is outside its validity period. */
    CLAIM_SIGNATURE_OUTSIDE_VALIDITY("claimSignature.outsideValidity"),

    /** A general error occurred. */
    GENERAL_ERROR("general.error"),

    /** A hashed URI reference is missing. */
    HASHED_URI_MISSING("hashedURI.missing"),

    /** A hashed URI reference does not match. */
    HASHED_URI_MISMATCH("hashedURI.mismatch"),

    /** The ingredient's claim signature is missing. */
    INGREDIENT_CLAIM_SIGNATURE_MISSING("ingredient.claimSignature.missing"),

    /** The ingredient's claim signature does not match. */
    INGREDIENT_CLAIM_SIGNATURE_MISMATCH("ingredient.claimSignature.mismatch"),

    /** The ingredient's hashed URI does not match. */
    INGREDIENT_HASHED_URI_MISMATCH("ingredient.hashedURI.mismatch"),

    /** The ingredient's manifest is missing. */
    INGREDIENT_MANIFEST_MISSING("ingredient.manifest.missing"),

    /** The ingredient's manifest does not match. */
    INGREDIENT_MANIFEST_MISMATCH("ingredient.manifest.mismatch"),

    /** A compressed manifest is invalid. */
    MANIFEST_COMPRESSED_INVALID("manifest.compressed.invalid"),

    /** The manifest is inaccessible. */
    MANIFEST_INACCESSIBLE("manifest.inaccessible"),

    /** The manifest is missing. */
    MANIFEST_MISSING("manifest.missing"),

    /** The manifest has multiple parents. */
    MANIFEST_MULTIPLE_PARENTS("manifest.multipleParents"),

    /** The manifest timestamp is invalid. */
    MANIFEST_TIMESTAMP_INVALID("manifest.timestamp.invalid"),

    /** The manifest timestamp has wrong parents. */
    MANIFEST_TIMESTAMP_WRONG_PARENTS("manifest.timestamp.wrongParents"),

    /** The manifest update is invalid. */
    MANIFEST_UPDATE_INVALID("manifest.update.invalid"),

    /** The manifest update has wrong parents. */
    MANIFEST_UPDATE_WRONG_PARENTS("manifest.update.wrongParents"),

    /** The signing credential is expired. */
    SIGNING_CREDENTIAL_EXPIRED("signingCredential.expired"),

    /** The signing credential is invalid. */
    SIGNING_CREDENTIAL_INVALID("signingCredential.invalid"),

    /** The signing credential has been revoked (via OCSP). */
    SIGNING_CREDENTIAL_OCSP_REVOKED("signingCredential.ocsp.revoked"),

    /** The signing credential's OCSP revocation status (legacy code). */
    SIGNING_CREDENTIAL_REVOKED("signingCredential.revoked"),

    /** The signing credential is untrusted. */
    SIGNING_CREDENTIAL_UNTRUSTED("signingCredential.untrusted"),

    /** The metadata assertion contains a field that is not allowed. */
    ASSERTION_METADATA_DISALLOWED("assertion.metadata.disallowed"),

    /** A required field is not present in an assertion. */
    ASSERTION_REQUIRED_MISSING("assertion.required.missing"),

    /** The DID document for a CAWG identity claims aggregation credential is unavailable. */
    CAWG_ICA_DID_UNAVAILABLE("cawg.ica.did_unavailable"),

    /** A CAWG identity claims aggregation credential uses an invalid algorithm. */
    CAWG_ICA_INVALID_ALG("cawg.ica.invalid_alg"),

    /** A CAWG identity claims aggregation credential has an invalid content type. */
    CAWG_ICA_INVALID_CONTENT_TYPE("cawg.ica.invalid_content_type"),

    /** A CAWG identity claims aggregation COSE_Sign1 structure is invalid. */
    CAWG_ICA_INVALID_COSE_SIGN1("cawg.ica.invalid_cose_sign1"),

    /** The DID document for a CAWG identity claims aggregation credential is invalid. */
    CAWG_ICA_INVALID_DID_DOCUMENT("cawg.ica.invalid_did_document"),

    /** A CAWG identity claims aggregation issuer is invalid. */
    CAWG_ICA_INVALID_ISSUER("cawg.ica.invalid_issuer"),

    /** A CAWG identity claims aggregation verifiable credential is invalid. */
    CAWG_ICA_INVALID_VERIFIABLE_CREDENTIAL("cawg.ica.invalid_verifiable_credential"),

    /** The CAWG identity claims aggregation signature does not match. */
    CAWG_ICA_SIGNATURE_MISMATCH("cawg.ica.signature_mismatch"),

    /** A CAWG identity claims aggregation signer payload does not match the asset. */
    CAWG_ICA_SIGNER_PAYLOAD_MISMATCH("cawg.ica.signer_payload.mismatch"),

    /** The CAWG identity claims aggregation time stamp is invalid. */
    CAWG_ICA_TIME_STAMP_INVALID("cawg.ica.time_stamp.invalid"),

    /** The valid-from time in a CAWG identity claims aggregation credential is invalid. */
    CAWG_ICA_VALID_FROM_INVALID("cawg.ica.valid_from.invalid"),

    /** The valid-from time is missing from a CAWG identity claims aggregation credential. */
    CAWG_ICA_VALID_FROM_MISSING("cawg.ica.valid_from.missing"),

    /** The valid-until time in a CAWG identity claims aggregation credential is invalid. */
    CAWG_ICA_VALID_UNTIL_INVALID("cawg.ica.valid_until.invalid"),

    /** A CAWG identity assertion references the same assertion more than once. */
    CAWG_IDENTITY_ASSERTION_DUPLICATE("cawg.identity.assertion.duplicate"),

    /** A referenced CAWG identity assertion is missing or has a mismatched hash. */
    CAWG_IDENTITY_ASSERTION_MISMATCH("cawg.identity.assertion.mismatch"),

    /** The CAWG identity assertion CBOR is not valid. */
    CAWG_IDENTITY_CBOR_INVALID("cawg.identity.cbor.invalid"),

    /** A CAWG identity assertion does not reference a hard-binding assertion. */
    CAWG_IDENTITY_HARD_BINDING_MISSING("cawg.identity.hard_binding_missing"),

    /** The CAWG identity assertion padding is invalid. */
    CAWG_IDENTITY_PAD_INVALID("cawg.identity.pad.invalid"),

    /** The CAWG identity assertion signature type is unknown. */
    CAWG_IDENTITY_SIG_TYPE_UNKNOWN("cawg.identity.sig_type.unknown"),

    /** The CAWG identity assertion's X.509 signature algorithm is not supported. */
    CAWG_X509_ALGORITHM_UNSUPPORTED("cawg.x509.algorithm.unsupported"),

    /** The CAWG identity assertion's X.509 certificate does not meet the certificate profile. */
    CAWG_X509_CREDENTIAL_INVALID("cawg.x509.credential.invalid"),

    /** The CAWG identity assertion's X.509 certificate does not chain to a trust anchor. */
    CAWG_X509_CREDENTIAL_UNTRUSTED("cawg.x509.credential.untrusted"),

    /** The CAWG identity assertion's X.509 signature could not be validated. */
    CAWG_X509_SIGNATURE_MISMATCH("cawg.x509.signature.mismatch"),

    /** The time of signing is outside the validity period of the CAWG X.509 certificate chain. */
    CAWG_X509_SIGNATURE_OUTSIDE_VALIDITY("cawg.x509.signature.outside_validity"),

    /** A required field is not present in the claim. */
    CLAIM_REQUIRED_MISSING("claim.required.missing"),

    /** The manifest is not referenced by an ingredient assertion. */
    MANIFEST_UNREFERENCED("manifest.unreferenced"),
    ;

    companion object {
        private val byCode: Map<String, ValidationStatusCode> = entries.associateBy { it.code }

        /**
         * Returns the entry for a status code string, or `null` if this library does not list it.
         *
         * @param code The status code as it appears in manifest JSON, such as `claimSignature.validated`.
         */
        @JvmStatic
        fun fromCode(code: String): ValidationStatusCode? = byCode[code]
    }
}

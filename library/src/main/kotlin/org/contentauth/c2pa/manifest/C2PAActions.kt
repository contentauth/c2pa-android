package org.contentauth.c2pa.manifest

object C2PAActions {
    const val CREATED = "c2pa.created"
    const val EDITED = "c2pa.edited"
    const val OPENED = "c2pa.opened"
    const val PLACED = "c2pa.placed"
    const val DRAWING = "c2pa.drawing"
    const val COLOR_ADJUSTMENTS = "c2pa.color_adjustments"
    const val RESIZED = "c2pa.resized"
    const val CROPPED = "c2pa.cropped"
    const val FILTERED = "c2pa.filtered"
    const val ORIENTATION = "c2pa.orientation"
    const val TRANSCODED = "c2pa.transcoded"
    const val RECOMPRESSED = "c2pa.recompressed"
    const val VERSION_CREATED = "c2pa.version_created"
    const val CONVERTED = "c2pa.converted"
    const val PRODUCED = "c2pa.produced"
    const val PUBLISHED = "c2pa.published"
    const val REDACTED = "c2pa.redacted"

    object Adobe {
        const val PHOTOSHOP_EDITED = "adobe.photoshop.edited"
        const val ILLUSTRATOR_EDITED = "adobe.illustrator.edited"
        const val INDESIGN_EDITED = "adobe.indesign.edited"
        const val LIGHTROOM_EDITED = "adobe.lightroom.edited"
        const val PREMIERE_EDITED = "adobe.premiere.edited"
        const val AFTER_EFFECTS_EDITED = "adobe.after_effects.edited"
    }
}

object C2PAAssertionTypes {
    const val CREATIVE_WORK = "c2pa.creative_work"
    const val ACTIONS = "c2pa.actions"
    const val ASSERTION_METADATA = "c2pa.assertion.metadata"
    const val THUMBNAIL = "c2pa.thumbnail"
    const val DATA_HASH = "c2pa.data_hash"
    const val HASH_DATA = "c2pa.hash.data"
    const val BMFF_HASH = "c2pa.hash.bmff"
    const val EXIF = "stds.exif"
    const val IPTC = "stds.iptc"
    const val XMP = "stds.xmp"
    const val SCHEMA_ORG_CREATIVE_WORK = "stds.schema-org.CreativeWork"
    const val INGREDIENT = "c2pa.ingredient"
    const val CAWG_IDENTITY = "cawg.identity"
}

object CAWGIdentityTypes {
    const val SOCIAL_MEDIA = "cawg.social_media"
    const val EMAIL = "cawg.email"
    const val PHONE = "cawg.phone"
    const val WEBSITE = "cawg.website"
    const val PROFESSIONAL = "cawg.professional"
}

object CAWGProviders {
    const val INSTAGRAM = "https://instagram.com"
    const val BEHANCE = "https://behance.net"
    const val LINKEDIN = "https://linkedin.com"
    const val TWITTER = "https://twitter.com"
    const val FACEBOOK = "https://facebook.com"
    const val YOUTUBE = "https://youtube.com"
    const val TIKTOK = "https://tiktok.com"
    const val SNAPCHAT = "https://snapchat.com"
    const val PINTEREST = "https://pinterest.com"
    const val GITHUB = "https://github.com"
    const val DRIBBBLE = "https://dribbble.com"
    const val ARTSTATION = "https://artstation.com"
}

object C2PAFormats {
    const val JPEG = "image/jpeg"
    const val PNG = "image/png"
    const val WEBP = "image/webp"
    const val TIFF = "image/tiff"
    const val HEIF = "image/heif"
    const val AVIF = "image/avif"
    const val MP4 = "video/mp4"
    const val MOV = "video/quicktime"
    const val AVI = "video/x-msvideo"
    const val PDF = "application/pdf"
    const val SVG = "image/svg+xml"
    const val GIF = "image/gif"
    const val BMP = "image/bmp"
    const val WEBM = "video/webm"
    const val OGG = "video/ogg"
    const val MKV = "video/x-matroska"
}

object C2PARelationships {
    const val PARENT_OF = "parentOf"
    const val COMPONENT_OF = "componentOf"
    const val INGREDIENT_OF = "ingredientOf"
    const val ALTERNATE_OF = "alternateOf"
}

object TimestampAuthorities {
    const val DIGICERT = "http://timestamp.digicert.com"
    const val SECTIGO = "http://timestamp.sectigo.com"
    const val GLOBALSIGN = "http://timestamp.globalsign.com/tsa/r6advanced1"
    const val ENTRUST = "http://timestamp.entrust.net/TSS/RFC3161sha2TS"
}
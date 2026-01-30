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
 * IPTC Image Region types for categorizing regions of interest.
 *
 * These types describe what kind of subject or content is within a region.
 *
 * @see RegionOfInterest
 */
@Serializable
enum class ImageRegionType {
    /** A human subject. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/human")
    HUMAN,

    /** A face within the region. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/face")
    FACE,

    /** A headshot of a person. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/headshot")
    HEADSHOT,

    /** A body part. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/bodyPart")
    BODY_PART,

    /** An animal subject. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/animal")
    ANIMAL,

    /** A plant subject. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/plant")
    PLANT,

    /** A product or item. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/product")
    PRODUCT,

    /** A building or structure. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/building")
    BUILDING,

    /** An object. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/object")
    OBJECT,

    /** A vehicle. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/vehicle")
    VEHICLE,

    /** An event. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/event")
    EVENT,

    /** An artwork. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/artwork")
    ARTWORK,

    /** A logo. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/logo")
    LOGO,

    /** Text content. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/text")
    TEXT,

    /** A visible code (QR, barcode, etc.). */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/visibleCode")
    VISIBLE_CODE,

    /** A geographical feature or landmark. */
    @SerialName("http://cv.iptc.org/newscodes/imageregiontype/geoFeature")
    GEO_FEATURE,
}

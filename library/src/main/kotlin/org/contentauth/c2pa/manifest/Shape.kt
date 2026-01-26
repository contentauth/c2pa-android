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

import kotlinx.serialization.Serializable

/**
 * Represents a geometric shape for spatial regions.
 *
 * The shape type determines which properties are used:
 * - [RECTANGLE][ShapeType.RECTANGLE]: Uses [origin], [width], and [height]
 * - [CIRCLE][ShapeType.CIRCLE]: Uses [origin] (center) and [width] (diameter)
 * - [POLYGON][ShapeType.POLYGON]: Uses [vertices]
 *
 * @property type The geometric type of the shape.
 * @property origin The origin point (top-left for rectangles, center for circles).
 * @property width The width for rectangles or diameter for circles.
 * @property height The height for rectangles.
 * @property vertices A list of coordinate points defining a polygon.
 * @property inside Whether points inside (true) or outside (false) the shape are selected.
 * @property unit The unit of measurement for coordinates.
 * @see RegionRange
 */
@Serializable
data class Shape(
    val type: ShapeType,
    val origin: Coordinate? = null,
    val width: Double? = null,
    val height: Double? = null,
    val vertices: List<Coordinate>? = null,
    val inside: Boolean? = null,
    val unit: UnitType? = null,
) {
    companion object {
        /**
         * Creates a rectangular shape.
         *
         * @param x The x-coordinate of the top-left corner.
         * @param y The y-coordinate of the top-left corner.
         * @param width The width of the rectangle.
         * @param height The height of the rectangle.
         * @param unit The unit of measurement.
         * @param inside Whether points inside the shape are selected.
         */
        fun rectangle(
            x: Double,
            y: Double,
            width: Double,
            height: Double,
            unit: UnitType? = null,
            inside: Boolean? = null,
        ) = Shape(
            type = ShapeType.RECTANGLE,
            origin = Coordinate(x, y),
            width = width,
            height = height,
            unit = unit,
            inside = inside,
        )

        /**
         * Creates a circular shape.
         *
         * @param centerX The x-coordinate of the center.
         * @param centerY The y-coordinate of the center.
         * @param diameter The diameter of the circle.
         * @param unit The unit of measurement.
         * @param inside Whether points inside the shape are selected.
         */
        fun circle(
            centerX: Double,
            centerY: Double,
            diameter: Double,
            unit: UnitType? = null,
            inside: Boolean? = null,
        ) = Shape(
            type = ShapeType.CIRCLE,
            origin = Coordinate(centerX, centerY),
            width = diameter,
            unit = unit,
            inside = inside,
        )

        /**
         * Creates a polygon shape.
         *
         * @param vertices The list of vertices defining the polygon.
         * @param unit The unit of measurement.
         * @param inside Whether points inside the shape are selected.
         */
        fun polygon(
            vertices: List<Coordinate>,
            unit: UnitType? = null,
            inside: Boolean? = null,
        ) = Shape(
            type = ShapeType.POLYGON,
            vertices = vertices,
            unit = unit,
            inside = inside,
        )
    }
}

/*
 * Knowmad - Knowledge nomad
 * Copyright (C) 2026 LTFan (aka xfqwdsj)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package top.ltfan.knowmad.data.schedule

import biweekly.property.Color
import kotlinx.serialization.Serializable
import kotlin.uuid.Uuid

@Serializable
enum class ICalendarColor(val value: String, val r: UByte, val g: UByte, val b: UByte) {
    Aliceblue("aliceblue", 240u, 248u, 255u),
    Antiquewhite("antiquewhite", 250u, 235u, 215u),
    Aqua("aqua", 0u, 255u, 255u),
    Aquamarine("aquamarine", 127u, 255u, 212u),
    Azure("azure", 240u, 255u, 255u),
    Beige("beige", 245u, 245u, 220u),
    Bisque("bisque", 255u, 228u, 196u),
    Black("black", 0u, 0u, 0u),
    Blanchedalmond("blanchedalmond", 255u, 235u, 205u),
    Blue("blue", 0u, 0u, 255u),
    Blueviolet("blueviolet", 138u, 43u, 226u),
    Brown("brown", 165u, 42u, 42u),
    Burlywood("burlywood", 222u, 184u, 135u),
    Cadetblue("cadetblue", 95u, 158u, 160u),
    Chartreuse("chartreuse", 127u, 255u, 0u),
    Chocolate("chocolate", 210u, 105u, 30u),
    Coral("coral", 255u, 127u, 80u),
    Cornflowerblue("cornflowerblue", 100u, 149u, 237u),
    Cornsilk("cornsilk", 255u, 248u, 220u),
    Crimson("crimson", 220u, 20u, 60u),
    Cyan("cyan", 0u, 255u, 255u),
    Darkblue("darkblue", 0u, 0u, 139u),
    Darkcyan("darkcyan", 0u, 139u, 139u),
    Darkgoldenrod("darkgoldenrod", 184u, 134u, 11u),
    Darkgray("darkgray", 169u, 169u, 169u),
    Darkgreen("darkgreen", 0u, 100u, 0u),
    Darkgrey("darkgrey", 169u, 169u, 169u),
    Darkkhaki("darkkhaki", 189u, 183u, 107u),
    Darkmagenta("darkmagenta", 139u, 0u, 139u),
    Darkolivegreen("darkolivegreen", 85u, 107u, 47u),
    Darkorange("darkorange", 255u, 140u, 0u),
    Darkorchid("darkorchid", 153u, 50u, 204u),
    Darkred("darkred", 139u, 0u, 0u),
    Darksalmon("darksalmon", 233u, 150u, 122u),
    Darkseagreen("darkseagreen", 143u, 188u, 143u),
    Darkslateblue("darkslateblue", 72u, 61u, 139u),
    Darkslategray("darkslategray", 47u, 79u, 79u),
    Darkslategrey("darkslategrey", 47u, 79u, 79u),
    Darkturquoise("darkturquoise", 0u, 206u, 209u),
    Darkviolet("darkviolet", 148u, 0u, 211u),
    Deeppink("deeppink", 255u, 20u, 147u),
    Deepskyblue("deepskyblue", 0u, 191u, 255u),
    Dimgray("dimgray", 105u, 105u, 105u),
    Dimgrey("dimgrey", 105u, 105u, 105u),
    Dodgerblue("dodgerblue", 30u, 144u, 255u),
    Firebrick("firebrick", 178u, 34u, 34u),
    Floralwhite("floralwhite", 255u, 250u, 240u),
    Forestgreen("forestgreen", 34u, 139u, 34u),
    Fuchsia("fuchsia", 255u, 0u, 255u),
    Gainsboro("gainsboro", 220u, 220u, 220u),
    Ghostwhite("ghostwhite", 248u, 248u, 255u),
    Gold("gold", 255u, 215u, 0u),
    Goldenrod("goldenrod", 218u, 165u, 32u),
    Gray("gray", 128u, 128u, 128u),
    Green("green", 0u, 128u, 0u),
    Greenyellow("greenyellow", 173u, 255u, 47u),
    Grey("grey", 128u, 128u, 128u),
    Honeydew("honeydew", 240u, 255u, 240u),
    Hotpink("hotpink", 255u, 105u, 180u),
    Indianred("indianred", 205u, 92u, 92u),
    Indigo("indigo", 75u, 0u, 130u),
    Ivory("ivory", 255u, 255u, 240u),
    Khaki("khaki", 240u, 230u, 140u),
    Lavender("lavender", 230u, 230u, 250u),
    Lavenderblush("lavenderblush", 255u, 240u, 245u),
    Lawngreen("lawngreen", 124u, 252u, 0u),
    Lemonchiffon("lemonchiffon", 255u, 250u, 205u),
    Lightblue("lightblue", 173u, 216u, 230u),
    Lightcoral("lightcoral", 240u, 128u, 128u),
    Lightcyan("lightcyan", 224u, 255u, 255u),
    Lightgoldenrodyellow("lightgoldenrodyellow", 250u, 250u, 210u),
    Lightgray("lightgray", 211u, 211u, 211u),
    Lightgreen("lightgreen", 144u, 238u, 144u),
    Lightgrey("lightgrey", 211u, 211u, 211u),
    Lightpink("lightpink", 255u, 182u, 193u),
    Lightsalmon("lightsalmon", 255u, 160u, 122u),
    Lightseagreen("lightseagreen", 32u, 178u, 170u),
    Lightskyblue("lightskyblue", 135u, 206u, 250u),
    Lightslategray("lightslategray", 119u, 136u, 153u),
    Lightslategrey("lightslategrey", 119u, 136u, 153u),
    Lightsteelblue("lightsteelblue", 176u, 196u, 222u),
    Lightyellow("lightyellow", 255u, 255u, 224u),
    Lime("lime", 0u, 255u, 0u),
    Limegreen("limegreen", 50u, 205u, 50u),
    Linen("linen", 250u, 240u, 230u),
    Magenta("magenta", 255u, 0u, 255u),
    Maroon("maroon", 128u, 0u, 0u),
    Mediumaquamarine("mediumaquamarine", 102u, 205u, 170u),
    Mediumblue("mediumblue", 0u, 0u, 205u),
    Mediumorchid("mediumorchid", 186u, 85u, 211u),
    Mediumpurple("mediumpurple", 147u, 112u, 219u),
    Mediumseagreen("mediumseagreen", 60u, 179u, 113u),
    Mediumslateblue("mediumslateblue", 123u, 104u, 238u),
    Mediumspringgreen("mediumspringgreen", 0u, 250u, 154u),
    Mediumturquoise("mediumturquoise", 72u, 209u, 204u),
    Mediumvioletred("mediumvioletred", 199u, 21u, 133u),
    Midnightblue("midnightblue", 25u, 25u, 112u),
    Mintcream("mintcream", 245u, 255u, 250u),
    Mistyrose("mistyrose", 255u, 228u, 225u),
    Moccasin("moccasin", 255u, 228u, 181u),
    Navajowhite("navajowhite", 255u, 222u, 173u),
    Navy("navy", 0u, 0u, 128u),
    Oldlace("oldlace", 253u, 245u, 230u),
    Olive("olive", 128u, 128u, 0u),
    Olivedrab("olivedrab", 107u, 142u, 35u),
    Orange("orange", 255u, 165u, 0u),
    Orangered("orangered", 255u, 69u, 0u),
    Orchid("orchid", 218u, 112u, 214u),
    Palegoldenrod("palegoldenrod", 238u, 232u, 170u),
    Palegreen("palegreen", 152u, 251u, 152u),
    Paleturquoise("paleturquoise", 175u, 238u, 238u),
    Palevioletred("palevioletred", 219u, 112u, 147u),
    Papayawhip("papayawhip", 255u, 239u, 213u),
    Peachpuff("peachpuff", 255u, 218u, 185u),
    Peru("peru", 205u, 133u, 63u),
    Pink("pink", 255u, 192u, 203u),
    Plum("plum", 221u, 160u, 221u),
    Powderblue("powderblue", 176u, 224u, 230u),
    Purple("purple", 128u, 0u, 128u),
    Red("red", 255u, 0u, 0u),
    Rosybrown("rosybrown", 188u, 143u, 143u),
    Royalblue("royalblue", 65u, 105u, 225u),
    Saddlebrown("saddlebrown", 139u, 69u, 19u),
    Salmon("salmon", 250u, 128u, 114u),
    Sandybrown("sandybrown", 244u, 164u, 96u),
    Seagreen("seagreen", 46u, 139u, 87u),
    Seashell("seashell", 255u, 245u, 238u),
    Sienna("sienna", 160u, 82u, 45u),
    Silver("silver", 192u, 192u, 192u),
    Skyblue("skyblue", 135u, 206u, 235u),
    Slateblue("slateblue", 106u, 90u, 205u),
    Slategray("slategray", 112u, 128u, 144u),
    Slategrey("slategrey", 112u, 128u, 144u),
    Snow("snow", 255u, 250u, 250u),
    Springgreen("springgreen", 0u, 255u, 127u),
    Steelblue("steelblue", 70u, 130u, 180u),
    Tan("tan", 210u, 180u, 140u),
    Teal("teal", 0u, 128u, 128u),
    Thistle("thistle", 216u, 191u, 216u),
    Tomato("tomato", 255u, 99u, 71u),
    Turquoise("turquoise", 64u, 224u, 208u),
    Violet("violet", 238u, 130u, 238u),
    Wheat("wheat", 245u, 222u, 179u),
    White("white", 255u, 255u, 255u),
    Whitesmoke("whitesmoke", 245u, 245u, 245u),
    Yellow("yellow", 255u, 255u, 0u),
    Yellowgreen("yellowgreen", 154u, 205u, 50u);

    companion object {
        private val map = entries.associateBy(ICalendarColor::value)

        fun fromValue(value: String) = map[value]

        fun fromId(id: Uuid) = entries[
            (id.hashCode().toUInt() % ICalendarColor.entries.size.toUInt()).toInt()
        ]

    }

    val property inline get() = Color(value)
}

fun Color.toICalendarColor(): ICalendarColor? {
    return ICalendarColor.fromValue(value ?: return null)
}

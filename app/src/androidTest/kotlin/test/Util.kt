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

package top.ltfan.knowmad.test

import okio.Path
import okio.Path.Companion.toPath
import okio.fakefilesystem.FakeFileSystem
import kotlin.time.Clock
import kotlin.time.Instant

val TestClock = object : Clock {
    override fun now() = Instant.DISTANT_PAST
}

fun FakeFileSystem.dumpTree(
    root: Path = "/".toPath(),
    println: (String) -> Unit = ::println,
) {
    println("📂 File System Structure:")

    listRecursively(root).forEach { path ->
        val depth = path.segments.size
        val indent = " ".repeat(depth - 1)

        val metadata = this.metadataOrNull(path)
        val icon = if (metadata?.isDirectory == true) "📁" else "📄"

        println("$indent$icon ${path.name}")
    }
}

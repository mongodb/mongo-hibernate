/*
 * Copyright 2025-present MongoDB, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.diffplug.spotless.FormatterFunc
import com.diffplug.spotless.FormatterStep
import java.io.Serializable

plugins {
    id("com.diffplug.spotless")
}

spotless {
    java {
        addStep(
            FormatterStep.create(
                "multilineFormatter",
                MultilineFormatter(),
                { formatter -> FormatterFunc { input -> formatter.format(input) } }))
    }
}

class MultilineFormatter : Serializable {
    fun format(content: String): String {
        val tripleQuote = "\"\"\""
        val lines = content.lines()
        val result = StringBuilder()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (!line.trimEnd().endsWith(tripleQuote)) {
                result.append(line)
                if (i + 1 < lines.size) result.append("\n")
                i++
                continue
            }
            val baseIndent = line.indexOf(tripleQuote)
            result.append(line).append("\n")
            i++
            val multilineStringLines = mutableListOf<String>()
            while (i < lines.size) {
                val multilineStringLine = lines[i++]
                multilineStringLines.add(multilineStringLine)
                if (multilineStringLine.contains(tripleQuote)) break
            }
            val minIndent =
                multilineStringLines
                    .filter { it.isNotBlank() }
                    .map { l -> l.indexOfFirst { ch -> !ch.isWhitespace() }.takeIf { it >= 0 } ?: line.length }
                    .minOrNull() ?: 0
            multilineStringLines.forEach { blockLine ->
                result.append(" ".repeat(baseIndent)).append(blockLine.drop(minIndent)).append("\n")
            }
        }
        return result.toString()
    }
}
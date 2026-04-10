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

package top.ltfan.knowmad.ui.util

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.FloatRange
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Shader
import org.intellij.lang.annotations.Language

/**
 * Adapted from https://www.shadertoy.com/view/Mtl3Rj
 *
 * Uses the GPU-friendly optimizations, see
 * https://www.rastergrid.com/blog/2010/09/efficient-gaussian-blur-with-linear-sampling
 *
 * Copyright 2024, Christopher Banes and the Haze project contributors
 * SPDX-License-Identifier: Apache-2.0
 */
@Language("AGSL")
private fun makeBlurShader(vertical: Boolean) = """
uniform shader content;
uniform vec2 size;
uniform float blurRadius;
uniform shader mask;

const float maxRadius = 150.0;

bool inside(vec2 p) {
  return p.x >= 0.5 && p.y >= 0.5 && p.x <= size.x - 0.5 && p.y <= size.y - 0.5;
}

vec4 blur(vec2 coord, float radius) {
  if (radius <= 0.0) {
    return content.eval(coord);
  }

  float r = floor(radius);
  if (r <= 0.0) {
    return content.eval(coord);
  }

  float sigma = max(radius / 2.0, 1.0);
  float inv2Sigma2 = 1.0 / (2.0 * sigma * sigma);

  vec4 result = vec4(0.0);
  float weightSum = 0.0;

  bool fullyInside = ${if (vertical) "coord.y - r > 0.5 && coord.y + r < size.y - 0.5" else "coord.x - r > 0.5 && coord.x + r < size.x - 0.5"};

  float wPrev = 1.0;

  if (fullyInside) {
    result += wPrev * content.eval(coord);
    weightSum += wPrev;

    for (float i = 1.0; i < maxRadius; i += 2.0) {
      if (i >= r) { break; }

      float w1 = wPrev * exp(-(2.0 * (i - 1.0) + 1.0) * inv2Sigma2);
      float w2 = w1 * exp(-(2.0 * i + 1.0) * inv2Sigma2);

      float weight = w1 + w2;

      vec2 offset = ${if (vertical) "vec2(0.0, i + w2 / weight)" else "vec2(i + w2 / weight, 0.0)"};

      result += weight * content.eval(coord - offset);
      result += weight * content.eval(coord + offset);
      weightSum += 2.0 * weight;

      wPrev = w2;
    }

    if (r < maxRadius && mod(r, 2.0) == 1.0) {
      float w = wPrev * exp(-(2.0 * (r - 1.0) + 1.0) * inv2Sigma2);

      vec2 offset = ${if (vertical) "vec2(0.0, r)" else "vec2(r, 0.0)"};

      result += w * content.eval(coord - offset);
      result += w * content.eval(coord + offset);
      weightSum += 2.0 * w;
    }
  } else {
    if (inside(coord)) {
      result += wPrev * content.eval(coord);
      weightSum += wPrev;
    }

    for (float i = 1.0; i < maxRadius; i += 2.0) {
      if (i >= r) { break; }

      float w1 = wPrev * exp(-(2.0 * (i - 1.0) + 1.0) * inv2Sigma2);
      float w2 = w1 * exp(-(2.0 * i + 1.0) * inv2Sigma2);

      float weight = w1 + w2;

      vec2 offset = ${if (vertical) "vec2(0.0, i + w2 / weight)" else "vec2(i + w2 / weight, 0.0)"};

      vec2 p1 = coord - offset;
      if (inside(p1)) {
        result += weight * content.eval(p1);
        weightSum += weight;
      }

      vec2 p2 = coord + offset;
      if (inside(p2)) {
        result += weight * content.eval(p2);
        weightSum += weight;
      }

      wPrev = w2;
    }

    if (r < maxRadius && mod(r, 2.0) == 1.0) {
      float w = wPrev * exp(-(2.0 * (r - 1.0) + 1.0) * inv2Sigma2);

      vec2 offset = ${if (vertical) "vec2(0.0, r)" else "vec2(r, 0.0)"};

      vec2 p1 = coord - offset;
      if (inside(p1)) {
        result += w * content.eval(p1);
        weightSum += w;
      }

      vec2 p2 = coord + offset;
      if (inside(p2)) {
        result += w * content.eval(p2);
        weightSum += w;
      }
    }
  }

  return result / max(weightSum, 1e-5);
}

vec4 main(vec2 coord) {
  float intensity = mask.eval(coord).a;

  if (intensity <= 0.0 || blurRadius <= 0.0) {
    return content.eval(coord);
  }

  return blur(coord, mix(0.0, blurRadius, intensity));
}
"""

val BlurShaderVertical by lazy(NONE) {
    makeBlurShader(true)
}
val BlurShaderHorizontal: String by lazy(NONE) {
    makeBlurShader(false)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
fun createBlurRenderEffect(
    size: Size,
    @FloatRange(from = 0.0, fromInclusive = true) radius: Float,
    mask: Shader,
): RenderEffect {
    val verticalShader = RuntimeShader(BlurShaderVertical).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("blurRadius", radius)
        setInputShader("mask", mask)
    }

    val horizontalShader = RuntimeShader(BlurShaderHorizontal).apply {
        setFloatUniform("size", size.width, size.height)
        setFloatUniform("blurRadius", radius)
        setInputShader("mask", mask)
    }

    val verticalBlurEffect = RenderEffect.createRuntimeShaderEffect(verticalShader, "content")
    val horizontalBlurEffect = RenderEffect.createRuntimeShaderEffect(horizontalShader, "content")

    return RenderEffect.createChainEffect(
        horizontalBlurEffect,
        verticalBlurEffect,
    )
}

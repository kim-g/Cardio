package com.example.gl

import android.opengl.GLES30
import android.util.Log

object HeartShaders {

    const val BACKDROP_VERTEX = """#version 300 es
precision highp float;
layout(location = 0) in vec2 aPos;
out vec2 vUv;
void main() {
    vUv = aPos * 0.5 + 0.5;
    gl_Position = vec4(aPos, 0.9999, 1.0);
}
"""

    const val BACKDROP_FRAGMENT = """#version 300 es
precision mediump float;
in vec2 vUv;
out vec4 fragColor;
void main() {
    vec2 d = vUv - vec2(0.5, 0.52);
    float dist = length(d * vec2(1.0, 1.35));
    vec3 centerCol = vec3(0.065, 0.105, 0.185);
    vec3 edgeCol = vec3(0.025, 0.042, 0.072);
    vec3 col = mix(centerCol, edgeCol, smoothstep(0.05, 0.85, dist));
    fragColor = vec4(col, 1.0);
}
"""

    const val HEART_VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aRegion;
layout(location = 3) in vec3 aLocalDir;

uniform mat4 uModelMatrix;
uniform mat4 uViewProjMatrix;
uniform float uAtrialScale;
uniform float uVentricularScale;
uniform float uVentricularTwist;
uniform float uVesselPulse;

out vec3 vModelPos;
out vec3 vWorldPos;
out vec3 vNormal;
out float vRegion;

vec3 rotateY(vec3 p, float angle) {
    float c = cos(angle);
    float s = sin(angle);
    return vec3(c * p.x + s * p.z, p.y, -s * p.x + c * p.z);
}

void main() {
    vec3 pos = aPosition;
    vec3 norm = aNormal;

    if (aRegion < 2.5) {
        // Left & Right Ventricles: radial systole squeeze + apical torsion
        float disp = (uVentricularScale - 1.0) * 0.42;
        pos += aLocalDir * disp;
        float apexFactor = clamp((-pos.y + 0.15) * 0.85, 0.0, 1.0);
        float twist = uVentricularTwist * apexFactor;
        pos = rotateY(pos, twist);
        norm = rotateY(norm, twist);
    } else if (aRegion < 4.5) {
        // Right & Left Atria: diastolic expansion + atrial systole contraction
        float disp = (uAtrialScale - 1.0) * 0.34;
        pos += aLocalDir * disp;
    } else if (aRegion > 4.5 && aRegion < 6.5) {
        // Pulmonary Trunk & Aorta: Windkessel elastic systolic expansion
        pos += aLocalDir * (uVesselPulse * 0.22);
    } else if (aRegion > 6.5 && aRegion < 7.5) {
        // Interventricular septum: subtle thickening/shift with systole
        pos += aLocalDir * ((uVentricularScale - 1.0) * 0.12);
    }

    vec4 world = uModelMatrix * vec4(pos, 1.0);
    vModelPos = pos;
    vWorldPos = world.xyz;
    vNormal = normalize(mat3(uModelMatrix) * norm);
    vRegion = aRegion;
    gl_Position = uViewProjMatrix * world;
}
"""

    const val HEART_FRAGMENT = """#version 300 es
precision highp float;

in vec3 vModelPos;
in vec3 vWorldPos;
in vec3 vNormal;
in float vRegion;

uniform vec3 uCameraPos;
uniform float uOpacity;
uniform float uCutawayAmount;
uniform float uSelectedRegion;
uniform float uIsOpaquePass;

out vec4 fragColor;

vec3 regionBaseColor(float r) {
    if (r < 1.5) return vec3(0.68, 0.14, 0.20);      // Left Ventricle
    if (r < 2.5) return vec3(0.58, 0.14, 0.21);      // Right Ventricle
    if (r < 3.5) return vec3(0.60, 0.17, 0.24);      // Right Atrium
    if (r < 4.5) return vec3(0.70, 0.19, 0.26);      // Left Atrium
    if (r < 5.5) return vec3(0.21, 0.36, 0.72);      // Pulmonary Trunk & Arteries (venous)
    if (r < 6.5) return vec3(0.78, 0.18, 0.24);      // Aorta (arterial)
    if (r < 7.5) return vec3(0.46, 0.11, 0.16);      // Septum
    if (r < 8.5) return vec3(0.18, 0.32, 0.64);      // Venae Cavae (venous)
    return vec3(0.74, 0.20, 0.28);                   // Pulmonary Veins (arterial)
}

void main() {
    bool isChamber = (vRegion < 4.5);
    bool isSeptum = (vRegion > 6.5 && vRegion < 7.5);

    // Anatomical anterior cutaway plane for chambers
    float cutEdgeGlow = 0.0;
    if (uCutawayAmount > 0.5 && isChamber) {
        float cutDist = vModelPos.z - 0.02;
        if (cutDist > 0.03) {
            discard;
        }
        cutEdgeGlow = smoothstep(-0.04, 0.028, cutDist);
    }

    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) {
        N = -N;
    }
    vec3 V = normalize(uCameraPos - vWorldPos);

    // Medical Studio 3-point lighting
    vec3 L1 = normalize(vec3(0.55, 0.78, 0.75));
    vec3 L2 = normalize(vec3(-0.75, 0.25, -0.55));
    vec3 L3 = normalize(vec3(0.0, -0.85, 0.35));

    float diff1 = max(dot(N, L1), 0.0);
    float diff2 = max(dot(N, L2), 0.0);
    float diff3 = max(dot(N, L3), 0.0);

    vec3 H1 = normalize(L1 + V);
    float spec = pow(max(dot(N, H1), 0.0), 42.0) * 0.42;

    float fresnel = pow(1.0 - clamp(abs(dot(N, V)), 0.0, 1.0), 2.2);

    vec3 baseCol = regionBaseColor(vRegion);
    if (!gl_FrontFacing) {
        // Darker endocardial inner cavity wall
        baseCol *= 0.58;
    }

    vec3 lit = baseCol * (0.28 + 0.72 * diff1)
             + vec3(0.18, 0.28, 0.46) * (0.30 * diff2)
             + baseCol * (0.22 * diff3)
             + vec3(1.0, 0.92, 0.92) * spec;

    // Rim lighting gives crisp glass-like anatomical contours when transparent
    vec3 rimTint = (vRegion > 4.5 && vRegion < 5.5) || (vRegion > 7.5 && vRegion < 8.5)
        ? vec3(0.38, 0.65, 1.0)
        : vec3(1.0, 0.38, 0.48);
    lit += rimTint * fresnel * (0.25 + 0.55 * (1.0 - uOpacity));

    // Cutaway edge highlight
    lit += vec3(0.98, 0.48, 0.56) * cutEdgeGlow * 0.55;

    // Highlight selected structure from label tap
    if (uSelectedRegion > 0.5 && abs(vRegion - uSelectedRegion) < 0.4) {
        lit += vec3(0.06, 0.85, 0.68) * (0.18 + 0.45 * fresnel);
    }

    float finalAlpha = uOpacity;
    if (isSeptum) {
        finalAlpha = max(uOpacity, uCutawayAmount > 0.5 ? 0.88 : 0.45);
    } else if (uCutawayAmount > 0.5 && isChamber) {
        // Keep posterior cavity wall relatively visible in cutaway mode
        finalAlpha = max(uOpacity, 0.82);
    } else if (uOpacity < 0.96) {
        finalAlpha = clamp(uOpacity * 0.82 + fresnel * 0.46, 0.06, 0.95);
    }

    if (uIsOpaquePass > 0.5 && finalAlpha < 0.95) {
        discard;
    }

    fragColor = vec4(lit, finalAlpha);
}
"""

    const val VALVE_VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in float aHingeWeight;
layout(location = 3) in float aSectorAngle;

uniform mat4 uModelMatrix;
uniform mat4 uViewProjMatrix;
uniform vec3 uValveCenter;
uniform vec3 uFlowAxis;
uniform float uOpenAmount;
uniform float uValveRadius;

out vec3 vWorldPos;
out vec3 vNormal;

void main() {
    vec3 fromCenter = aPosition - uValveCenter;
    float axial = dot(fromCenter, uFlowAxis);
    vec3 radialVec = fromCenter - uFlowAxis * axial;
    float rLen = length(radialVec);
    vec3 radialDir = rLen > 0.0001 ? radialVec / rLen : vec3(1.0, 0.0, 0.0);

    // When valve opens, cusp tips swing forward along flowAxis and radially outward toward annulus
    float swingOut = uOpenAmount * aHingeWeight * uValveRadius * 0.72;
    float swingForward = uOpenAmount * aHingeWeight * uValveRadius * 0.95;

    vec3 pos = aPosition + radialDir * swingOut + uFlowAxis * swingForward;

    vec4 world = uModelMatrix * vec4(pos, 1.0);
    vWorldPos = world.xyz;
    vNormal = normalize(mat3(uModelMatrix) * aNormal);
    gl_Position = uViewProjMatrix * world;
}
"""

    const val VALVE_FRAGMENT = """#version 300 es
precision mediump float;

in vec3 vWorldPos;
in vec3 vNormal;

uniform vec3 uCameraPos;

out vec4 fragColor;

void main() {
    vec3 N = normalize(vNormal);
    if (!gl_FrontFacing) N = -N;
    vec3 V = normalize(uCameraPos - vWorldPos);
    vec3 L = normalize(vec3(0.5, 0.8, 0.7));
    float diff = max(dot(N, L), 0.0);
    float rim = pow(1.0 - clamp(abs(dot(N, V)), 0.0, 1.0), 2.0);
    vec3 ivory = vec3(0.92, 0.88, 0.82);
    vec3 col = ivory * (0.45 + 0.55 * diff) + vec3(0.95, 0.85, 0.75) * rim * 0.25;
    fragColor = vec4(col, 0.96);
}
"""

    const val PARTICLE_VERTEX = """#version 300 es
precision highp float;

layout(location = 0) in vec3 aPosition;
layout(location = 1) in vec2 aUv;
layout(location = 2) in float aIsArterial;
layout(location = 3) in float aSpeedNorm;
layout(location = 4) in float aAlpha;

uniform mat4 uModelMatrix;
uniform mat4 uViewProjMatrix;

out vec2 vUv;
out float vIsArterial;
out float vSpeedNorm;
out float vAlpha;

void main() {
    vUv = aUv;
    vIsArterial = aIsArterial;
    vSpeedNorm = aSpeedNorm;
    vAlpha = aAlpha;
    gl_Position = uViewProjMatrix * uModelMatrix * vec4(aPosition, 1.0);
}
"""

    const val PARTICLE_FRAGMENT = """#version 300 es
precision mediump float;

in vec2 vUv;
in float vIsArterial;
in float vSpeedNorm;
in float vAlpha;

out vec4 fragColor;

void main() {
    vec2 centered = vUv * 2.0 - 1.0;
    float r2 = dot(centered, centered);
    if (r2 > 1.0) discard;

    float glow = exp(-r2 * 2.1);
    float core = smoothstep(0.60, 0.0, r2);

    vec3 venousOuter = vec3(0.10, 0.32, 0.85);
    vec3 venousCore  = vec3(0.40, 0.72, 1.00);

    vec3 arterialOuter = vec3(0.82, 0.08, 0.20);
    vec3 arterialCore  = vec3(1.00, 0.36, 0.48);

    vec3 outerCol = mix(venousOuter, arterialOuter, vIsArterial);
    vec3 coreCol = mix(venousCore, arterialCore, vIsArterial);

    float boost = 0.88 + 0.32 * clamp(vSpeedNorm, 0.0, 1.0);
    vec3 col = mix(outerCol, coreCol, core) * boost;
    float alpha = glow * vAlpha * (0.72 + 0.28 * core);
    fragColor = vec4(col, clamp(alpha, 0.0, 0.92));
}
"""

    fun compileProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES30.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSrc)
        val program = GLES30.glCreateProgram()
        GLES30.glAttachShader(program, vs)
        GLES30.glAttachShader(program, fs)
        GLES30.glLinkProgram(program)
        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Log.e("HeartShaders", "Program link error: ${GLES30.glGetProgramInfoLog(program)}")
            GLES30.glDeleteProgram(program)
            return 0
        }
        GLES30.glDeleteShader(vs)
        GLES30.glDeleteShader(fs)
        return program
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, src)
        GLES30.glCompileShader(shader)
        val status = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            Log.e("HeartShaders", "Shader compile error: ${GLES30.glGetShaderInfoLog(shader)}")
        }
        return shader
    }
}

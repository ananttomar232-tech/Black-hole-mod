#version 150

// Living Black Hole - screen-space gravitational lensing.
// Point-lens mapping with a softened secondary image, frame-dragging swirl, chromatic dispersion,
// heat shimmer, light starvation near the shadow and an Einstein ring at the caustic.

uniform sampler2D DiffuseSampler;
uniform sampler2D DepthSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 InSize;
uniform vec2 Center;
uniform float Radius;
uniform float Strength;
uniform float HoleDepth;
uniform float Instability;
uniform float GameTime;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec2 rotate2(vec2 v, float a) {
    float s = sin(a);
    float c = cos(a);
    return vec2(c * v.x - s * v.y, s * v.x + c * v.y);
}

// On-screen offset from the hole (aspect corrected) -> offset the light actually came from.
vec2 lens(vec2 d, float rs, float einstein, float k) {
    float r = max(length(d), 1e-5);
    vec2 n = d / r;
    float beta = r - einstein * einstein * k / r;
    float soft = 1.12 * rs;
    float src = sign(beta) * sqrt(beta * beta + soft * soft);
    float spin = Strength * (0.35 + Instability * 0.9) * (rs * rs) / (r * r);
    vec2 lensed = rotate2(n * src, spin);
    float falloff = exp(-max(r - rs, 0.0) / (rs * 14.0));
    return mix(d, lensed, clamp(Strength, 0.0, 1.5) * falloff);
}

vec3 sampleBackground(vec2 d, float aspect) {
    vec2 uv = Center + vec2(d.x / aspect, d.y);
    uv = clamp(uv, vec2(0.001), vec2(0.999));
    // Never smear foreground geometry into the lensed background.
    if (texture(DepthSampler, uv).r < HoleDepth) {
        uv = texCoord;
    }
    return texture(DiffuseSampler, uv).rgb;
}

void main() {
    vec4 base = texture(DiffuseSampler, texCoord);
    float sceneDepth = texture(DepthSampler, texCoord).r;
    if (Strength <= 0.001 || sceneDepth < HoleDepth) {
        fragColor = vec4(base.rgb, 1.0);
        return;
    }

    float aspect = InSize.x / InSize.y;
    vec2 d = texCoord - Center;
    d.x *= aspect;
    float r = length(d);
    float rs = max(Radius, 1e-4);
    float einstein = rs * 1.65;

    float shimmerMask = exp(-max(r - rs, 0.0) / (rs * 2.5)) * Strength;
    vec2 shimmer = vec2(vnoise(texCoord * 140.0 + GameTime * 3.0), vnoise(texCoord * 140.0 - GameTime * 2.7)) - 0.5;
    d += shimmer * rs * (0.05 + Instability * 0.08) * shimmerMask;

    vec3 col;
    col.r = sampleBackground(lens(d, rs, einstein, 1.02), aspect).r;
    col.g = sampleBackground(lens(d, rs, einstein, 1.00), aspect).g;
    col.b = sampleBackground(lens(d, rs, einstein, 0.97), aspect).b;

    float starve = smoothstep(rs * 0.95, rs * 2.2, r);
    col *= mix(1.0, starve, 0.7 * clamp(Strength, 0.0, 1.0));

    float q1 = (r - einstein) / (rs * 0.07);
    float q2 = (r - rs * 1.08) / (rs * 0.04);
    float ring = exp(-q1 * q1) * Strength;
    float ring2 = exp(-q2 * q2) * Strength;
    col += vec3(1.0, 0.82, 0.62) * ring * (0.22 + Instability * 0.4);
    col += vec3(1.0, 0.93, 0.85) * ring2 * (0.35 + Instability * 0.5);

    fragColor = vec4(col, 1.0);
}

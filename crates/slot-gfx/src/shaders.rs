use crate::surface::GfxError;

const VERT_PREAMBLE: &str = "#version 330 core\n#define attribute in\n#define varying out\n";

const FRAG_PREAMBLE: &str = "#version 330 core\n#define varying in\n\
                             #define texture2D texture\nout vec4 FRAG_COLOR;\n";

const VERT_PREAMBLE_ES: &str = "";
const FRAG_PREAMBLE_ES: &str = "#define FRAG_COLOR gl_FragColor\n";

pub fn program(vert: &str, frag: &str) -> Result<gl::types::GLuint, GfxError> {
    let (vp, fp) = match crate::gl::es() {
        true => (VERT_PREAMBLE_ES, FRAG_PREAMBLE_ES),
        false => (VERT_PREAMBLE, FRAG_PREAMBLE),
    };
    crate::gl::program(&format!("{vp}{vert}"), &format!("{fp}{frag}"))
}

pub const RECT_VERT: &str = r#"
attribute vec2 a_pos;
uniform vec4 u_rect;
uniform vec2 u_target;
varying vec2 v_uv;
void main() {
    v_uv = a_pos;
    vec2 p = (u_rect.xy + a_pos * u_rect.zw) / u_target;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
}
"#;

pub const SPRITE_VERT: &str = r#"
attribute vec2 a_pos;
uniform vec4 u_rect;
uniform vec2 u_target;
uniform vec2 u_turn;
varying vec2 v_uv;
void main() {
    v_uv = a_pos;
    vec2 mid = u_rect.zw * 0.5;
    vec2 local = a_pos * u_rect.zw - mid;
    vec2 turned = vec2(u_turn.x * local.x - u_turn.y * local.y,
                       u_turn.y * local.x + u_turn.x * local.y);
    vec2 p = (u_rect.xy + a_pos * u_rect.zw + (turned - local)) / u_target;
    gl_Position = vec4(p.x * 2.0 - 1.0, 1.0 - p.y * 2.0, 0.0, 1.0);
}
"#;

pub const GAME_FRAG: &str = r#"
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D u_game;
uniform sampler2D u_paper;
uniform vec2 u_src;
uniform vec4 u_uv;
uniform float u_bright;
uniform float u_mode;
uniform float u_colour_correct;
uniform float u_paper_size;
uniform vec4 u_rect;
uniform vec2 u_fbo;
uniform vec4 u_pic;
// Pixel Transparency (see pixel_transparency.rs for each value).
uniform vec4 u_pt0; // on, transparent pixels, amount, white threshold
uniform vec4 u_pt1; // white boost, natural brightness, grid on colors / whites
uniform vec4 u_pt2; // backing tint, tint intensity, backing brightness, polarizer
uniform vec4 u_pt3; // saturation, pixel brightness, full texture, smooth blur
uniform vec4 u_pt4; // shadows, edge shadow, shadow opacity, shadow blur
uniform vec2 u_pt_shadow; // shadow offset, 640x480 reference pixels
varying vec2 v_uv;
const vec3 offsets = vec3(3.141592654) * vec3(1.0 / 2.0, 1.0 / 2.0 - 2.0 / 3.0, 1.0 / 2.0 - 4.0 / 3.0);
const vec3 luma_coeff = vec3(0.2126, 0.7152, 0.0722);
float dot_weight(vec2 f, vec2 o, float bloom) {
    // RetroArch Dot defaults: gamma 2.4, shine 0.05, blend 0.65,
    // soft 0.0. With soft 0.0, all nine taps share one source sample.
    return exp(-2.4 * length(f - (o + vec2(0.5))) * bloom);
}

/* GLES2 ports of libretro/glsl-shaders (see licenses/shaders.txt).
   No recompilation on hot switching: all modes are linked once at startup.
   Multi-pass AGB/AGS and textured presets are analytic/native-scale ports
   requiring visual QA before claiming byte-for-byte RetroArch parity. */
float lcd_integral(float z, bool y) {
    float a = y ? 0.0 : -2.0/3.0;
    float b = y ? -4.0/5.0 : -1.0/5.0;
    float d = y ? 2.0/7.0 : 4.0/7.0;
    float e = y ? 4.0/9.0 : -1.0/9.0;
    float g = y ? -4.0/11.0 : -2.0/11.0;
    float z2 = z*z;
    return z*(1.0+z2*(a+z2*(b+z2*(d+z2*(e+z2*(g+z2/13.0))))));
}
float lcd_smear(float x, float dx, float radius, bool y) {
    dx = max(dx, 0.0001);
    float lo = clamp((x-dx*0.5)/radius, -1.0, 1.0);
    float hi = clamp((x+dx*0.5)/radius, -1.0, 1.0);
    return radius*(lcd_integral(hi,y)-lcd_integral(lo,y))/dx;
}
vec3 gba_color(vec3 c) {
    // hunterk/Pokefan531 official gba-color default matrix and gamma.
    c = pow(max(c,vec3(0.0)),vec3(3.2));
    c = mat3(0.82,0.125,0.195, 0.24,0.665,0.075, -0.06,0.21,0.73)*(c*0.94);
    return pow(clamp(c,0.0,1.0),vec3(1.0/2.2));
}
vec3 gba_simpletex_color(vec3 c) {
    c = pow(max(c,vec3(0.0)),vec3(3.25));
    c = mat3(0.82,0.125,0.195, 0.24,0.665,0.075, -0.06,0.21,0.73)*(c*0.94);
    return clamp(pow(max(c,vec3(0.0)),vec3(1.0/2.2)),0.0,1.0);
}
vec3 lcd_grid_v2(vec2 uv, bool bgr) {
    // cgwg LCD Grid V2 coefficient integration, rewritten without GLSL130
    // array constructors (unsupported on GLES2). Same official preset
    // parameters: gain=1.5, gamma=2.2, RGB matrix diagonal=0.75.
    vec2 xy = uv*u_src;
    vec2 tli = floor(xy-vec2(0.4999));
    vec2 range = vec2(1.0)/max(u_rect.zw*u_fbo,vec2(1.0));
    float sub = (xy.x-0.4999-tli.x)*3.0;
    float dx = range.x*3.0;
    vec3 left = vec3(lcd_smear(sub+1.0,dx,1.5,false),
                     lcd_smear(sub,dx,1.5,false),
                     lcd_smear(sub-1.0,dx,1.5,false));
    vec3 right = vec3(lcd_smear(sub-2.0,dx,1.5,false),
                      lcd_smear(sub-3.0,dx,1.5,false),
                      lcd_smear(sub-4.0,dx,1.5,false));
    if (bgr) { left=left.bgr; right=right.bgr; }
    float py = xy.y-0.4999-tli.y;
    float top = lcd_smear(py,range.y,0.63,true);
    float bot = lcd_smear(py-1.0,range.y,0.63,true);
    vec2 pixel = vec2(1.0)/u_src;
    vec2 pos = (tli+vec2(0.5))*pixel;
    vec3 tl=pow(1.5*texture2D(u_game,pos).rgb,vec3(2.2));
    vec3 tr=pow(1.5*texture2D(u_game,pos+vec2(pixel.x,0.0)).rgb,vec3(2.2));
    vec3 bl=pow(1.5*texture2D(u_game,pos+vec2(0.0,pixel.y)).rgb,vec3(2.2));
    vec3 br=pow(1.5*texture2D(u_game,pos+pixel).rgb,vec3(2.2));
    vec3 weighted=(tl*left+tr*right)*top+(bl*left+br*right)*bot;
    return pow(max(weighted*0.75,vec3(0.0)),vec3(1.0/2.2));
}
vec3 mgba_mask(vec2 uv) {
    // endrift AGB/AGS-001 original 4x RGB subpixel mask.
    float px=mod((uv*u_src).x*4.0,4.0);
    if(px<1.0) return vec3(1.0,0.2,0.2);
    if(px<2.0) return vec3(0.2,1.0,0.2);
    if(px<3.0) return vec3(0.2,0.2,1.0);
    return vec3(0.4);
}
vec3 ags_first_pass(vec2 uv) {
    float py=mod((uv*u_src).y*4.0,4.0);
    return pow(texture2D(u_game,uv).rgb,vec3(1.6))*mgba_mask(uv)*
           (py>=3.0 ? 0.9 : 1.0);
}
vec3 nearest_bilinear(vec2 uv) {
    // GPU sampler remains NEAREST for the other presets. Manually emulate
    // the RetroArch sharp-bilinear LINEAR filter using four source texels.
    vec2 coord=uv*u_src-vec2(0.5);
    vec2 base=floor(coord);
    vec2 t=fract(coord);
    vec2 stepuv=vec2(1.0)/u_src;
    vec2 pos=(base+vec2(0.5))*stepuv;
    vec3 a=texture2D(u_game,pos).rgb;
    vec3 b=texture2D(u_game,pos+vec2(stepuv.x,0.0)).rgb;
    vec3 c=texture2D(u_game,pos+vec2(0.0,stepuv.y)).rgb;
    vec3 d=texture2D(u_game,pos+stepuv).rgb;
    return mix(mix(a,b,t.x),mix(c,d,t.x),t.y);
}

/* Pixel Transparency v2.3 by mattakins, Copyright (C) 2025-2026 Matt Akins
   (github.com/mattakins/Pixel_Transparency), ported to GLES2 as the last
   step of this pass. `lcd` is the screen shader's result (RetroArch's
   Source), u_game the unprocessed frame (Original). `p` is the position on
   the shown picture, 0..1 (vTexCoord); `lo` and `span` map it back to the
   game texture; `px` is the picture's size in framebuffer pixels
   (OutputSize). The shadow offset, motion included, arrives from the CPU.
   Same arithmetic as the slang shader; its two blur shortcuts
   (collapseShadowAxis) are exact optimizations and are left out. */
float pt_luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}
float pt_brightness(vec3 c) {
    return u_pt1.y < 0.5 ? (c.r + c.g + c.b) / 3.0 : pt_luma(c);
}
bool pt_white(vec3 c) {
    return pt_luma(c) > u_pt0.w && min(min(c.r, c.g), c.b) > u_pt0.w * 0.9;
}
float pt_hash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
float pt_paper(vec2 p) {
    p *= 128.0; // uv * scale 0.25 * 512
    float n = pt_hash(p) * 0.5 + pt_hash(p * 2.0) * 0.25;
    if (u_pt3.z < 0.5) return n * (7.0 / 6.0);
    return n + pt_hash(p * 4.0) * 0.125;
}
float pt_caster(vec2 p, vec2 lo, vec2 span) {
    if (any(lessThan(p, vec2(0.0))) || any(greaterThanEqual(p, vec2(1.0)))) {
        return u_pt4.y;
    }
    return 1.0 - pt_brightness(texture2D(u_game, lo + p * span).rgb);
}
float pt_alpha(vec3 source, bool white) {
    float a = u_pt0.y > 0.5 && u_pt0.y < 1.5
        ? u_pt0.z * pt_brightness(source) * 2.665
        : pt_brightness(source) / 3.0 + u_pt0.z;
    return white ? max(a, u_pt1.x) : a;
}
vec3 pixel_transparency(vec3 lcd, vec3 orig, vec2 p, vec2 lo, vec2 span, vec2 px) {
    // Negative pixel brightness dims the screen before compositing.
    if (u_pt3.y < -0.001) lcd *= max(1.0 + u_pt3.y * pt_luma(lcd), 0.0);
    vec3 out_c = lcd;
    bool white = pt_white(orig);
    bool apply = u_pt0.y < 0.5 ? white : true;
    if (apply) {
        float grid = white ? u_pt1.w : u_pt1.z;
        float alpha = pt_alpha(orig, white);
        if (grid > 0.0) alpha = mix(alpha, pt_alpha(lcd, white), grid);
        alpha = clamp(alpha, 0.0, 1.0);

        vec3 back = vec3(u_pt2.z + (pt_paper(p) - 0.4375) * 0.065);
        if (u_pt4.x > 0.5) {
            float scale = sqrt(px.x / 640.0 * px.y / 480.0);
            vec2 texel = 1.0 / px;
            vec2 at = p - u_pt_shadow * scale * texel;
            float shadow;
            if (u_pt4.w > 0.1) {
                vec2 bx = vec2(u_pt4.w * scale * texel.x, 0.0);
                vec2 by = vec2(0.0, u_pt4.w * scale * texel.y);
                float sides = pt_caster(at - bx, lo, span) + pt_caster(at + bx, lo, span)
                    + pt_caster(at - by, lo, span) + pt_caster(at + by, lo, span);
                float centre = pt_caster(at, lo, span);
                if (u_pt3.w > 0.5) {
                    // 3x3 Gaussian: centre 4, edges 2, corners 1 (of 16).
                    float corners = pt_caster(at - bx - by, lo, span)
                        + pt_caster(at + bx - by, lo, span)
                        + pt_caster(at - bx + by, lo, span)
                        + pt_caster(at + bx + by, lo, span);
                    shadow = (centre * 4.0 + sides * 2.0 + corners) / 16.0;
                } else {
                    // 5-tap cross: centre 4, sides 2 (of 12).
                    shadow = (centre * 4.0 + sides * 2.0) / 12.0;
                }
            } else {
                shadow = pt_caster(at, lo, span);
            }
            shadow *= u_pt4.z;
            // Deadzone: near-white casters leave no faint shadow.
            float deadzone = (1.0 - u_pt0.w) * u_pt4.z;
            if (deadzone > 0.001) shadow = smoothstep(0.0, deadzone, shadow) * shadow;
            back = mix(back, back * 0.2, shadow);
        }
        if (u_pt2.x > 0.5) {
            vec3 tint = u_pt2.x < 1.5 ? vec3(0.651, 0.675, 0.518) // Warm 1
                : u_pt2.x < 2.5 ? vec3(0.72, 0.73, 0.66)          // Warm 2
                : u_pt2.x < 3.5 ? vec3(0.766, 0.73, 0.763)        // Neutral
                : vec3(0.76);                                       // Aluminum
            tint *= 0.675 / max(tint.r, max(tint.g, tint.b));
            vec3 tinted = clamp(tint + back * 2.0 - 1.0, 0.0, 1.0);
            back = mix(back, tinted, u_pt2.y);
        }
        out_c = mix(lcd, back, alpha);
    }
    // Front polarizer, light passing through it twice.
    if (u_pt2.w > 0.001) out_c *= mix(vec3(1.0), vec3(0.94, 1.0, 0.865), u_pt2.w);
    if (u_pt3.y > 0.001) out_c = clamp(out_c * (1.0 + u_pt3.y * pt_luma(out_c)), 0.0, 1.0);
    if (abs(u_pt3.x - 1.0) > 0.001) {
        out_c = clamp(mix(vec3(pt_luma(out_c)), out_c, u_pt3.x), 0.0, 1.0);
    }
    // Anti-banding dither.
    return out_c + (pt_hash(gl_FragCoord.xy) - 0.5) / 255.0;
}

void main() {
    vec2 uv = u_uv.xy + v_uv * u_uv.zw;
    vec2 f = fract(uv * u_src);
    vec3 c = texture2D(u_game, uv).rgb;
    bool inside = all(greaterThanEqual(uv, u_pic.xy)) && all(lessThan(uv, u_pic.zw));

    if (!inside) {
    } else if (u_mode > 11.5) {
        // Themaister sharp-bilinear: automatic prescale=3 at Slot 720x480.
        vec2 texel=uv*u_src;
        vec2 s=fract(texel);
        float scale=max(1.0,floor((u_rect.w*u_fbo.y)/u_src.y+0.01));
        float bound=0.5-0.5/scale;
        vec2 delta=s-vec2(0.5);
        vec2 filtered=(delta-clamp(delta,vec2(-bound),vec2(bound)))*scale+0.5;
        c=nearest_bilinear((floor(texel)+filtered)/u_src);
    } else if (u_mode > 10.5) {
        // endrift AGS-001: frontlit SP gamma, subpixel mask and light.
        c=ags_first_pass(uv);
        vec2 radius=pow(abs((v_uv-vec2(0.5))*1.5),vec2(4.0));
        vec3 bleed=vec3(0.12,0.14,0.19) +
            (dot(radius,radius)+vec3(0.02,0.03,0.05))*vec3(0.14,0.18,0.2);
        c+=bleed+0.07*ags_first_pass(uv-vec2(0.0,0.025));
    } else if (u_mode > 9.5) {
        // endrift AGB-001: original non-backlit screen color subpixels.
        float py=mod((uv*u_src).y*4.0,4.0);
        c=(pow(c*0.8,vec3(1.8))+vec3(0.16))*mgba_mask(uv)*
            (py>=3.0 ? 0.8 : 1.0);
    } else if (u_mode > 8.5) {
        // jdgleaver simpletex_lcd+gba-color: official grid parameters,
        // using Slot's existing licensed paper texture crop.
        c=gba_simpletex_color(c);
        vec2 dd=abs(f-vec2(0.5));
        float d2=max(dd.x,dd.y); d2*=d2;
        float d4=d2*d2;
        float weight=48.0*(d4-(8.0/3.0)*d4*d2);
        weight=weight*(weight+(1.0-weight)*0.65)*0.65;
        float lum=dot(c,luma_coeff);
        weight*=lum+(1.0-lum)*0.4;
        c=mix(c,vec3(1.0),weight);
        vec3 paper=texture2D(u_paper,
            (floor(gl_FragCoord.xy)+vec2(0.5))/u_paper_size).rgb;
        c=mix(c,paper*c,dot(c,luma_coeff));
    } else if (u_mode > 7.5) {
        // jdgleaver LCD1x: proper scanline alignment, no RGB separation.
        vec2 angle=6.283185307*(uv*u_src-vec2(0.25));
        c*=((16.0+sin(angle.y))/17.0)*((4.0+sin(angle.x))/5.0);
    } else if (u_mode > 6.5) {
        // SoltanGris42 zfast_lcd_standard default BORDERMULT=14,
        // GBAGAMMA=1, nearest sampling.
        vec2 px=uv*u_src;
        vec2 dist=abs(floor(px)+vec2(0.5)-px);
        float y=max(dist.x,dist.y); y*=y;
        float yy=y*y;
        float weight=1.0-14.0*(yy-2.7*yy*y);
        c*=weight;
        c*=0.6+0.4*c;
    } else if (u_mode > 5.5) {
        // CGWG LCD Grid V2 followed by RetroArch GBA Color.
        c=gba_color(lcd_grid_v2(uv,true));
    } else if (u_mode > 4.5) {
        c=lcd_grid_v2(uv,false);
    } else if (u_mode > 3.5) {
        c = pow(c, vec3(1.05));
        vec2 d = abs(f - 0.5);
        float x2 = max(d.x, d.y);
        x2 = x2 * x2;
        float x4 = x2 * x2;
        float w = 48.0 * (x4 - (8.0 / 3.0) * x4 * x2);
        w = w * (w + (1.0 - w) * 0.65) * 0.65;
        float luma = dot(c, luma_coeff);
        w = w * luma;
        c = mix(c, vec3(1.0), w);
        vec3 bg = texture2D(u_paper, (floor(gl_FragCoord.xy) + 0.5) / u_paper_size).rgb * c;
        c = mix(c, bg, dot(c, luma_coeff));
    } else if (u_mode > 2.5) {
        float bloom = mix(1.05, 0.95, dot(c, vec3(0.30, 0.59, 0.11)));
        float mid = dot_weight(f, vec2(0.0), bloom);
        float sum = 0.0;
        for (int j = -1; j <= 1; j++) {
            for (int i = -1; i <= 1; i++) {
                sum += dot_weight(f, vec2(float(i), float(j)), bloom);
            }
        }
        c = mix(1.2 * c * mid, c * sum, 0.65);
    } else if (u_mode > 1.5) {
        vec2 hp = 0.5 * u_uv.zw * u_src / (u_rect.zw * u_fbo);
        vec2 edge = max(step(f - hp, vec2(1e-4)), step(vec2(1.0) + 1e-4, f + hp));
        c *= 1.0 - 0.75 * max(edge.x, edge.y);
    } else if (u_mode > 0.5) {
        vec2 angle = f * 6.283185307;
        float yfactor = (16.0 + sin(angle.y)) / 17.0;
        vec3 xfactors = (4.0 + sin(angle.x + offsets)) / 5.0;
        c *= yfactor * xfactors;
    }
    // These two RetroArch presets already contain GBA Color. Never
    // unintentionally apply the global correction twice.
    if (u_colour_correct > 0.5 && inside &&
        !(u_mode > 5.5 && u_mode < 6.5) &&
        !(u_mode > 8.5 && u_mode < 9.5)) {
        // GBA-era display simulation: subtly warmer, lower-saturation
        // colours and a restrained luminance response. GPU only; libretro
        // framebuffer, SRAM and save states remain untouched.
        float luminance = dot(c, vec3(0.299, 0.587, 0.114));
        c = mix(vec3(luminance), c, 0.80);
        c *= vec3(1.02, 1.00, 0.95);
        c = pow(clamp(c, 0.0, 1.0), vec3(1.05));
    }
    if (u_pt0.x > 0.5 && inside) {
        // The part of the picture this quad shows, in texture and in
        // framebuffer pixels: borders (Original Ratio) are not picture.
        vec2 lo = max(u_pic.xy, u_uv.xy);
        vec2 span = max(min(u_pic.zw, u_uv.xy + u_uv.zw) - lo, vec2(1e-6));
        vec2 px = max(u_rect.zw * u_fbo * span / u_uv.zw, vec2(1.0));
        // RetroArch hands the last pass an 8-bit framebuffer.
        c = pixel_transparency(clamp(c, 0.0, 1.0), texture2D(u_game, uv).rgb,
                               (uv - lo) / span, lo, span, px);
    }
    FRAG_COLOR = vec4(c * u_bright, 1.0);
}
"#;

pub const SPRITE_FRAG: &str = r#"
precision mediump float;
uniform sampler2D u_tex;
uniform vec4 u_colour;
varying vec2 v_uv;
void main() {
    FRAG_COLOR = texture2D(u_tex, v_uv) * u_colour;
}
"#;

pub const BLIT_VERT: &str = r#"
attribute vec2 a_pos;
uniform vec4 u_uv;
varying vec2 v_uv;
void main() {
    v_uv = u_uv.xy + a_pos * u_uv.zw;
    gl_Position = vec4(a_pos * 2.0 - 1.0, 0.0, 1.0);
}
"#;

pub const BLIT_FRAG: &str = r#"
precision mediump float;
uniform sampler2D u_tex;
uniform vec3 u_gain;
varying vec2 v_uv;
void main() {
    FRAG_COLOR = vec4(texture2D(u_tex, v_uv).rgb * u_gain, 1.0);
}
"#;

pub const BEZEL_FRAG: &str = r#"
precision mediump float;
uniform sampler2D u_tex;
uniform vec3 u_gain;
uniform float u_alpha;
varying vec2 v_uv;
void main() {
    vec4 c = texture2D(u_tex, v_uv);
    FRAG_COLOR = vec4(c.rgb * u_gain, c.a * u_alpha);
}
"#;

//! Pixel Transparency by mattakins (github.com/mattakins/Pixel_Transparency),
//! v2.3: the light pixels of an unlit Game Boy Color / Game Boy Advance
//! screen let the backing material show through, with drop shadows cast on
//! that backing. RetroArch appends it as the last pass of an LCD preset;
//! slot. runs it at the end of the single game pass, after the chosen
//! screen shader, so it combines with every shader (or none).
//!
//! The values here are in the shader's own units, exactly as its
//! `#pragma parameter` lines define them. Defaults are those of the
//! `pixel_transparency.slangp` preset.

/// One set of Pixel Transparency parameters, ready for the GPU.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct PixelTransparency {
    /// PT_PIXEL_MODE: 0 white pixels only, 1 bright pixels, 2 all pixels.
    pub transparent_pixels: f32,
    /// PT_BASE_ALPHA, 0..1.
    pub amount: f32,
    /// PT_THRESHOLD, 0..1.
    pub white_threshold: f32,
    /// PT_WHITE_TRANSPARENCY when PT_WHITE_BOOST is on; 0 is off.
    pub white_boost: f32,
    /// PT_BRIGHTNESS_MODE: false Simple (RGB average), true Natural (BT.709).
    pub natural_brightness: bool,
    /// PT_GRID_COLOR_MIX, 0..1.
    pub grid_on_colors: f32,
    /// PT_GRID_WHITE_MIX, 0..1.
    pub grid_on_whites: f32,
    /// PT_PALETTE: 0 off, 1 Warm 1, 2 Warm 2, 3 Neutral, 4 Aluminum.
    pub backing_tint: f32,
    /// PT_PALETTE_INTENSITY, 0..2.
    pub tint_intensity: f32,
    /// PT_BACKING_BRIGHTNESS, 0.20..0.65.
    pub backing_brightness: f32,
    /// PT_POLARIZER_TINT when PT_POLARIZER is on; 0 is off.
    pub polarizer: f32,
    /// PT_SATURATION, 0.5..1.5.
    pub saturation: f32,
    /// PT_HIGHLIGHTS, -1..1.
    pub pixel_brightness: f32,
    /// PT_PAPER_NOISE_FAST: false Fast (2 octaves), true Full (3 octaves).
    pub full_texture: bool,
    /// PT_SHADOW_FAST_BLUR: false Fast (5 taps), true Smooth (9 taps).
    pub smooth_blur: bool,
    /// PT_SHADOW_ENABLE.
    pub shadows: bool,
    /// PT_SHADOW_EDGE_ENABLE: the screen's edge casts a shadow too.
    pub edge_shadow: bool,
    /// PT_SHADOW_OPACITY, 0..1.
    pub shadow_opacity: f32,
    /// PT_SHADOW_BLUR, 0..5.
    pub shadow_blur: f32,
    /// The shadow offset the shader's vertex stage would compute, in its
    /// 640x480 reference pixels: PT_SHADOW_OFFSET_X/Y, or with motion the
    /// neutral offset plus the tilt times PT_SHADOW_MAX_OFFSET.
    pub shadow_offset: [f32; 2],
}

impl Default for PixelTransparency {
    fn default() -> Self {
        Self {
            transparent_pixels: 1.0,
            amount: 0.20,
            white_threshold: 0.90,
            white_boost: 0.0,
            natural_brightness: true,
            grid_on_colors: 0.0,
            grid_on_whites: 0.0,
            backing_tint: 1.0,
            tint_intensity: 1.0,
            backing_brightness: 0.48,
            polarizer: 0.0,
            saturation: 1.0,
            pixel_brightness: 0.0,
            full_texture: true,
            smooth_blur: true,
            shadows: true,
            edge_shadow: true,
            shadow_opacity: 0.50,
            shadow_blur: 1.0,
            shadow_offset: [3.0, 3.0],
        }
    }
}

fn flag(on: bool) -> f32 {
    if on {
        1.0
    } else {
        0.0
    }
}

impl PixelTransparency {
    /// The five packed `u_pt0..u_pt4` vectors of the game shader. The first
    /// component of `u_pt0` switches the pass on.
    pub fn uniforms(&self) -> [[f32; 4]; 5] {
        [
            [
                1.0,
                self.transparent_pixels,
                self.amount,
                self.white_threshold,
            ],
            [
                self.white_boost,
                flag(self.natural_brightness),
                self.grid_on_colors,
                self.grid_on_whites,
            ],
            [
                self.backing_tint,
                self.tint_intensity,
                self.backing_brightness,
                self.polarizer,
            ],
            [
                self.saturation,
                self.pixel_brightness,
                flag(self.full_texture),
                flag(self.smooth_blur),
            ],
            [
                flag(self.shadows),
                flag(self.edge_shadow),
                self.shadow_opacity,
                self.shadow_blur,
            ],
        ]
    }

    /// The uniforms when the pass is off: only the switch is read.
    pub const OFF: [[f32; 4]; 5] = [[0.0; 4]; 5];
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_follow_the_pixel_transparency_preset() {
        let pt = PixelTransparency::default();
        // pixel_transparency.slangp overrides the shader's own defaults.
        assert_eq!(pt.backing_tint, 1.0, "PT_PALETTE = 1.0");
        assert_eq!(pt.polarizer, 0.0, "PT_POLARIZER = 0.0");
        assert_eq!(pt.pixel_brightness, 0.0, "PT_HIGHLIGHTS = 0.0");
        assert_eq!(pt.shadow_offset, [3.0, 3.0]);
    }

    #[test]
    fn packing_puts_the_switch_first_and_flags_as_floats() {
        let u = PixelTransparency {
            natural_brightness: false,
            full_texture: false,
            ..Default::default()
        }
        .uniforms();
        assert_eq!(u[0][0], 1.0);
        assert_eq!(u[1][1], 0.0);
        assert_eq!(u[3][2], 0.0);
        assert_eq!(u[3][3], 1.0);
        assert_eq!(u[4], [1.0, 1.0, 0.5, 1.0]);
        assert_eq!(PixelTransparency::OFF[0][0], 0.0);
    }
}

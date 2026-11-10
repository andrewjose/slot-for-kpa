//! The LCD Effect's parameters (Matt Akins' Pixel Transparency and
//! Sunlight Shimmer v3.0), in the shader's own units. The defaults are the
//! values slot. draws the effect with: the shader's own defaults, as
//! upstream slot. for Android chose them.

/// One set of LCD Effect parameters, ready for the GPU.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct LcdParams {
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
    /// PT_BACKING_BRIGHTNESS, 0.20..0.65.
    pub backing_brightness: f32,
    /// PT_PALETTE: 0 off, 1 Warm 1, 2 Warm 2, 3 Neutral, 4 Aluminum.
    pub backing_tint: f32,
    /// PT_PALETTE_INTENSITY, 0..2.
    pub tint_intensity: f32,
    /// PT_POLARIZER_TINT when PT_POLARIZER is on; 0 is off.
    pub polarizer: f32,
    /// PT_SATURATION, 0.5..1.5.
    pub saturation: f32,
    /// PT_HIGHLIGHTS, -1..1.
    pub pixel_brightness: f32,
    /// PT_PAPER_NOISE_FAST: false Fast (2 octaves), true Full (3 octaves).
    pub full_texture: bool,
    /// PT_SHADOW_ENABLE.
    pub shadows: bool,
    /// PT_SHADOW_EDGE_ENABLE: the screen's edge casts a shadow too.
    pub edge_shadow: bool,
    /// PT_SHADOW_OPACITY, 0..1.
    pub shadow_opacity: f32,
    /// PT_SHADOW_BLUR, 0..5.
    pub shadow_blur: f32,
    /// PT_SHADOW_OFFSET_X / Y, in 640x480 reference pixels.
    pub shadow_offset: [f32; 2],
    /// Sunlight Shimmer, PT_RAINBOW_INTENSITY; 0 is off.
    pub rainbow: f32,
    /// PT_LIGHT_SPREAD, 0.5..4.
    pub band_density: f32,
    /// PT_FILM_NOISE, 0..1.
    pub film_noise: f32,
    /// PT_FILM_SCALE, 0.1..3.
    pub film_scale: f32,
    /// PT_GLARE_INTENSITY; 0 is off.
    pub glare: f32,
    /// PT_GLARE_SIZE, 0.05..1.
    pub glare_spread: f32,
    /// PT_FILM_TUNING, 0.5..2.
    pub colour_shift: f32,
}

impl Default for LcdParams {
    fn default() -> Self {
        Self {
            transparent_pixels: 1.0,
            amount: 0.20,
            white_threshold: 0.90,
            white_boost: 0.0,
            natural_brightness: true,
            backing_brightness: 0.48,
            backing_tint: 3.0,
            tint_intensity: 1.0,
            polarizer: 1.0,
            saturation: 1.0,
            pixel_brightness: 0.05,
            full_texture: true,
            shadows: true,
            edge_shadow: true,
            shadow_opacity: 0.50,
            shadow_blur: 2.0,
            shadow_offset: [3.0, 3.0],
            rainbow: 0.15,
            band_density: 1.8,
            film_noise: 0.5,
            film_scale: 0.6,
            glare: 0.15,
            glare_spread: 0.25,
            colour_shift: 1.0,
        }
    }
}

fn flag(on: bool) -> f32 {
    if on { 1.0 } else { 0.0 }
}

impl LcdParams {
    /// The six packed `u_pt0..u_pt5` vectors of the game shader.
    pub fn uniforms(&self) -> [[f32; 4]; 6] {
        [
            [self.transparent_pixels, self.amount, self.white_threshold, self.white_boost],
            [flag(self.natural_brightness), self.backing_brightness, self.backing_tint, self.tint_intensity],
            [self.polarizer, self.saturation, self.pixel_brightness, flag(self.full_texture)],
            [flag(self.shadows), flag(self.edge_shadow), self.shadow_opacity, self.shadow_blur],
            [self.rainbow, self.band_density, self.film_noise, self.film_scale],
            [self.glare, self.glare_spread, self.colour_shift, 0.0],
        ]
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_are_the_constants_slot_drew_with() {
        let u = LcdParams::default().uniforms();
        assert_eq!(u[0], [1.0, 0.20, 0.90, 0.0], "Bright mode, 0.20, threshold 0.90");
        assert_eq!(u[1], [1.0, 0.48, 3.0, 1.0], "Natural, 0.48, Neutral tint");
        assert_eq!(u[2], [1.0, 1.0, 0.05, 1.0], "polarizer on, highlights 0.05");
        assert_eq!(u[3], [1.0, 1.0, 0.50, 2.0], "shadow opacity 0.5, blur 2");
        assert_eq!(u[4], [0.15, 1.8, 0.5, 0.6]);
        assert_eq!(u[5], [0.15, 0.25, 1.0, 0.0]);
    }
}

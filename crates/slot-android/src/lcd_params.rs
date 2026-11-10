//! The LCD Effect's parameters (Matt Akins' Pixel Transparency and
//! Sunlight Shimmer v3.0): one shared set, and the menu pages that edit
//! them. Each Game Boy system picks its LCD Effect on its own (Settings >
//! Systems > LCD Effect); A on that row opens these pages.
//!
//! Values are kept as whole steps (hundredths, tenths or a choice) so the
//! menu moves in exact increments and Settings keeps `Eq`. `shader()` turns
//! them into the shader's own units.
use serde::{Deserialize, Serialize};
use slot_gfx::LcdParams;

/// Defaults are the values slot. draws the effect with (slot_gfx::LcdParams).
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct LcdSettings {
    /// 0 white pixels only, 1 bright pixels, 2 all pixels.
    pub transparent_pixels: u8,
    pub transparency: i16,    // hundredths
    pub white_threshold: i16, // hundredths
    pub white_boost: i16,     // hundredths, 0 = OFF
    pub natural_brightness: bool,
    /// 0 OFF, 1 Warm 1, 2 Warm 2, 3 Neutral, 4 Aluminum.
    pub backing_tint: u8,
    pub tint_intensity: i16,     // hundredths, 0..=200
    pub backing_brightness: i16, // hundredths, 20..=65
    pub polarizer: i16,          // hundredths, 0 = OFF
    pub saturation: i16,         // hundredths, 50..=150
    pub pixel_brightness: i16,   // hundredths, -100..=100
    pub full_texture: bool,
    pub shadows: bool,
    pub edge_shadow: bool,
    pub shadow_x: i16,       // tenths of a reference pixel
    pub shadow_y: i16,       // tenths
    pub shadow_opacity: i16, // hundredths
    pub shadow_blur: i16,    // tenths
    /// Sunlight Shimmer.
    pub rainbow: i16,      // hundredths, 0 = OFF
    pub colour_shift: i16, // hundredths, 50..=200
    pub band_density: i16, // tenths, 5..=40
    pub film_noise: i16,   // hundredths
    pub film_scale: i16,   // hundredths, 10..=300
    pub glare: i16,        // hundredths, 0 = OFF
    pub glare_spread: i16, // hundredths, 5..=100
}

impl Default for LcdSettings {
    fn default() -> Self {
        Self {
            transparent_pixels: 1,
            transparency: 20,
            white_threshold: 90,
            white_boost: 0,
            natural_brightness: true,
            backing_tint: 3,
            tint_intensity: 100,
            backing_brightness: 48,
            polarizer: 100,
            saturation: 100,
            pixel_brightness: 5,
            full_texture: true,
            shadows: true,
            edge_shadow: true,
            shadow_x: 30,
            shadow_y: 30,
            shadow_opacity: 50,
            shadow_blur: 20,
            rainbow: 15,
            colour_shift: 100,
            band_density: 18,
            film_noise: 50,
            film_scale: 60,
            glare: 15,
            glare_spread: 25,
        }
    }
}

/// One page of the LCD EFFECT menu, in the shader's own sections. Six
/// rows at most, like every slot. settings page.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LcdPage {
    Transparency,
    Color,
    Shadows,
    /// Sunlight Shimmer's rainbow.
    Sunlight,
    /// Sunlight Shimmer's glare, and the backing's texture.
    GlareTexture,
}

impl LcdPage {
    pub const ALL: [Self; 5] =
        [Self::Transparency, Self::Color, Self::Shadows, Self::Sunlight, Self::GlareTexture];

    /// The page's row in the LCD EFFECT list.
    pub fn index(self) -> usize {
        Self::ALL.iter().position(|p| *p == self).unwrap_or(0)
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Transparency => "Transparency",
            Self::Color => "Color",
            Self::Shadows => "Shadows",
            Self::Sunlight => "Sunlight Shimmer",
            Self::GlareTexture => "Glare & Texture",
        }
    }

    pub fn title(self) -> &'static str {
        match self {
            Self::Transparency => "TRANSPARENCY",
            Self::Color => "COLOR",
            Self::Shadows => "SHADOWS",
            Self::Sunlight => "SUNLIGHT SHIMMER",
            Self::GlareTexture => "GLARE & TEXTURE",
        }
    }

    pub fn fields(self) -> &'static [LcdField] {
        use LcdField::*;
        match self {
            Self::Transparency => &[
                TransparentPixels, Transparency, WhiteThreshold, WhiteBoost, BrightnessMode,
            ],
            Self::Color => &[
                BackingTint, TintIntensity, BackingBrightness, Polarizer,
                Saturation, PixelBrightness,
            ],
            Self::Shadows => &[Shadows, EdgeShadow, OffsetX, OffsetY, Opacity, Blur],
            Self::Sunlight => &[Rainbow, ColourShift, BandDensity, FilmNoise, FilmScale],
            Self::GlareTexture => &[Glare, GlareSpread, BackgroundTexture],
        }
    }
}

/// Every parameter the menu shows, one row each.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LcdField {
    TransparentPixels,
    Transparency,
    WhiteThreshold,
    WhiteBoost,
    BrightnessMode,
    BackingTint,
    TintIntensity,
    BackingBrightness,
    Polarizer,
    Saturation,
    PixelBrightness,
    Shadows,
    EdgeShadow,
    OffsetX,
    OffsetY,
    Opacity,
    Blur,
    Rainbow,
    ColourShift,
    BandDensity,
    FilmNoise,
    FilmScale,
    Glare,
    GlareSpread,
    BackgroundTexture,
}

const ON_OFF: &[&str] = &["OFF", "ON"];

enum Kind {
    /// Wraps around, like slot.'s other choices.
    Choice(&'static [&'static str]),
    /// Stops at either end. `point` digits after the decimal point.
    Number { min: i16, max: i16, step: i16, point: u8, off_at_zero: bool },
}

const fn hundredths(min: i16, max: i16, step: i16) -> Kind {
    Kind::Number { min, max, step, point: 2, off_at_zero: false }
}
const fn tenths(min: i16, max: i16, step: i16) -> Kind {
    Kind::Number { min, max, step, point: 1, off_at_zero: false }
}
const fn optional(max: i16, step: i16) -> Kind {
    Kind::Number { min: 0, max, step, point: 2, off_at_zero: true }
}

impl LcdField {
    pub const ALL: [Self; 25] = {
        use LcdField::*;
        [
            TransparentPixels, Transparency, WhiteThreshold, WhiteBoost, BrightnessMode,
            BackingTint, TintIntensity, BackingBrightness, Polarizer, Saturation,
            PixelBrightness, Shadows, EdgeShadow, OffsetX, OffsetY, Opacity, Blur,
            Rainbow, ColourShift, BandDensity, FilmNoise, FilmScale, Glare, GlareSpread,
            BackgroundTexture,
        ]
    };

    /// The field's place in `ALL`.
    pub fn index(self) -> usize {
        Self::ALL.iter().position(|f| *f == self).unwrap_or(0)
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::TransparentPixels => "Transparent Pixels",
            Self::Transparency => "Transparency",
            Self::WhiteThreshold => "White Threshold",
            Self::WhiteBoost => "White Boost",
            Self::BrightnessMode => "Brightness Mode",
            Self::BackingTint => "Backing Tint",
            Self::TintIntensity => "Tint Intensity",
            Self::BackingBrightness => "Backing Brightness",
            Self::Polarizer => "Polarizer Tint",
            Self::Saturation => "Saturation",
            Self::PixelBrightness => "Pixel Brightness",
            Self::Shadows => "Shadows",
            Self::EdgeShadow => "Screen Edge Shadow",
            Self::OffsetX => "X Offset",
            Self::OffsetY => "Y Offset",
            Self::Opacity => "Opacity",
            Self::Blur => "Blur",
            Self::Rainbow => "Rainbow",
            Self::ColourShift => "Color Shift",
            Self::BandDensity => "Band Density",
            Self::FilmNoise => "Film Splotches",
            Self::FilmScale => "Splotch Size",
            Self::Glare => "Glare",
            Self::GlareSpread => "Glare Spread",
            Self::BackgroundTexture => "Backing Texture",
        }
    }

    /// Ranges and steps of the shader's own #pragma parameter lines.
    fn kind(self) -> Kind {
        match self {
            Self::TransparentPixels => Kind::Choice(&["WHITE ONLY", "BRIGHT", "ALL"]),
            Self::Transparency | Self::WhiteThreshold | Self::Opacity => hundredths(0, 100, 1),
            Self::WhiteBoost => optional(100, 5),
            Self::BrightnessMode => Kind::Choice(&["SIMPLE", "NATURAL"]),
            Self::BackingTint => {
                Kind::Choice(&["OFF", "WARM 1", "WARM 2", "NEUTRAL", "ALUMINUM"])
            }
            Self::TintIntensity => hundredths(0, 200, 5),
            Self::BackingBrightness => hundredths(20, 65, 1),
            Self::Polarizer => optional(200, 5),
            Self::Saturation => hundredths(50, 150, 5),
            Self::PixelBrightness => hundredths(-100, 100, 1),
            Self::Shadows | Self::EdgeShadow => Kind::Choice(ON_OFF),
            Self::OffsetX | Self::OffsetY => tenths(-300, 300, 5),
            Self::Blur => tenths(0, 50, 1),
            Self::Rainbow => optional(100, 1),
            Self::ColourShift => hundredths(50, 200, 5),
            Self::BandDensity => tenths(5, 40, 1),
            Self::FilmNoise => hundredths(0, 100, 5),
            Self::FilmScale => hundredths(10, 300, 5),
            Self::Glare => optional(100, 5),
            Self::GlareSpread => hundredths(5, 100, 5),
            Self::BackgroundTexture => Kind::Choice(&["FAST", "FULL"]),
        }
    }

    pub fn get(self, p: &LcdSettings) -> i16 {
        match self {
            Self::TransparentPixels => i16::from(p.transparent_pixels),
            Self::Transparency => p.transparency,
            Self::WhiteThreshold => p.white_threshold,
            Self::WhiteBoost => p.white_boost,
            Self::BrightnessMode => i16::from(p.natural_brightness),
            Self::BackingTint => i16::from(p.backing_tint),
            Self::TintIntensity => p.tint_intensity,
            Self::BackingBrightness => p.backing_brightness,
            Self::Polarizer => p.polarizer,
            Self::Saturation => p.saturation,
            Self::PixelBrightness => p.pixel_brightness,
            Self::Shadows => i16::from(p.shadows),
            Self::EdgeShadow => i16::from(p.edge_shadow),
            Self::OffsetX => p.shadow_x,
            Self::OffsetY => p.shadow_y,
            Self::Opacity => p.shadow_opacity,
            Self::Blur => p.shadow_blur,
            Self::Rainbow => p.rainbow,
            Self::ColourShift => p.colour_shift,
            Self::BandDensity => p.band_density,
            Self::FilmNoise => p.film_noise,
            Self::FilmScale => p.film_scale,
            Self::Glare => p.glare,
            Self::GlareSpread => p.glare_spread,
            Self::BackgroundTexture => i16::from(p.full_texture),
        }
    }

    fn set(self, p: &mut LcdSettings, v: i16) {
        let small = v.clamp(0, 255) as u8;
        match self {
            Self::TransparentPixels => p.transparent_pixels = small,
            Self::Transparency => p.transparency = v,
            Self::WhiteThreshold => p.white_threshold = v,
            Self::WhiteBoost => p.white_boost = v,
            Self::BrightnessMode => p.natural_brightness = v != 0,
            Self::BackingTint => p.backing_tint = small,
            Self::TintIntensity => p.tint_intensity = v,
            Self::BackingBrightness => p.backing_brightness = v,
            Self::Polarizer => p.polarizer = v,
            Self::Saturation => p.saturation = v,
            Self::PixelBrightness => p.pixel_brightness = v,
            Self::Shadows => p.shadows = v != 0,
            Self::EdgeShadow => p.edge_shadow = v != 0,
            Self::OffsetX => p.shadow_x = v,
            Self::OffsetY => p.shadow_y = v,
            Self::Opacity => p.shadow_opacity = v,
            Self::Blur => p.shadow_blur = v,
            Self::Rainbow => p.rainbow = v,
            Self::ColourShift => p.colour_shift = v,
            Self::BandDensity => p.band_density = v,
            Self::FilmNoise => p.film_noise = v,
            Self::FilmScale => p.film_scale = v,
            Self::Glare => p.glare = v,
            Self::GlareSpread => p.glare_spread = v,
            Self::BackgroundTexture => p.full_texture = v != 0,
        }
    }

    /// The nearest allowed value: in range, on a step.
    fn allowed(self, v: i16) -> i16 {
        match self.kind() {
            Kind::Choice(names) => v.clamp(0, names.len() as i16 - 1),
            Kind::Number { min, max, step, .. } => {
                let v = v.clamp(min, max);
                min + (v - min) / step * step
            }
        }
    }

    /// One press of Left or Right. Returns whether the value moved.
    pub fn step(self, p: &mut LcdSettings, right: bool) -> bool {
        let now = self.get(p);
        let next = match self.kind() {
            Kind::Choice(names) => {
                let n = names.len() as i16;
                (now + if right { 1 } else { n - 1 }) % n
            }
            Kind::Number { min, max, step, .. } => {
                (if right { now + step } else { now - step }).clamp(min, max)
            }
        };
        self.set(p, next);
        next != now
    }

    /// The value as the menu shows it.
    pub fn text(self, p: &LcdSettings) -> String {
        let v = self.get(p);
        match self.kind() {
            Kind::Choice(names) => names[v.clamp(0, names.len() as i16 - 1) as usize].into(),
            Kind::Number { off_at_zero: true, .. } if v == 0 => "OFF".into(),
            Kind::Number { point, .. } => {
                let scale = if point == 1 { 10.0 } else { 100.0 };
                format!("{:.*}", point as usize, f32::from(v) / scale)
            }
        }
    }
}

impl LcdSettings {
    /// Values from an edited or older Settings file, put back in range.
    pub fn sanitize(mut self) -> Self {
        for field in LcdField::ALL {
            let v = field.allowed(field.get(&self));
            field.set(&mut self, v);
        }
        self
    }

    /// The parameters in the shader's units.
    pub fn shader(&self) -> LcdParams {
        let h = |v: i16| f32::from(v) / 100.0;
        let t = |v: i16| f32::from(v) / 10.0;
        LcdParams {
            transparent_pixels: f32::from(self.transparent_pixels),
            amount: h(self.transparency),
            white_threshold: h(self.white_threshold),
            white_boost: h(self.white_boost),
            natural_brightness: self.natural_brightness,
            backing_brightness: h(self.backing_brightness),
            backing_tint: f32::from(self.backing_tint),
            tint_intensity: h(self.tint_intensity),
            polarizer: h(self.polarizer),
            saturation: h(self.saturation),
            pixel_brightness: h(self.pixel_brightness),
            full_texture: self.full_texture,
            shadows: self.shadows,
            edge_shadow: self.edge_shadow,
            shadow_opacity: h(self.shadow_opacity),
            shadow_blur: t(self.shadow_blur),
            shadow_offset: [t(self.shadow_x), t(self.shadow_y)],
            rainbow: h(self.rainbow),
            band_density: t(self.band_density),
            film_noise: h(self.film_noise),
            film_scale: h(self.film_scale),
            glare: h(self.glare),
            glare_spread: h(self.glare_spread),
            colour_shift: h(self.colour_shift),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_draw_the_effect_as_before() {
        assert_eq!(LcdSettings::default().shader(), LcdParams::default());
    }

    #[test]
    fn every_field_appears_on_exactly_one_page_of_at_most_six_rows() {
        for field in LcdField::ALL {
            let pages = LcdPage::ALL.iter().filter(|p| p.fields().contains(&field)).count();
            assert_eq!(pages, 1, "{field:?}");
        }
        assert!(LcdPage::ALL.iter().all(|p| p.fields().len() <= 6));
        // The page list plus Reset to Defaults is itself one page.
        assert!(LcdPage::ALL.len() < 6);
        assert!(LcdPage::ALL.iter().enumerate().all(|(i, p)| p.index() == i));
        assert!(LcdField::ALL.iter().enumerate().all(|(i, f)| f.index() == i));
    }

    #[test]
    fn numbers_stop_at_their_ends_and_choices_wrap() {
        let mut p = LcdSettings { transparency: 99, ..Default::default() };
        assert!(LcdField::Transparency.step(&mut p, true));
        assert_eq!(p.transparency, 100);
        assert!(!LcdField::Transparency.step(&mut p, true), "stays at 1.00");
        assert_eq!(LcdField::Transparency.text(&p), "1.00");
        p.backing_tint = 4;
        assert!(LcdField::BackingTint.step(&mut p, true));
        assert_eq!(LcdField::BackingTint.text(&p), "OFF");
        assert!(LcdField::BackingTint.step(&mut p, false));
        assert_eq!(LcdField::BackingTint.text(&p), "ALUMINUM");
        assert!(LcdField::Shadows.step(&mut p, false));
        assert_eq!(LcdField::Shadows.text(&p), "OFF");
    }

    #[test]
    fn values_read_like_the_shader_parameters() {
        let mut p = LcdSettings::default();
        assert_eq!(LcdField::OffsetX.text(&p), "3.0");
        assert_eq!(LcdField::BackingBrightness.text(&p), "0.48");
        assert_eq!(LcdField::BackingTint.text(&p), "NEUTRAL");
        assert_eq!(LcdField::Polarizer.text(&p), "1.00");
        assert_eq!(LcdField::WhiteBoost.text(&p), "OFF");
        LcdField::WhiteBoost.step(&mut p, true);
        assert_eq!(LcdField::WhiteBoost.text(&p), "0.05");
        for _ in 0..30 {
            LcdField::OffsetX.step(&mut p, false);
        }
        assert_eq!(LcdField::OffsetX.text(&p), "-12.0");
        for _ in 0..10 {
            LcdField::PixelBrightness.step(&mut p, false);
        }
        assert_eq!(LcdField::PixelBrightness.text(&p), "-0.05");
        assert_eq!(LcdField::BandDensity.text(&p), "1.8");
        for _ in 0..15 {
            LcdField::Rainbow.step(&mut p, false);
        }
        assert_eq!(LcdField::Rainbow.text(&p), "OFF");
    }

    #[test]
    fn edited_settings_files_come_back_in_range_and_on_a_step() {
        let p = LcdSettings {
            transparency: 400,
            backing_tint: 9,
            shadow_x: 33,
            film_scale: 0,
            ..Default::default()
        }
        .sanitize();
        assert_eq!(p.transparency, 100);
        assert_eq!(p.backing_tint, 4);
        assert_eq!(p.shadow_x, 30, "offsets move by 0.5");
        assert_eq!(p.film_scale, 10);
        assert_eq!(LcdSettings::default().sanitize(), LcdSettings::default());
    }

    #[test]
    fn missing_fields_keep_their_defaults() {
        let p: LcdSettings = serde_json::from_str(r#"{"transparency": 35}"#).unwrap();
        assert_eq!(p, LcdSettings { transparency: 35, ..Default::default() });
    }
}

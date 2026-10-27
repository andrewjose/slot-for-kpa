//! Pixel Transparency (mattakins) settings: one shared set of shader
//! parameters, and the menu pages that edit them. Each system turns the
//! effect ON or OFF on its own (Settings, and per game from the pause menu).
//!
//! Values are kept as whole steps (hundredths, tenths or a choice) so the
//! menu moves in exact increments and Settings keeps `Eq`. `shader()` turns
//! them into the shader's own units.
use serde::{Deserialize, Serialize};
use slot_gfx::PixelTransparency;

/// Defaults are the `pixel_transparency.slangp` preset's.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Serialize, Deserialize)]
#[serde(default)]
pub struct PtParams {
    /// 0 OFF, 1 Warm 1, 2 Warm 2, 3 Neutral, 4 Aluminum.
    pub backing_tint: u8,
    pub tint_intensity: i16,     // hundredths, 0..=200
    pub backing_brightness: i16, // hundredths, 20..=65
    pub polarizer: i16,          // hundredths, 0 = OFF
    pub saturation: i16,         // hundredths, 50..=150
    pub pixel_brightness: i16,   // hundredths, -100..=100
    pub full_texture: bool,
    /// 0 white pixels only, 1 bright pixels, 2 all pixels.
    pub transparent_pixels: u8,
    pub transparency: i16,    // hundredths
    pub white_threshold: i16, // hundredths
    pub white_boost: i16,     // hundredths, 0 = OFF
    pub natural_brightness: bool,
    pub grid_on_colors: i16, // hundredths
    pub grid_on_whites: i16, // hundredths
    pub shadows: bool,
    pub edge_shadow: bool,
    pub shadow_x: i16,       // tenths of a reference pixel
    pub shadow_y: i16,       // tenths
    pub shadow_opacity: i16, // hundredths
    pub shadow_blur: i16,    // tenths
    pub smooth_blur: bool,
    /// Shadows follow the handheld's tilt (accelerometer).
    pub motion: bool,
    pub tilt_sensitivity: i16, // hundredths, 10..=500
    pub max_offset: i16,       // tenths
    pub neutral_x: i16,        // tenths
    pub neutral_y: i16,        // tenths
}

impl Default for PtParams {
    fn default() -> Self {
        Self {
            backing_tint: 1,
            tint_intensity: 100,
            backing_brightness: 48,
            polarizer: 0,
            saturation: 100,
            pixel_brightness: 0,
            full_texture: true,
            transparent_pixels: 1,
            transparency: 20,
            white_threshold: 90,
            white_boost: 0,
            natural_brightness: true,
            grid_on_colors: 0,
            grid_on_whites: 0,
            shadows: true,
            edge_shadow: true,
            shadow_x: 30,
            shadow_y: 30,
            shadow_opacity: 50,
            shadow_blur: 10,
            smooth_blur: true,
            motion: true,
            tilt_sensitivity: 100,
            max_offset: 25,
            neutral_x: 20,
            neutral_y: 20,
        }
    }
}

/// One page of the PIXEL TRANSPARENCY menu, in the shader's own sections.
/// Six rows at most, like every slot. settings page.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PtPage {
    Color,
    Transparency,
    Shadows,
    Motion,
    /// Grid visibility and the shader's two PERFORMANCE choices.
    GridQuality,
}

impl PtPage {
    pub const ALL: [Self; 5] =
        [Self::Color, Self::Transparency, Self::Shadows, Self::Motion, Self::GridQuality];

    /// The page's row in the PIXEL TRANSPARENCY list.
    pub fn index(self) -> usize {
        Self::ALL.iter().position(|p| *p == self).unwrap_or(0)
    }

    pub fn name(self) -> &'static str {
        match self {
            Self::Color => "Color",
            Self::Transparency => "Transparency",
            Self::Shadows => "Shadows",
            Self::Motion => "Motion",
            Self::GridQuality => "Grid & Quality",
        }
    }

    pub fn title(self) -> &'static str {
        match self {
            Self::Color => "COLOR",
            Self::Transparency => "TRANSPARENCY",
            Self::Shadows => "SHADOWS",
            Self::Motion => "MOTION",
            Self::GridQuality => "GRID & QUALITY",
        }
    }

    pub fn fields(self) -> &'static [PtField] {
        use PtField::*;
        match self {
            Self::Color => &[
                BackingTint, TintIntensity, BackingBrightness, Polarizer,
                Saturation, PixelBrightness,
            ],
            Self::Transparency => &[
                TransparentPixels, Transparency, WhiteThreshold, WhiteBoost, BrightnessMode,
            ],
            Self::Shadows => &[Shadows, EdgeShadow, OffsetX, OffsetY, Opacity, Blur],
            Self::Motion => &[Motion, TiltSensitivity, MaxOffset, NeutralX, NeutralY],
            Self::GridQuality => &[GridOnColors, GridOnWhites, BackgroundTexture, BlurQuality],
        }
    }
}

/// Every parameter the menu shows, one row each.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum PtField {
    BackingTint,
    TintIntensity,
    BackingBrightness,
    Polarizer,
    Saturation,
    PixelBrightness,
    BackgroundTexture,
    TransparentPixels,
    Transparency,
    WhiteThreshold,
    WhiteBoost,
    BrightnessMode,
    GridOnColors,
    GridOnWhites,
    Shadows,
    EdgeShadow,
    OffsetX,
    OffsetY,
    Opacity,
    Blur,
    BlurQuality,
    Motion,
    TiltSensitivity,
    MaxOffset,
    NeutralX,
    NeutralY,
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

impl PtField {
    pub const ALL: [Self; 26] = {
        use PtField::*;
        [
            BackingTint, TintIntensity, BackingBrightness, Polarizer, Saturation,
            PixelBrightness, BackgroundTexture, TransparentPixels, Transparency,
            WhiteThreshold, WhiteBoost, BrightnessMode, GridOnColors, GridOnWhites,
            Shadows, EdgeShadow, OffsetX, OffsetY, Opacity, Blur, BlurQuality,
            Motion, TiltSensitivity, MaxOffset, NeutralX, NeutralY,
        ]
    };

    /// The field's place in `ALL`.
    pub fn index(self) -> usize {
        Self::ALL.iter().position(|f| *f == self).unwrap_or(0)
    }

    pub fn label(self) -> &'static str {
        match self {
            Self::BackingTint => "Backing Tint",
            Self::TintIntensity => "Tint Intensity",
            Self::BackingBrightness => "Backing Brightness",
            Self::Polarizer => "Polarizer Tint",
            Self::Saturation => "Saturation",
            Self::PixelBrightness => "Pixel Brightness",
            Self::BackgroundTexture => "Backing Texture",
            Self::TransparentPixels => "Transparent Pixels",
            Self::Transparency => "Transparency",
            Self::WhiteThreshold => "White Threshold",
            Self::WhiteBoost => "White Boost",
            Self::BrightnessMode => "Brightness Mode",
            Self::GridOnColors => "Grid on Colors",
            Self::GridOnWhites => "Grid on Whites",
            Self::Shadows => "Shadows",
            Self::EdgeShadow => "Screen Edge Shadow",
            Self::OffsetX => "X Offset",
            Self::OffsetY => "Y Offset",
            Self::Opacity => "Opacity",
            Self::Blur => "Blur",
            Self::BlurQuality => "Blur Quality",
            Self::Motion => "Motion",
            Self::TiltSensitivity => "Tilt Sensitivity",
            Self::MaxOffset => "Max Offset",
            Self::NeutralX => "Neutral X",
            Self::NeutralY => "Neutral Y",
        }
    }

    fn kind(self) -> Kind {
        match self {
            Self::BackingTint => {
                Kind::Choice(&["OFF", "WARM 1", "WARM 2", "NEUTRAL", "ALUMINUM"])
            }
            Self::TintIntensity => hundredths(0, 200, 5),
            Self::BackingBrightness => hundredths(20, 65, 1),
            Self::Polarizer => optional(100, 5),
            Self::Saturation => hundredths(50, 150, 5),
            Self::PixelBrightness => hundredths(-100, 100, 5),
            Self::BackgroundTexture => Kind::Choice(&["FAST", "FULL"]),
            Self::TransparentPixels => Kind::Choice(&["WHITE ONLY", "BRIGHT", "ALL"]),
            Self::Transparency | Self::WhiteThreshold | Self::Opacity => hundredths(0, 100, 1),
            Self::WhiteBoost => optional(100, 5),
            Self::BrightnessMode => Kind::Choice(&["SIMPLE", "NATURAL"]),
            Self::GridOnColors | Self::GridOnWhites => hundredths(0, 100, 5),
            Self::Shadows | Self::EdgeShadow | Self::Motion => Kind::Choice(ON_OFF),
            Self::OffsetX | Self::OffsetY | Self::NeutralX | Self::NeutralY => {
                tenths(-300, 300, 5)
            }
            Self::Blur => tenths(0, 50, 1),
            Self::BlurQuality => Kind::Choice(&["FAST", "SMOOTH"]),
            Self::TiltSensitivity => hundredths(10, 500, 5),
            Self::MaxOffset => tenths(0, 300, 5),
        }
    }

    pub fn get(self, p: &PtParams) -> i16 {
        match self {
            Self::BackingTint => i16::from(p.backing_tint),
            Self::TintIntensity => p.tint_intensity,
            Self::BackingBrightness => p.backing_brightness,
            Self::Polarizer => p.polarizer,
            Self::Saturation => p.saturation,
            Self::PixelBrightness => p.pixel_brightness,
            Self::BackgroundTexture => i16::from(p.full_texture),
            Self::TransparentPixels => i16::from(p.transparent_pixels),
            Self::Transparency => p.transparency,
            Self::WhiteThreshold => p.white_threshold,
            Self::WhiteBoost => p.white_boost,
            Self::BrightnessMode => i16::from(p.natural_brightness),
            Self::GridOnColors => p.grid_on_colors,
            Self::GridOnWhites => p.grid_on_whites,
            Self::Shadows => i16::from(p.shadows),
            Self::EdgeShadow => i16::from(p.edge_shadow),
            Self::OffsetX => p.shadow_x,
            Self::OffsetY => p.shadow_y,
            Self::Opacity => p.shadow_opacity,
            Self::Blur => p.shadow_blur,
            Self::BlurQuality => i16::from(p.smooth_blur),
            Self::Motion => i16::from(p.motion),
            Self::TiltSensitivity => p.tilt_sensitivity,
            Self::MaxOffset => p.max_offset,
            Self::NeutralX => p.neutral_x,
            Self::NeutralY => p.neutral_y,
        }
    }

    fn set(self, p: &mut PtParams, v: i16) {
        let small = v.clamp(0, 255) as u8;
        match self {
            Self::BackingTint => p.backing_tint = small,
            Self::TintIntensity => p.tint_intensity = v,
            Self::BackingBrightness => p.backing_brightness = v,
            Self::Polarizer => p.polarizer = v,
            Self::Saturation => p.saturation = v,
            Self::PixelBrightness => p.pixel_brightness = v,
            Self::BackgroundTexture => p.full_texture = v != 0,
            Self::TransparentPixels => p.transparent_pixels = small,
            Self::Transparency => p.transparency = v,
            Self::WhiteThreshold => p.white_threshold = v,
            Self::WhiteBoost => p.white_boost = v,
            Self::BrightnessMode => p.natural_brightness = v != 0,
            Self::GridOnColors => p.grid_on_colors = v,
            Self::GridOnWhites => p.grid_on_whites = v,
            Self::Shadows => p.shadows = v != 0,
            Self::EdgeShadow => p.edge_shadow = v != 0,
            Self::OffsetX => p.shadow_x = v,
            Self::OffsetY => p.shadow_y = v,
            Self::Opacity => p.shadow_opacity = v,
            Self::Blur => p.shadow_blur = v,
            Self::BlurQuality => p.smooth_blur = v != 0,
            Self::Motion => p.motion = v != 0,
            Self::TiltSensitivity => p.tilt_sensitivity = v,
            Self::MaxOffset => p.max_offset = v,
            Self::NeutralX => p.neutral_x = v,
            Self::NeutralY => p.neutral_y = v,
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
    pub fn step(self, p: &mut PtParams, right: bool) -> bool {
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
    pub fn text(self, p: &PtParams) -> String {
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

impl PtParams {
    /// Values from an edited or older Settings file, put back in range.
    pub fn sanitize(mut self) -> Self {
        for field in PtField::ALL {
            let v = field.allowed(field.get(&self));
            field.set(&mut self, v);
        }
        self
    }

    /// Whether this set needs the accelerometer.
    pub fn wants_tilt(&self) -> bool {
        self.motion && self.shadows
    }

    /// The shadow offset of the shader's vertex stage, in reference pixels.
    /// `gravity` is the accelerometer in g, turned the way the handheld is
    /// held (RetroArch's sensor uniform); None or ~0 keeps the fixed offset.
    pub fn shadow_offset(&self, gravity: Option<[f32; 3]>) -> [f32; 2] {
        let fixed = [f32::from(self.shadow_x) / 10.0, f32::from(self.shadow_y) / 10.0];
        let Some([x, y, z]) = gravity.filter(|_| self.motion) else { return fixed };
        let length = (x * x + y * y + z * z).sqrt();
        if length < 0.01 {
            return fixed;
        }
        let sensitivity = f32::from(self.tilt_sensitivity) / 100.0;
        let tilt = [-x / length, y / length].map(|t| (t * sensitivity).clamp(-1.0, 1.0));
        let max = f32::from(self.max_offset) / 10.0;
        [
            f32::from(self.neutral_x) / 10.0 + tilt[0] * max,
            f32::from(self.neutral_y) / 10.0 + tilt[1] * max,
        ]
    }

    /// The parameters in the shader's units.
    pub fn shader(&self, gravity: Option<[f32; 3]>) -> PixelTransparency {
        let h = |v: i16| f32::from(v) / 100.0;
        PixelTransparency {
            transparent_pixels: f32::from(self.transparent_pixels),
            amount: h(self.transparency),
            white_threshold: h(self.white_threshold),
            white_boost: h(self.white_boost),
            natural_brightness: self.natural_brightness,
            grid_on_colors: h(self.grid_on_colors),
            grid_on_whites: h(self.grid_on_whites),
            backing_tint: f32::from(self.backing_tint),
            tint_intensity: h(self.tint_intensity),
            backing_brightness: h(self.backing_brightness),
            polarizer: h(self.polarizer),
            saturation: h(self.saturation),
            pixel_brightness: h(self.pixel_brightness),
            full_texture: self.full_texture,
            smooth_blur: self.smooth_blur,
            shadows: self.shadows,
            edge_shadow: self.edge_shadow,
            shadow_opacity: h(self.shadow_opacity),
            shadow_blur: f32::from(self.shadow_blur) / 10.0,
            shadow_offset: self.shadow_offset(gravity),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn defaults_match_the_shader_preset() {
        assert_eq!(PtParams::default().shader(None), PixelTransparency::default());
    }

    #[test]
    fn every_field_appears_on_exactly_one_page_of_at_most_six_rows() {
        for field in PtField::ALL {
            let pages = PtPage::ALL.iter().filter(|p| p.fields().contains(&field)).count();
            assert_eq!(pages, 1, "{field:?}");
        }
        assert!(PtPage::ALL.iter().all(|p| p.fields().len() <= 6));
        // The page list plus Reset to Defaults is itself one page.
        assert!(PtPage::ALL.len() < 6);
        assert!(PtPage::ALL.iter().enumerate().all(|(i, p)| p.index() == i));
    }

    #[test]
    fn numbers_stop_at_their_ends_and_choices_wrap() {
        let mut p = PtParams { transparency: 99, ..Default::default() };
        assert!(PtField::Transparency.step(&mut p, true));
        assert_eq!(p.transparency, 100);
        assert!(!PtField::Transparency.step(&mut p, true), "stays at 1.00");
        assert_eq!(PtField::Transparency.text(&p), "1.00");
        p.backing_tint = 4;
        assert!(PtField::BackingTint.step(&mut p, true));
        assert_eq!(PtField::BackingTint.text(&p), "OFF");
        assert!(PtField::BackingTint.step(&mut p, false));
        assert_eq!(PtField::BackingTint.text(&p), "ALUMINUM");
        assert!(PtField::Shadows.step(&mut p, false));
        assert_eq!(PtField::Shadows.text(&p), "OFF");
    }

    #[test]
    fn values_read_like_the_shader_parameters() {
        let mut p = PtParams::default();
        assert_eq!(PtField::OffsetX.text(&p), "3.0");
        assert_eq!(PtField::BackingBrightness.text(&p), "0.48");
        assert_eq!(PtField::WhiteBoost.text(&p), "OFF");
        PtField::WhiteBoost.step(&mut p, true);
        assert_eq!(PtField::WhiteBoost.text(&p), "0.05");
        for _ in 0..30 {
            PtField::OffsetX.step(&mut p, false);
        }
        assert_eq!(PtField::OffsetX.text(&p), "-12.0");
        PtField::PixelBrightness.step(&mut p, false);
        assert_eq!(PtField::PixelBrightness.text(&p), "-0.05");
    }

    #[test]
    fn edited_settings_files_come_back_in_range_and_on_a_step() {
        let p = PtParams {
            transparency: 400,
            backing_tint: 9,
            shadow_x: 33,
            tilt_sensitivity: 0,
            ..Default::default()
        }
        .sanitize();
        assert_eq!(p.transparency, 100);
        assert_eq!(p.backing_tint, 4);
        assert_eq!(p.shadow_x, 30, "offsets move by 0.5");
        assert_eq!(p.tilt_sensitivity, 10);
        assert_eq!(PtParams::default().sanitize(), PtParams::default());
    }

    #[test]
    fn missing_fields_keep_their_defaults() {
        let p: PtParams = serde_json::from_str(r#"{"transparency": 35}"#).unwrap();
        assert_eq!(p, PtParams { transparency: 35, ..Default::default() });
    }

    #[test]
    fn tilt_moves_the_shadow_like_the_vertex_shader() {
        let p = PtParams::default();
        // Flat on a table: gravity straight through the screen, neutral offset.
        assert_eq!(p.shadow_offset(Some([0.0, 0.0, 1.0])), [2.0, 2.0]);
        // Tipped toward the player: shadows drop by the full 2.5.
        let [x, y] = p.shadow_offset(Some([0.0, 1.0, 0.0]));
        assert!((x - 2.0).abs() < 1e-6 && (y - 4.5).abs() < 1e-6);
        // Rolled right: the shadow moves left.
        let [x, _] = p.shadow_offset(Some([0.7, 0.0, 0.7]));
        assert!(x < 2.0);
        // No reading, or Motion OFF: the fixed X/Y offset.
        assert_eq!(p.shadow_offset(None), [3.0, 3.0]);
        assert_eq!(p.shadow_offset(Some([0.0; 3])), [3.0, 3.0]);
        let still = PtParams { motion: false, ..Default::default() };
        assert_eq!(still.shadow_offset(Some([0.0, 1.0, 0.0])), [3.0, 3.0]);
        assert!(!still.wants_tilt());
        assert!(!PtParams { shadows: false, ..Default::default() }.wants_tilt());
    }
}

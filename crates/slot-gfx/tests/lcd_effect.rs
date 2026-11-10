#![cfg(all(target_os = "linux", feature = "test-support"))]
//! The LCD Effect's parameters (Matt Akins' Pixel Transparency and Sunlight
//! Shimmer) on a real (Mesa) GL context: the 240x160 frame fills a 960x640
//! KONKR framebuffer, 4 pixels per game pixel. The defaults are the
//! constants the shader had before it took parameters: a render with them
//! was checked byte for byte against that shader (Sunlight Shimmer within
//! one level on a couple of bytes, float rounding).
use slot_gfx::{Compositor, Draw, LcdEffect, LcdParams, ScreenEffect, TestEgl, SRC_H, SRC_W};

const W: usize = 960;

/// White, with a dark block (it casts a shadow) and a coloured band, in
/// the libretro XRGB8888 (B, G, R, X) byte order.
fn frame() -> Vec<u8> {
    let mut out = Vec::with_capacity((SRC_W * SRC_H * 4) as usize);
    for y in 0..SRC_H {
        for x in 0..SRC_W {
            let [r, g, b] = if (100..140).contains(&x) && (60..100).contains(&y) { [10, 20, 30] }
                else if y < 30 { [200, 60, 30] } else { [250, 250, 245] };
            out.extend_from_slice(&[b, g, r, 255]);
        }
    }
    out
}

fn render(gpu: &mut Compositor, lcd: LcdEffect, params: LcdParams, game: &[u8]) -> Vec<u8> {
    gpu.fit((960, 640));
    gpu.set_screen_power(1.0);
    gpu.set_screen_effect(ScreenEffect::None);
    gpu.set_lcd_effect(lcd);
    gpu.set_lcd_params(params);
    gpu.set_light(0.3, 0.4);
    gpu.set_picture([0.0, 0.0, 1.0, 1.0]);
    gpu.set_game_source_rect([0.0, 0.0, 1.0, 1.0]);
    gpu.upload_game(game);
    gpu.begin_frame();
    gpu.draw_list(&[Draw::Game]);
    gpu.read_frame()
}

/// The framebuffer pixel at the centre of game pixel (x, y), as 0..1.
fn at(frame: &[u8], x: u32, y: u32) -> [f32; 3] {
    let i = ((y as usize * 4 + 2) * W + x as usize * 4 + 2) * 4;
    [0, 1, 2].map(|c| frame[i + c] as f32 / 255.0)
}

fn luma(c: [f32; 3]) -> f32 {
    0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
}

#[test]
fn lcd_effect_parameters() {
    // One context for every case: Mesa EGL fixtures are created serially.
    let surface = TestEgl::new();
    let mut gpu = Compositor::new(&surface).unwrap();
    let game = frame();
    let base = LcdParams::default();
    let pt = |gpu: &mut Compositor, params| render(gpu, LcdEffect::PixelTransparency, params, &game);

    let plain = pt(&mut gpu, base);
    // The block's shadow falls down and to the right (offset 3, 3).
    // (At full transparency, so the backing it falls on shows plainly.)
    let full = LcdParams { amount: 0.4, ..base };
    let shaded = pt(&mut gpu, full);
    let bare = pt(&mut gpu, LcdParams { shadows: false, ..full });
    let (shade, lit) = (luma(at(&shaded, 140, 80)), luma(at(&bare, 140, 80)));
    assert!(shade < lit - 0.05, "shadow {shade} vs none {lit}");
    let unshaded = pt(&mut gpu, LcdParams { shadows: false, ..base });
    // Far from the block nothing changes.
    assert!((luma(at(&unshaded, 60, 120)) - luma(at(&plain, 60, 120))).abs() < 0.004);
    let open = luma(at(&plain, 60, 120));

    // More transparency: white shows more of the darker backing.
    let more = pt(&mut gpu, LcdParams { amount: 0.4, ..base });
    assert!(luma(at(&more, 60, 120)) < open - 0.05);

    // Warm 1 backing is green over blue; Neutral is not.
    let warm = pt(&mut gpu, LcdParams { backing_tint: 1.0, polarizer: 0.0, amount: 0.4, ..base });
    let w = at(&warm, 60, 120);
    assert!(w[1] - w[2] > 0.05, "Warm 1: {w:?}");

    // White pixels only: the coloured band keeps its own colour.
    let whites = pt(&mut gpu, LcdParams { transparent_pixels: 0.0, polarizer: 0.0,
        pixel_brightness: 0.0, ..base });
    let band = at(&whites, 60, 10);
    assert!((band[0] - 200.0 / 255.0).abs() < 0.01 && (band[2] - 30.0 / 255.0).abs() < 0.01, "{band:?}");
    assert!(luma(at(&whites, 60, 120)) < 0.95, "white still shows the backing");

    // Saturation 0.5 greys the band.
    let grey = pt(&mut gpu, LcdParams { saturation: 0.5, ..base });
    let (g, c) = (at(&grey, 60, 10), at(&plain, 60, 10));
    assert!(g[0] - g[2] < c[0] - c[2] - 0.1, "{g:?} vs {c:?}");

    // Sunlight Shimmer: no glare and no rainbow is Pixel Transparency.
    let sun = render(&mut gpu, LcdEffect::SunlightShimmer, base, &game);
    let flat = render(&mut gpu, LcdEffect::SunlightShimmer,
        LcdParams { glare: 0.0, rainbow: 0.0, ..base }, &game);
    let near = |a: [f32; 3], b: [f32; 3]| a.iter().zip(b).all(|(x, y)| (x - y).abs() < 1.5 / 255.0);
    assert!(near(at(&flat, 60, 120), at(&plain, 60, 120)));
    // The glare lights the picture up; the rainbow keeps its brightness.
    let mean = |f: &[u8]| (0..SRC_H).step_by(8).flat_map(|y| (0..SRC_W).step_by(8).map(move |x| (x, y)))
        .map(|(x, y)| luma(at(f, x, y))).sum::<f32>();
    assert!(mean(&sun) > mean(&flat) * 1.01, "glare {} vs none {}", mean(&sun), mean(&flat));
    let no_glare = render(&mut gpu, LcdEffect::SunlightShimmer, LcdParams { glare: 0.0, ..base }, &game);
    assert!((mean(&no_glare) - mean(&flat)).abs() < mean(&flat) * 0.005);
}

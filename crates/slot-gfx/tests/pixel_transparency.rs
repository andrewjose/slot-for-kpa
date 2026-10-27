#![cfg(all(target_os = "linux", feature = "test-support"))]
//! Pixel Transparency on a real (Mesa) GL context: the 240x160 GBA frame
//! fills a 960x640 KONKR framebuffer, 4 pixels per game pixel.
use slot_gfx::{Compositor, Draw, PixelTransparency, ScreenEffect, TestEgl, SRC_H, SRC_W};

const W: usize = 960;

/// A GBA frame in the libretro XRGB8888 (B, G, R, X) byte order.
fn frame(pixel: impl Fn(u32, u32) -> [u8; 3]) -> Vec<u8> {
    let mut out = Vec::with_capacity((SRC_W * SRC_H * 4) as usize);
    for y in 0..SRC_H {
        for x in 0..SRC_W {
            let [r, g, b] = pixel(x, y);
            out.extend_from_slice(&[b, g, r, 255]);
        }
    }
    out
}

fn render(
    gpu: &mut Compositor,
    effect: ScreenEffect,
    pt: Option<PixelTransparency>,
    game: &[u8],
) -> Vec<u8> {
    gpu.fit((960, 640));
    gpu.set_screen_power(1.0);
    gpu.set_screen_effect(effect);
    gpu.set_picture([0.0, 0.0, 1.0, 1.0]);
    gpu.set_game_source_rect([0.0, 0.0, 1.0, 1.0]);
    gpu.set_pixel_transparency(pt);
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

fn no_shadows() -> PixelTransparency {
    PixelTransparency {
        shadows: false,
        ..Default::default()
    }
}

#[test]
fn pixel_transparency() {
    // One context for every case: Mesa EGL fixtures are created serially.
    let surface = TestEgl::new();
    let mut gpu = Compositor::new(&surface).unwrap();
    let white = frame(|_, _| [255; 3]);
    let black = frame(|_, _| [0; 3]);
    let colour = frame(|_, _| [200, 60, 30]);

    // Off: the game pass is untouched.
    let plain = render(&mut gpu, ScreenEffect::None, None, &colour);
    let px = at(&plain, 120, 80);
    assert!((px[0] - 200.0 / 255.0).abs() < 0.01 && (px[2] - 30.0 / 255.0).abs() < 0.01);

    // White pixels let the backing show through.
    let lit = render(&mut gpu, ScreenEffect::None, Some(no_shadows()), &white);
    let centre = at(&lit, 120, 80);
    assert!(luma(centre) < 0.9, "white must not stay white: {centre:?}");
    // Warm 1 backing: green and red above blue.
    assert!(centre[1] > centre[2] && centre[0] > centre[2], "{centre:?}");

    // Bright mode: black pixels are opaque (only the dither moves them).
    let dark = render(&mut gpu, ScreenEffect::None, Some(no_shadows()), &black);
    assert!(luma(at(&dark, 120, 80)) < 3.0 / 255.0);

    // A shadow falls down and to the right of a dark block (offset 3, 3).
    let block = frame(|x, y| {
        if (100..110).contains(&x) && (60..70).contains(&y) {
            [0; 3]
        } else {
            [255; 3]
        }
    });
    let shaded = render(
        &mut gpu,
        ScreenEffect::None,
        Some(PixelTransparency::default()),
        &block,
    );
    let flat = render(&mut gpu, ScreenEffect::None, Some(no_shadows()), &block);
    let below_right = luma(at(&shaded, 110, 70));
    assert!(
        below_right < luma(at(&flat, 110, 70)) - 0.02,
        "shadow expected"
    );
    // Away from the block the two agree.
    assert!((luma(at(&shaded, 30, 120)) - luma(at(&flat, 30, 120))).abs() < 0.01);
    // Up and to the left of the block, no shadow…
    assert!((luma(at(&shaded, 99, 59)) - luma(at(&flat, 99, 59))).abs() < 0.01);
    // …until tilting moves the offset the other way.
    let tilted = render(
        &mut gpu,
        ScreenEffect::None,
        Some(PixelTransparency {
            shadow_offset: [-3.0, -3.0],
            ..Default::default()
        }),
        &block,
    );
    assert!(luma(at(&tilted, 99, 59)) < luma(at(&flat, 99, 59)) - 0.02);

    // The screen's top-left edge casts a shadow, unless turned off.
    let edge = render(
        &mut gpu,
        ScreenEffect::None,
        Some(PixelTransparency::default()),
        &white,
    );
    let no_edge = render(
        &mut gpu,
        ScreenEffect::None,
        Some(PixelTransparency {
            edge_shadow: false,
            ..Default::default()
        }),
        &white,
    );
    assert!(luma(at(&edge, 0, 0)) < luma(at(&no_edge, 0, 0)) - 0.02);
    assert!((luma(at(&edge, 120, 80)) - luma(at(&no_edge, 120, 80))).abs() < 0.01);

    // It follows any screen shader: every mode compiles and the backing
    // shows through its white. (AGB-001's unlit subpixels are darker than
    // the backing, so "changed", not "darker".)
    for mode in [
        ScreenEffect::Lcd3x,
        ScreenEffect::Grid,
        ScreenEffect::Dot,
        ScreenEffect::Simpletex,
        ScreenEffect::LcdGridV2,
        ScreenEffect::LcdGridV2Gba,
        ScreenEffect::ZfastLcd,
        ScreenEffect::Lcd1x,
        ScreenEffect::SimpletexGba,
        ScreenEffect::Agb001,
        ScreenEffect::Ags001,
        ScreenEffect::SharpBilinear,
    ] {
        let with = render(&mut gpu, mode, Some(no_shadows()), &white);
        let without = render(&mut gpu, mode, None, &white);
        let changed: u32 = with
            .iter()
            .zip(&without)
            .map(|(a, b)| u32::from(a.abs_diff(*b)))
            .sum();
        assert!(changed > (W * 640) as u32 * 8, "{mode:?}: {changed}");
    }
    assert_eq!(unsafe { gl::GetError() }, gl::NO_ERROR);
    drop(gpu);
}

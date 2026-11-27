#![cfg(all(target_os = "linux", feature = "test-support"))]
//! Curvature on a real (Mesa) GL context: the CRT shaders that bend pull
//! the picture's corners in to black and leave its centre lit; the flat
//! default and the shaders that don't bend keep every corner.
use slot_gfx::{Compositor, Draw, LcdEffect, ScreenEffect, TestEgl, SRC_H, SRC_W};

const W: usize = 960;

fn render(gpu: &mut Compositor, effect: ScreenEffect, curve: f32) -> Vec<u8> {
    let white = vec![255u8; (SRC_W * SRC_H * 4) as usize];
    gpu.fit((960, 640));
    gpu.set_screen_power(1.0);
    gpu.set_screen_effect(effect);
    gpu.set_lcd_effect(LcdEffect::Off);
    gpu.set_curvature(curve);
    gpu.set_picture([0.0, 0.0, 1.0, 1.0]);
    gpu.set_game_source_rect([0.0, 0.0, 1.0, 1.0]);
    gpu.upload_game(&white);
    gpu.begin_frame();
    gpu.draw_list(&[Draw::Game]);
    gpu.read_frame()
}

/// The brightest of a 16x16 block of framebuffer pixels at (x, y): the CRT
/// masks and scanlines darken single pixels.
fn light(frame: &[u8], x: usize, y: usize) -> u8 {
    (y..y + 16).flat_map(|y| (x..x + 16).map(move |x| (y * W + x) * 4))
        .map(|i| frame[i].max(frame[i + 1]).max(frame[i + 2])).max().unwrap()
}

#[test]
fn bending_shaders_pull_the_corners_in() {
    let surface = TestEgl::new();
    let mut gpu = Compositor::new(&surface).unwrap();
    for effect in [ScreenEffect::CrtPi, ScreenEffect::CrtGdvMiniUltra,
                   ScreenEffect::CrtGeomDeluxe, ScreenEffect::Fakelottes] {
        let flat = render(&mut gpu, effect, 0.0);
        assert!(light(&flat, 0, 0) > 100, "{effect:?}: flat, the corner is lit");
        let bent = render(&mut gpu, effect, 1.0);
        assert_eq!(light(&bent, 0, 0), 0, "{effect:?}: bent, the corner is black");
        assert!(light(&bent, 472, 312) > 100, "{effect:?}: the centre stays lit");
    }
    let other = render(&mut gpu, ScreenEffect::ZfastCrt, 1.0);
    assert!(light(&other, 0, 0) > 100, "zfast-crt doesn't bend");
}

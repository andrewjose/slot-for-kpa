//! A PlayStation disc's product code (SLUS-00594, SCES-02105...), read from
//! the BOOT line of SYSTEM.CNF in the disc's root directory, so a disc's
//! label can be found whatever its file is called. A translation or a hack
//! usually keeps the original game's code, so it gets the original's cover.
//!
//! CHD (MAME's compressed disc image) through the `chd` crate, and PBP
//! (PSP eboot) from its PARAM.SFO. Bounded: a few sectors are read, never
//! the whole image.
// Only the Android JNI entry calls in; host builds run the tests alone.
#![cfg_attr(not(target_os = "android"), allow(dead_code))]

use std::fs::File;
use std::io::{Read, Seek, SeekFrom};

/// Raw CD frame in a CHD: 2352 bytes of sector and 96 of subcode.
const FRAME: usize = 2448;
/// Mode 2 Form 1 user data: after 12 sync, 4 header and 8 subheader bytes.
const DATA: usize = 24;
const USER: usize = 2048;

/// Reads 2048-byte user data of CD sector `lba`.
trait Sectors {
    fn sector(&mut self, lba: u32) -> Option<Vec<u8>>;
}

struct Chd {
    chd: chd::Chd<File>,
    hunk_frames: u32,
    cached: Option<(u32, Vec<u8>)>,
    cmp: Vec<u8>,
}

impl Chd {
    fn open(file: File) -> Option<Self> {
        let chd = chd::Chd::open(file, None).ok()?;
        let hunk = chd.header().hunk_size() as usize;
        if hunk == 0 || !hunk.is_multiple_of(FRAME) {
            return None;
        }
        Some(Self { hunk_frames: (hunk / FRAME) as u32, chd, cached: None, cmp: Vec::new() })
    }
}

impl Sectors for Chd {
    fn sector(&mut self, lba: u32) -> Option<Vec<u8>> {
        let index = lba / self.hunk_frames;
        if self.cached.as_ref().map(|(i, _)| *i) != Some(index) {
            let mut out = self.chd.get_hunksized_buffer();
            let mut hunk = self.chd.hunk(index).ok()?;
            hunk.read_hunk_in(&mut self.cmp, &mut out).ok()?;
            self.cached = Some((index, out));
        }
        let (_, hunk) = self.cached.as_ref()?;
        let at = (lba % self.hunk_frames) as usize * FRAME;
        let frame = hunk.get(at..at + 2352)?;
        // Mode 2 (PlayStation) data at 24; a Mode 1 sector's at 16.
        let start = if frame[15] == 1 { 16 } else { DATA };
        Some(frame[start..start + USER].to_vec())
    }
}

fn le32(b: &[u8], at: usize) -> Option<u32> {
    Some(u32::from_le_bytes(b.get(at..at + 4)?.try_into().ok()?))
}

/// The bytes of a file in the ISO 9660 root directory, at most `limit`.
fn root_file(disc: &mut impl Sectors, name: &str, limit: usize) -> Option<Vec<u8>> {
    let pvd = disc.sector(16)?;
    if pvd.get(1..6)? != b"CD001" || pvd[0] != 1 {
        return None;
    }
    let root = pvd.get(156..156 + 34)?;
    let (mut lba, size) = (le32(root, 2)?, le32(root, 10)? as usize);
    let mut left = size.min(64 * USER);
    while left > 0 {
        let dir = disc.sector(lba)?;
        let mut at = 0;
        while at + 33 < USER {
            let len = dir[at] as usize;
            if len == 0 {
                break;
            }
            let id_len = dir[at + 32] as usize;
            let id = dir.get(at + 33..at + 33 + id_len)?;
            let id = String::from_utf8_lossy(id).to_ascii_uppercase();
            let id = id.split(';').next().unwrap_or("");
            if id == name {
                let (start, bytes) = (le32(&dir, at + 2)?, le32(&dir, at + 10)? as usize);
                let bytes = bytes.min(limit);
                let mut out = Vec::with_capacity(bytes);
                let mut s = start;
                while out.len() < bytes {
                    let data = disc.sector(s)?;
                    let take = (bytes - out.len()).min(USER);
                    out.extend_from_slice(&data[..take]);
                    s += 1;
                }
                return Some(out);
            }
            at += len;
        }
        left = left.saturating_sub(USER);
        lba += 1;
    }
    None
}

/// "BOOT = cdrom:\SLUS_005.94;1" -> "SLUS-00594".
pub fn serial_from_system_cnf(text: &str) -> Option<String> {
    let line = text.lines().find(|l| l.trim_start().to_ascii_uppercase().starts_with("BOOT"))?;
    let path = line.split_once('=')?.1.trim();
    let file = path.rsplit(['\\', '/', ':']).next()?.split(';').next()?.trim();
    serial_from_name(file)
}

/// SLUS_005.94, SCES_021.05, SLPS_012.34 -> SLUS-00594 ...
fn serial_from_name(file: &str) -> Option<String> {
    let flat: String = file.chars().filter(|c| c.is_ascii_alphanumeric()).collect();
    let flat = flat.to_ascii_uppercase();
    let letters: String = flat.chars().take_while(|c| c.is_ascii_alphabetic()).collect();
    let digits: String = flat[letters.len()..].chars().take_while(|c| c.is_ascii_digit()).collect();
    if letters.len() != 4 || digits.len() != 5 {
        return None;
    }
    Some(format!("{letters}-{digits}"))
}

/// A CHD's PlayStation product code.
pub fn chd_serial(file: File) -> Option<String> {
    let mut disc = Chd::open(file)?;
    let cnf = root_file(&mut disc, "SYSTEM.CNF", 4096)?;
    serial_from_system_cnf(&String::from_utf8_lossy(&cnf))
}

/// A PBP's product code: DISC_ID in its PARAM.SFO ("SLUS00594").
pub fn pbp_serial(mut file: File) -> Option<String> {
    let mut head = [0u8; 40];
    file.read_exact(&mut head).ok()?;
    if &head[0..4] != b"\0PBP" {
        return None;
    }
    let (sfo_at, next) = (le32(&head, 8)? as u64, le32(&head, 12)? as u64);
    let len = next.checked_sub(sfo_at)?.min(64 * 1024) as usize;
    let mut sfo = vec![0u8; len];
    file.seek(SeekFrom::Start(sfo_at)).ok()?;
    file.read_exact(&mut sfo).ok()?;
    sfo_value(&sfo, "DISC_ID").and_then(|v| serial_from_name(&v))
}

fn sfo_value(sfo: &[u8], want: &str) -> Option<String> {
    if sfo.get(0..4)? != b"\0PSF" {
        return None;
    }
    let keys = le32(sfo, 8)? as usize;
    let data = le32(sfo, 12)? as usize;
    let count = le32(sfo, 16)? as usize;
    for i in 0..count.min(256) {
        let e = 20 + i * 16;
        let key_at = keys + u16::from_le_bytes(sfo.get(e..e + 2)?.try_into().ok()?) as usize;
        let key_end = key_at + sfo.get(key_at..)?.iter().position(|b| *b == 0)?;
        if &sfo[key_at..key_end] != want.as_bytes() {
            continue;
        }
        let len = le32(sfo, e + 4)? as usize;
        let at = data + le32(sfo, e + 12)? as usize;
        let raw = sfo.get(at..at + len)?;
        let text: String = raw.iter().take_while(|b| **b != 0).map(|b| *b as char).collect();
        return Some(text);
    }
    None
}

/// The disc's product code, by the image's extension; None when unknown.
pub fn serial(file: File, ext: &str) -> Option<String> {
    match ext.to_ascii_lowercase().as_str() {
        "chd" => chd_serial(file),
        "pbp" => pbp_serial(file),
        _ => None,
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn boot_lines() {
        assert_eq!(serial_from_system_cnf("BOOT = cdrom:\\SLUS_005.94;1\r\nTCB = 4\r\n").as_deref(),
            Some("SLUS-00594"));
        assert_eq!(serial_from_system_cnf("BOOT=cdrom:\\SCES_021.05;1").as_deref(), Some("SCES-02105"));
        assert_eq!(serial_from_system_cnf("BOOT = cdrom:PSX.EXE;1"), None);
        assert_eq!(serial_from_name("SLUS00594").as_deref(), Some("SLUS-00594"));
    }

    /// SLOT_TEST_CHD=<file.chd> cargo test -p slot-android disc_serial -- --ignored
    #[test]
    #[ignore]
    fn a_real_chd() {
        let Ok(path) = std::env::var("SLOT_TEST_CHD") else { return };
        for p in path.split('|') {
            println!("{p}: {:?}", chd_serial(File::open(p).unwrap()));
        }
    }
}

/// DiscId.serialFromFd(fd, ext): the product code of the disc open at `fd`
/// (the caller keeps and closes it; a duplicate is read), "" when unknown.
#[cfg(target_os = "android")]
#[unsafe(no_mangle)]
pub extern "system" fn Java_fyi_slot_konkr_DiscId_serialFromFd(
    mut env: jni::JNIEnv<'_>,
    _this: jni::objects::JObject<'_>,
    fd: jni::sys::jint,
    ext: jni::objects::JString<'_>,
) -> jni::sys::jstring {
    use std::os::fd::FromRawFd;
    let ext: String = env.get_string(&ext).map(|s| s.to_string_lossy().into_owned()).unwrap_or_default();
    // SAFETY: dup() gives this function its own descriptor, closed with the File.
    let own = unsafe { libc::dup(fd) };
    let found = if own < 0 { None } else {
        let file = unsafe { File::from_raw_fd(own) };
        std::panic::catch_unwind(move || serial(file, &ext)).ok().flatten()
    };
    env.new_string(found.unwrap_or_default()).map_or(std::ptr::null_mut(), |s| s.into_raw())
}

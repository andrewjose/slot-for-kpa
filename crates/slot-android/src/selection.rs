//! Durable last selected shelf and ROM, without touching game/save data.
use serde::{Serialize,Deserialize};
use std::path::Path;
const FILE: &str="konkr-shelf-focus.json";
#[derive(Clone,Debug,PartialEq,Eq,Serialize,Deserialize)]
pub struct Focus { pub rack: usize, pub uri: String }
/// The file keeps the rack numbers from before the disc shelves (six
/// systems, then Recents 6 and Favorites 7); the systems added since (the
/// PlayStation, the TurboGrafx-CD) are 8 on, in Platform::ALL order.
const FIRST_SYSTEMS: usize = 6;
fn saved_rack(rack: usize) -> usize {
    let systems = slot_store::Platform::ALL.len();
    match rack {
        n if n < FIRST_SYSTEMS => n,
        n if n < systems => n + 2,
        n => n - systems + FIRST_SYSTEMS,
    }
}
fn live_rack(saved: usize) -> Option<usize> {
    let systems = slot_store::Platform::ALL.len();
    match saved {
        n if n < FIRST_SYSTEMS => Some(n),
        n if n < FIRST_SYSTEMS + 2 => Some(n - FIRST_SYSTEMS + systems),
        n if n < systems + 2 => Some(n - 2),
        _ => None,
    }
}
pub fn load(root: &Path) -> Option<Focus> {
    std::fs::read(root.join(FILE)).ok()
        .and_then(|bytes|serde_json::from_slice::<Focus>(&bytes).ok())
        .and_then(|focus| Some(Focus { rack: live_rack(focus.rack)?, uri: focus.uri }))
        .filter(|focus|focus.rack<slot_store::Platform::ALL.len()+2 && !focus.uri.is_empty())
}
pub fn store(root: &Path, focus: &Focus) -> std::io::Result<()> {
    std::fs::create_dir_all(root)?;
    let temp=root.join(format!("{FILE}.partial"));
    let saved=Focus { rack: saved_rack(focus.rack), uri: focus.uri.clone() };
    let data=serde_json::to_vec(&saved).map_err(std::io::Error::other)?;
    std::fs::write(&temp,data)?;
    let final_path=root.join(FILE);
    match std::fs::rename(&temp,&final_path) {
        Ok(())=>Ok(()),
        Err(e)=>{let _=std::fs::remove_file(&temp);Err(e)}
    }
}
#[cfg(test)]
mod tests {
    use super::*;
    #[test] fn persist_restore_and_ignore_missing_state() {
        let dir=std::env::temp_dir().join(format!("slot-focus-u2-{}",std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        assert_eq!(load(&dir),None);
        let focus=Focus { rack: 7,uri:"content://roms/emerald".into() };
        store(&dir,&focus).unwrap();
        assert_eq!(load(&dir),Some(focus));
        // Files from before the PlayStation shelf: 7 was Favorites.
        std::fs::write(dir.join(FILE), br#"{"rack":7,"uri":"content://roms/emerald"}"#).unwrap();
        assert_eq!(load(&dir).map(|f| f.rack), Some(9));
        for rack in 0..10 {
            let focus=Focus { rack, uri:"content://roms/any".into() };
            store(&dir,&focus).unwrap();
            assert_eq!(load(&dir),Some(focus));
        }
        std::fs::write(dir.join(FILE), br#"{"rack":10,"uri":"content://roms/bad"}"#).unwrap();
        assert_eq!(load(&dir), None, "out-of-range rack must not restore");
        std::fs::remove_dir_all(dir).unwrap();
    }
}
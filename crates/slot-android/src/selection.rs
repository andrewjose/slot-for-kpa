//! Durable last selected shelf and ROM, without touching game/save data.
use serde::{Serialize,Deserialize};
use std::path::Path;
const FILE: &str="konkr-shelf-focus.json";
#[derive(Clone,Debug,PartialEq,Eq,Serialize,Deserialize)]
pub struct Focus { pub rack: usize, pub uri: String }
/// The file keeps the rack numbers from before the PlayStation shelf (six
/// systems, then Recents 6 and Favorites 7); the PlayStation's is 8.
const SAVED_RECENTS: usize = 6;
const SAVED_PSX: usize = 8;
fn saved_rack(rack: usize) -> usize {
    let psx = slot_store::Platform::Psx as usize;
    if rack == psx { SAVED_PSX } else if rack > psx { rack - 1 } else { rack }
}
fn live_rack(saved: usize) -> Option<usize> {
    let psx = slot_store::Platform::Psx as usize;
    match saved {
        SAVED_PSX => Some(psx),
        n if n >= SAVED_RECENTS && n < SAVED_PSX => Some(n + 1),
        n if n < SAVED_RECENTS => Some(n),
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
        assert_eq!(load(&dir).map(|f| f.rack), Some(8));
        for rack in 0..9 {
            let focus=Focus { rack, uri:"content://roms/any".into() };
            store(&dir,&focus).unwrap();
            assert_eq!(load(&dir),Some(focus));
        }
        std::fs::write(dir.join(FILE), br#"{"rack":9,"uri":"content://roms/bad"}"#).unwrap();
        assert_eq!(load(&dir), None, "out-of-range rack must not restore");
        std::fs::remove_dir_all(dir).unwrap();
    }
}
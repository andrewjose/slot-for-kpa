package fyi.slot.konkr

/**
 * slot. started upright (Interface > Orientation: Portrait, for handhelds
 * like the RG Rotate). The same activity as MainActivity; only its manifest
 * entry differs: screenOrientation="portrait", so Android draws even the
 * starting window upright instead of turning from landscape. Enabled as the
 * launcher entry only while Portrait is chosen (SlotOrientation.useLauncher).
 */
class PortraitActivity : MainActivity()

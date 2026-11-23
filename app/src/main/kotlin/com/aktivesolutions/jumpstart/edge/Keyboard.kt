package com.aktivesolutions.jumpstart.edge

import android.content.res.Configuration

/** A physical keyboard is attached and in use (shared by the home search pill and the edge bar). */
fun hardKeyboardAttached(cfg: Configuration): Boolean =
    cfg.keyboard == Configuration.KEYBOARD_QWERTY && cfg.hardKeyboardHidden == Configuration.HARDKEYBOARDHIDDEN_NO

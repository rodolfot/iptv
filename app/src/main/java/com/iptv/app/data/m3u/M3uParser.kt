package com.iptv.app.data.m3u

// The parser itself moved to the :core module. We keep type aliases here for
// the duration of the modularization rollout so existing call sites compile
// without churn while the package paths converge. Delete once everyone imports
// `com.iptv.core.m3u.*` directly.
typealias M3uTrack = com.iptv.core.m3u.M3uTrack
typealias M3uParser = com.iptv.core.m3u.M3uParser

#pragma once

namespace orcinus::orca {

// Routes OrcaSlicer's Boost.Log records to logcat under the "OrcaSlicer" tag.
// Safe to call more than once.
void install_android_log_sink();

}  // namespace orcinus::orca

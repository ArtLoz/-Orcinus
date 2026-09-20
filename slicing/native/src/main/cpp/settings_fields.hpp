#pragma once

// The fields of the desktop app's settings tabs (Field.cpp, OptionsGroup.cpp,
// GUI.cpp): the value a setting has for its field, the text the field shows for
// it, and the value it gives for the text the user entered.

#include <string>
#include <vector>

#include <boost/any.hpp>

#include "libslic3r/Point.hpp"
#include "libslic3r/PrintConfig.hpp"
#include "orca_engine_adapter.hpp"
#include "settings_dialogs.hpp"

namespace orcinus::orca::detail {

// double_to_string() of Field.cpp, with a decimal point.
std::string double_to_string(double value, int max_precision = 4);

// get_thumbnail_string() and get_thumbnails_string() of Field.cpp.
std::string get_thumbnail_string(const Slic3r::Vec2d& value);
std::string get_thumbnails_string(const std::vector<Slic3r::Vec2d>& values);

// ConfigOptionsGroup::get_config_value(): what the field of the setting holds,
// for a vector setting the value at opt_index.
boost::any config_value(const Slic3r::DynamicPrintConfig& config, const std::string& opt_key, int opt_index = -1);

// The text the app shows for what config_value() gives: "0.2", "15%", "1" or
// "0" for a check box, the enum key ("grid") for a combo box.
std::string field_text(const Slic3r::ConfigOptionDef& opt, const boost::any& value);
std::string field_text(const Slic3r::DynamicPrintConfig& config, const std::string& opt_key, int opt_index = -1);

// get_string_value() of UnsavedChangesDialog.cpp: the value of a setting as the
// dialog of unsaved changes shows it, for a vector setting the value at the
// index of opt_id ("retraction_length#0").
std::vector<UiText> changed_value_text(const std::string& opt_id, const Slic3r::DynamicPrintConfig& config);

// The options the combo boxes of the filament list (DynamicFilamentList) edit.
bool is_filament_list_option(const std::string& key);

// What the field of the setting passes to OptionsGroup::on_change_OG() when its
// text changed: Field::get_value() with its corrections and message boxes. The
// value is empty when the field does not propagate the text: the text is empty,
// is no number, or, when changed_only, gives the value the setting has.
boost::any field_value(const Slic3r::ConfigOptionDef& opt, const std::string& opt_id, const Slic3r::DynamicPrintConfig& config, int opt_index,
                       const std::string& text, SettingsDialogs& dialogs, bool changed_only = true);

// change_opt_value() of GUI.cpp: the value of a field goes into the setting.
void change_opt_value(Slic3r::DynamicPrintConfig& config, const std::string& opt_key, const boost::any& value, int opt_index = 0);

}  // namespace orcinus::orca::detail

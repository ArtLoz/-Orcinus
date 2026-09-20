#include "settings_fields.hpp"

#include <algorithm>
#include <cerrno>
#include <cfloat>
#include <climits>
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <regex>
#include <sstream>

#include <boost/algorithm/string.hpp>
#include <boost/log/trivial.hpp>

#include <boost/format.hpp>

#include "libslic3r/GCode/Thumbnails.hpp"
#include "libslic3r/LocalesUtils.hpp"

namespace orcinus::orca::detail {

namespace {

using Slic3r::ConfigOptionDef;
using GUIType = Slic3r::ConfigOptionDef::GUIType;

// The controls OptionsGroup::build_field() creates.
enum class FieldControl { text, check_box, spin, choice, colour, point, other };

FieldControl field_control(const ConfigOptionDef& opt)
{
    switch (opt.gui_type) {
    case GUIType::select_open:
    case GUIType::f_enum_open:
    case GUIType::i_enum_open:
        return FieldControl::choice;
    case GUIType::one_string:
        return FieldControl::text;
    case GUIType::color:
        return FieldControl::colour;
    case GUIType::slider:
    case GUIType::legend:
        return FieldControl::other;
    default:
        break;
    }
    switch (opt.type) {
    case Slic3r::coFloatOrPercent:
    case Slic3r::coFloatsOrPercents:
    case Slic3r::coFloat:
    case Slic3r::coFloats:
    case Slic3r::coPercent:
    case Slic3r::coPercents:
    case Slic3r::coString:
    case Slic3r::coStrings:
        return FieldControl::text;
    case Slic3r::coBool:
    case Slic3r::coBools:
        return FieldControl::check_box;
    case Slic3r::coInt:
    case Slic3r::coInts:
        return FieldControl::spin;
    case Slic3r::coEnum:
    case Slic3r::coEnums:
        return FieldControl::choice;
    case Slic3r::coPoint:
    case Slic3r::coPoints:
        return FieldControl::point;
    default:
        return FieldControl::other;
    }
}

// validate_thumbnails_string() of Field.cpp: the list in the form the desktop
// app writes it back ("300x300/PNG, 96x96/PNG"), and what was wrong with it.
Slic3r::ThumbnailErrors validate_thumbnails_string(std::string& str, const std::string& def_ext = "PNG")
{
    std::string input_string = str;

    str.clear();

    auto [thumbnails_list, errors] = Slic3r::GCodeThumbnails::make_and_check_thumbnail_list(input_string, def_ext);
    if (!thumbnails_list.empty()) {
        const auto& extentions = Slic3r::ConfigOptionEnum<Slic3r::GCodeThumbnailsFormat>::get_enum_names();
        for (const auto& [format, size] : thumbnails_list)
            str += (boost::format("%1%x%2%/%3%, ") % size.x() % size.y() % extentions[int(format)]).str();
        str.resize(str.size() - 2);
    }

    return errors;
}

// wxString::ToLong(): value is set unless nothing could be read, and the
// result tells whether the whole text is the number.
bool to_long(const std::string& text, long& value)
{
    const char* start = text.c_str();
    char* end = nullptr;
    errno = 0;
    const long parsed = std::strtol(start, &end, 10);
    if (end == start || errno == ERANGE) {
        return false;
    }
    value = parsed;
    return *end == '\0';
}

// wxString::ToDouble()
bool to_double(const std::string& text, double& value)
{
    const char* start = text.c_str();
    char* end = nullptr;
    errno = 0;
    const double parsed = std::strtod(start, &end);
    if (end == start || errno == ERANGE) {
        return false;
    }
    value = parsed;
    return *end == '\0';
}

// static_cast<int>() of a limit, which may be -FLT_MAX or FLT_MAX.
int int_limit(const float limit)
{
    return limit <= float(INT_MIN) ? INT_MIN : limit >= float(INT_MAX) ? INT_MAX : static_cast<int>(limit);
}

// wxString::Replace(dec_sep_alt, dec_sep, false): the first decimal comma
// becomes a point.
bool replace_decimal_comma(std::string& text)
{
    const size_t comma = text.find(',');
    if (comma == std::string::npos) {
        return false;
    }
    text[comma] = '.';
    return true;
}

std::vector<UiText> parameter_validation_title(const std::string& opt_id)
{
    return {ui_text("Parameter validation"), ui_text(": " + opt_id)};
}

// The text of one coordinate as PointCtrl shows it.
std::string point_coordinate(const double value)
{
    if (value - int(value) == 0) {
        return std::to_string(int(value));
    }
    return double_to_string(value, 2);
}

// Field::get_value_by_opt_type(). str is the field's text, last_value the value
// the field held (m_value). With dialogs the text is checked as check_value
// asks: message boxes are shown and str takes the text the field corrects
// itself to. Without, an invalid text gives an empty value.
boost::any value_by_opt_type(const ConfigOptionDef& opt, const std::string& opt_id, std::string& str, const boost::any& last_value,
                             SettingsDialogs* dialogs)
{
    const bool check_value = dialogs != nullptr;
    // The field shows the value of the preset it overrides while the override
    // is off (Field::m_na_value).
    const bool is_na_value = opt.nullable && str == "N/A";
    switch (opt.type) {
    case Slic3r::coInts:
    case Slic3r::coInt: {
        long val = 0;
        if (is_na_value) {
            val = Slic3r::ConfigOptionIntsNullable::nil_value();
        } else if (!to_long(str, val)) {
            if (!check_value) {
                return {};
            }
            dialogs->error("invalid_numeric", {ui_text("Invalid numeric.")});
            str = std::to_string(int(val));
        }
        if (!opt.is_value_valid(double(val))) {
            if (!check_value) {
                return {};
            }
            if (!is_na_value) {
                // Orca: no need to check ranges for the nil value
                dialogs->error("out_of_range", {ui_text("Value is out of range.")});
                const int min = int_limit(opt.min);
                const int max = int_limit(opt.max);
                if (min > val) {
                    val = min;
                }
                if (val > max) {
                    val = max;
                }
                str = std::to_string(int(val));
            }
        }
        return int(val);
    }
    case Slic3r::coPercent:
    case Slic3r::coPercents:
    case Slic3r::coFloats:
    case Slic3r::coFloat: {
        if (opt.type == Slic3r::coPercent && !str.empty() && str.back() == '%') {
            str.pop_back();
        } else if (!str.empty() && str.back() == '%') {
            if (!check_value) {
                return {};
            }
            const std::string& label = opt.full_label.empty() ? opt.label : opt.full_label;
            dialogs->error("percentage", {ui_text("%s can't be a percentage", {label}, true)});
            str = double_to_string(opt.min);
            return double(opt.min);
        }
        double val = 0.0;
        if (!is_na_value) {
            replace_decimal_comma(str);
        }
        if (str == ".") {
            val = 0.0;
        } else if (is_na_value) {
            val = Slic3r::ConfigOptionFloatsNullable::nil_value();
        } else {
            if (!to_double(str, val)) {
                if (!check_value) {
                    return {};
                }
                dialogs->error("invalid_numeric", {ui_text("Invalid numeric.")});
                str = double_to_string(val);
            }
            if (!opt.is_value_valid(val)) {
                if (!check_value) {
                    return {};
                }
                const std::string opt_key_without_idx = opt_id.substr(0, opt_id.find('#'));
                if (opt_id == "filament_flow_ratio") {
                    if (last_value.empty() || boost::any_cast<double>(last_value) != val) {
                        if (!dialogs->ask("out_of_range_continue", {ui_text("Value %s is out of range, continue?", {str})},
                                          parameter_validation_title(opt_id))) {
                            if (last_value.empty()) {
                                if (opt.min > val) {
                                    val = opt.min;
                                }
                                if (val > opt.max) {
                                    val = opt.max;
                                }
                            } else {
                                val = boost::any_cast<double>(last_value);
                            }
                            str = double_to_string(val);
                        }
                    }
                } else if (opt_id == "filament_retraction_distances_when_cut" || opt_key_without_idx == "retraction_distances_when_cut") {
                    if (last_value.empty() || boost::any_cast<double>(last_value) != val) {
                        dialogs->inform("out_of_range_limits",
                                        {ui_text("Value %s is out of range. The valid range is from %d to %d.",
                                                 {str, double_to_string(opt.min), double_to_string(opt.max)})},
                                        parameter_validation_title(opt_id));
                        if (last_value.empty()) {
                            if (opt.min > val) {
                                val = opt.min;
                            }
                            if (val > opt.max) {
                                val = opt.max;
                            }
                        } else {
                            val = boost::any_cast<double>(last_value);
                        }
                        str = double_to_string(val);
                    }
                } else {
                    // Orca: no need to check ranges for the nil value
                    dialogs->error("out_of_range", {ui_text("Value is out of range.")});
                    if (opt.min > val) {
                        val = opt.min;
                    }
                    if (val > opt.max) {
                        val = opt.max;
                    }
                    str = double_to_string(val);
                }
            }
        }
        return val;
    }
    case Slic3r::coString:
    case Slic3r::coStrings:
    case Slic3r::coFloatOrPercent:
    case Slic3r::coFloatsOrPercents: {
        if ((opt.type == Slic3r::coFloatOrPercent || opt.type == Slic3r::coFloatsOrPercents) && !str.empty() && str.back() != '%') {
            double val = 0.0;
            replace_decimal_comma(str);
            // remove space and "mm" substring, if any exists
            boost::replace_all(str, " ", "");
            boost::replace_all(str, "m", "");

            if (!to_double(str, val)) {
                if (!check_value) {
                    return {};
                }
                dialogs->error("invalid_numeric", {ui_text("Invalid numeric.")});
                str = double_to_string(val);
            } else if (((opt.sidetext.rfind("mm/s") != std::string::npos && val > opt.max)
                        || (opt.sidetext.rfind("mm ") != std::string::npos && val > opt.max_literal))
                       && (last_value.empty() || str != boost::any_cast<std::string>(last_value))) {
                if (!check_value) {
                    return {};
                }
                const std::string sidetext = opt.sidetext.rfind("mm/s") != std::string::npos ? "mm/s" : "mm";
                const std::string st_val = double_to_string(val, 2);
                if (val > 100
                    && dialogs->ask(
                        "percent_or_value",
                        {ui_text("Is it %s%% or %s %s?\nYES for %s%%, \nNO for %s %s.", {st_val, st_val, sidetext, st_val, st_val, sidetext})},
                        parameter_validation_title(opt_id))) {
                    str = st_val + "%";
                } else {
                    // it's no needed but can be helpful, when inputted value contained "," instead of "."
                    str = st_val;
                }
            }
        }
        if (opt.opt_key == "thumbnails") {
            std::string str_out = str;
            const Slic3r::ThumbnailErrors errors = validate_thumbnails_string(str_out);
            if (errors != Slic3r::enum_bitmask<Slic3r::ThumbnailError>()) {
                // set_value(str_out, true)
                str = str_out;
                std::vector<UiText> error_str;
                if (errors.has(Slic3r::ThumbnailError::InvalidVal))
                    error_str.push_back(ui_text("Invalid input format. Expected vector of dimensions in the following format: \"%1%\"",
                                                {"XxY/EXT, XxY/EXT, ..."}));
                if (errors.has(Slic3r::ThumbnailError::OutOfRange)) {
                    if (!error_str.empty())
                        error_str.push_back(ui_text("\n\n"));
                    error_str.push_back(ui_text("Input value is out of range"));
                }
                if (errors.has(Slic3r::ThumbnailError::InvalidExt)) {
                    if (!error_str.empty())
                        error_str.push_back(ui_text("\n\n"));
                    error_str.push_back(ui_text("Some extension in the input is invalid"));
                }
                // show_error(); without dialogs the text is only converted.
                if (dialogs != nullptr)
                    dialogs->error("thumbnails", error_str);
            } else if (str_out != str) {
                str = str_out;
            }
            return str;
        }
        if (opt.opt_key == "sparse_infill_rotate_template" || opt.opt_key == "solid_infill_rotate_template") {
            std::string ustr = str;
            if (!Slic3r::ConfigOptionFloats::validate_string(ustr)) {
                std::string v;
                std::smatch match;
                const std::string ps = opt.opt_key == "sparse_infill_rotate_template"
                    ? u8"[BT][!]?|[#][\\d]+[!]?|[+\\-]?[\\d.]+[%]?[*]?[\\d]*[/NnZz$LlUuQq~^|#]?[+\\-]?[\\d.]*[%#\'\"cm]?[m]?[BT]?[!*]?"
                    : u8"[#][\\d]+[!]?|[+\\-]?[\\d.]+[%]?[*]?[\\d]*[/NnZz$LlUuQq~^|#]?[+\\-]?[\\d.]*[%#\'\"cm]?[m]?[!*]?";
                try {
                    while (std::regex_search(ustr, match, std::regex(ps))) {
                        for (const auto& x : match) {
                            v += x.str() + ", ";
                        }
                        ustr = match.suffix().str();
                    }
                    v = v.substr(0, v.length() - 2);
                    str = v;
                } catch (...) {
                    if (!check_value) {
                        return {};
                    }
                    dialogs->error("rotate_template", {ui_text("This parameter expects a valid template.")});
                    // Revert to previous value
                    str = last_value.empty() ? std::string() : boost::any_cast<std::string>(last_value);
                }
            }
            return str;
        }
        if (opt.opt_key == "extra_solid_infills") {
            // New rule: accept either interval form (N or N#K) or explicit list (e.g. 1,7,9), with optional quotes.
            const std::regex rx_interval(u8R"(^\s*['"]?\s*\d+\s*(?:#\s*\d*)?\s*['"]?\s*$)");
            // List entries may be plain numbers or number with optional #K count, e.g., 5, 9#2, 18
            const std::regex rx_list(u8R"(^\s*['"]?\s*\d+(?:\s*#\s*\d*)?(?:\s*,\s*\d+(?:\s*#\s*\d*)?)*\s*['"]?\s*$)");
            const bool is_valid = str.empty() || std::regex_match(str, rx_interval) || std::regex_match(str, rx_list);
            if (!is_valid && check_value) {
                dialogs->error(
                    "extra_solid_infills",
                    {ui_text("Invalid pattern. Use N, N#K, or a comma-separated list with optional #K per entry. Examples: 5, 5#2, 1,7,9, 5,9#2,18.")});
                // Revert to previous value
                str = last_value.empty() ? std::string() : boost::any_cast<std::string>(last_value);
            }
            return str;
        }
        return str;
    }
    case Slic3r::coPoint: {
        Slic3r::Vec2d out_value = Slic3r::Vec2d::Zero();
        boost::replace_all(str, " ", "");
        if (!str.empty()) {
            bool invalid_val = true;
            const size_t separator = str.find('x');
            if (separator != std::string::npos) {
                double x = 0.0;
                double y = 0.0;
                if (to_double(str.substr(0, separator), x) && to_double(str.substr(separator + 1), y)) {
                    out_value = Slic3r::Vec2d(x, y);
                    invalid_val = false;
                }
            }
            if (invalid_val) {
                if (!check_value) {
                    return {};
                }
                dialogs->error("invalid_points", {ui_text("Invalid format. Expected vector format: \"%1%\"", {"XxY, XxY, ..."})});
                return {};
            }
        }
        return out_value;
    }
    case Slic3r::coPoints: {
        std::vector<Slic3r::Vec2d> out_values;
        boost::replace_all(str, " ", "");
        if (!str.empty()) {
            bool invalid_val = false;
            std::stringstream tokens(str);
            std::string token;
            while (std::getline(tokens, token, ',')) {
                const size_t separator = token.find('x');
                double x = 0.0;
                double y = 0.0;
                if (separator != std::string::npos && to_double(token.substr(0, separator), x) && to_double(token.substr(separator + 1), y)) {
                    out_values.push_back(Slic3r::Vec2d(x, y));
                    continue;
                }
                invalid_val = true;
                break;
            }
            if (invalid_val) {
                if (!check_value) {
                    return {};
                }
                dialogs->error("invalid_points", {ui_text("Invalid format. Expected vector format: \"%1%\"", {"XxY, XxY, ..."})});
                return {};
            }
        }
        return out_values;
    }
    default:
        return {};
    }
}

// TextCtrl::value_was_changed()
bool text_value_changed(const Slic3r::ConfigOptionDef& opt, const boost::any& held, const boost::any& value)
{
    if (held.empty()) {
        return true;
    }
    switch (opt.type) {
    case Slic3r::coInt:
        return boost::any_cast<int>(value) != boost::any_cast<int>(held);
    case Slic3r::coPercent:
    case Slic3r::coPercents: {
        if (opt.nullable && std::isnan(boost::any_cast<double>(value)) && std::isnan(boost::any_cast<double>(held))) {
            return false;
        }
        return boost::any_cast<double>(value) != boost::any_cast<double>(held);
    }
    case Slic3r::coFloats:
    case Slic3r::coFloat: {
        if (opt.nullable && std::isnan(boost::any_cast<double>(value)) && std::isnan(boost::any_cast<double>(held))) {
            return false;
        }
        return !Slic3r::is_approx(boost::any_cast<double>(value), boost::any_cast<double>(held));
    }
    case Slic3r::coString:
    case Slic3r::coStrings:
    case Slic3r::coFloatOrPercent:
        return boost::any_cast<std::string>(value) != boost::any_cast<std::string>(held);
    default:
        return true;
    }
}

// Choice::propagate_value()
bool choice_value_changed(const Slic3r::ConfigOptionType type, const boost::any& held, const boost::any& value)
{
    switch (type) {
    case Slic3r::coFloatOrPercent:
        return (held.empty() ? std::string() : boost::any_cast<std::string>(held)) != boost::any_cast<std::string>(value);
    case Slic3r::coInt:
        return (held.empty() ? 0 : boost::any_cast<int>(held)) != boost::any_cast<int>(value);
    default:
        return std::fabs((held.empty() ? -99999 : boost::any_cast<double>(held)) - boost::any_cast<double>(value)) > 0.0001;
    }
}

// The switch of change_opt_value() in GUI.cpp.
void set_opt_value(Slic3r::DynamicPrintConfig& config, const std::string& opt_key, const boost::any& value, const int opt_index)
{
    const ConfigOptionDef* opt_def = config.def()->get(opt_key);
    if (opt_def->type == Slic3r::coBools && opt_def->nullable) {
        const auto v = boost::any_cast<unsigned char>(value);
        auto vec_new = std::make_unique<Slic3r::ConfigOptionBoolsNullable>(1, v);
        if (v == Slic3r::ConfigOptionBoolsNullable::nil_value()) {
            vec_new->set_at_to_nil(0);
        }
        config.option<Slic3r::ConfigOptionBoolsNullable>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        return;
    }

    switch (opt_def->type) {
    case Slic3r::coFloatOrPercent: {
        std::string str = boost::any_cast<std::string>(value);
        bool percent = false;
        if (str.back() == '%') {
            str.pop_back();
            percent = true;
        }
        const double val = std::stod(str);
        config.set_key_value(opt_key, new Slic3r::ConfigOptionFloatOrPercent(val, percent));
        break;
    }
    case Slic3r::coFloatsOrPercents: {
        std::string str = boost::any_cast<std::string>(value);
        bool percent = false;
        if (str.back() == '%') {
            str.pop_back();
            percent = true;
        }
        const double val = std::stod(str);
        auto vec_new = std::make_unique<Slic3r::ConfigOptionFloatOrPercent>(val, percent);
        config.option<Slic3r::ConfigOptionFloatsOrPercents>(opt_key)->set_at(vec_new.get(), opt_index, opt_index);
        break;
    }
    case Slic3r::coPercent:
        config.set_key_value(opt_key, new Slic3r::ConfigOptionPercent(boost::any_cast<double>(value)));
        break;
    case Slic3r::coFloat:
        config.opt_float(opt_key) = boost::any_cast<double>(value);
        break;
    case Slic3r::coPercents: {
        auto vec_new = std::make_unique<Slic3r::ConfigOptionPercent>(boost::any_cast<double>(value));
        config.option<Slic3r::ConfigOptionPercents>(opt_key)->set_at(vec_new.get(), opt_index, opt_index);
        break;
    }
    case Slic3r::coFloats: {
        auto vec_new = std::make_unique<Slic3r::ConfigOptionFloat>(boost::any_cast<double>(value));
        config.option<Slic3r::ConfigOptionFloats>(opt_key)->set_at(vec_new.get(), opt_index, opt_index);
        break;
    }
    case Slic3r::coString:
        config.set_key_value(opt_key, new Slic3r::ConfigOptionString(boost::any_cast<std::string>(value)));
        break;
    case Slic3r::coStrings: {
        if (opt_key == "compatible_prints" || opt_key == "compatible_printers") {
            config.option<Slic3r::ConfigOptionStrings>(opt_key)->values = boost::any_cast<std::vector<std::string>>(value);
        } else if (opt_def->gui_flags == "serialized") {
            std::string str = boost::any_cast<std::string>(value);
            std::vector<std::string> values{};
            if (!str.empty()) {
                if (str.back() == ';') {
                    str.pop_back();
                }
                // Split a string to multiple strings by a semi - colon.This is the old way of storing multi - string values.
                // Currently used for the post_process config value only.
                boost::split(values, str, boost::is_any_of(";"));
                if (values.size() == 1 && values[0].empty()) {
                    values.resize(0);
                }
            }
            config.option<Slic3r::ConfigOptionStrings>(opt_key)->values = values;
        } else {
            auto vec_new = std::make_unique<Slic3r::ConfigOptionString>(boost::any_cast<std::string>(value));
            config.option<Slic3r::ConfigOptionStrings>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        }
        break;
    }
    case Slic3r::coBool:
        config.set_key_value(opt_key, new Slic3r::ConfigOptionBool(boost::any_cast<bool>(value)));
        break;
    case Slic3r::coBools: {
        auto vec_new = std::make_unique<Slic3r::ConfigOptionBool>(boost::any_cast<unsigned char>(value) != 0);
        config.option<Slic3r::ConfigOptionBools>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        break;
    }
    case Slic3r::coInt:
        config.set_key_value(opt_key, new Slic3r::ConfigOptionInt(boost::any_cast<int>(value)));
        break;
    case Slic3r::coInts: {
        auto vec_new = std::make_unique<Slic3r::ConfigOptionInt>(boost::any_cast<int>(value));
        config.option<Slic3r::ConfigOptionInts>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        break;
    }
    case Slic3r::coEnum: {
        auto* opt = opt_def->default_value.get()->clone();
        opt->setInt(boost::any_cast<int>(value));
        config.set_key_value(opt_key, opt);
        break;
    }
    case Slic3r::coEnums: {
        auto vec_new = std::make_unique<Slic3r::ConfigOptionEnumsGeneric>(std::vector<int>{boost::any_cast<int>(value)});
        if (config.has(opt_key)) {
            config.option<Slic3r::ConfigOptionEnumsGeneric>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        }
        break;
    }
    case Slic3r::coPoint:
        config.set_key_value(opt_key, new Slic3r::ConfigOptionPoint(boost::any_cast<Slic3r::Vec2d>(value)));
        break;
    case Slic3r::coPoints: {
        if (opt_key == "printable_area" || opt_key == "bed_exclude_area" || opt_key == "thumbnails" || opt_key == "wrapping_exclude_area") {
            config.option<Slic3r::ConfigOptionPoints>(opt_key)->values = boost::any_cast<std::vector<Slic3r::Vec2d>>(value);
            break;
        }
        auto vec_new = std::make_unique<Slic3r::ConfigOptionPoint>(boost::any_cast<Slic3r::Vec2d>(value));
        config.option<Slic3r::ConfigOptionPoints>(opt_key)->set_at(vec_new.get(), opt_index, 0);
        break;
    }
    case Slic3r::coNone:
    default:
        break;
    }
}

// get_string_from_enum() of UnsavedChangesDialog.cpp
std::vector<UiText> string_from_enum(const std::string& opt_key, const Slic3r::DynamicPrintConfig& config, const bool is_infill, const int idx = -1)
{
    const ConfigOptionDef& def = config.def()->options.at(opt_key);
    const std::vector<std::string>& names = def.enum_labels;
    int val = 0;

    if (idx >= 0) {
        val = dynamic_cast<const Slic3r::ConfigOptionInts*>(config.option(opt_key))->get_at(std::size_t(idx));
    } else {
        val = config.option(opt_key)->getInt();
    }

    // Each infill doesn't use all list of infill declared in PrintConfig.hpp.
    // So we should "convert" val to the correct one
    if (is_infill) {
        for (const auto& key_val : *def.enum_keys_map) {
            if (int(key_val.second) == val) {
                const auto it = std::find(def.enum_values.begin(), def.enum_values.end(), key_val.first);
                if (it == def.enum_values.end()) {
                    return {};
                }
                return {ui_text(names[std::size_t(it - def.enum_values.begin())])};
            }
        }
        return {ui_text("Undef")};
    }
    return val >= 0 && std::size_t(val) < names.size() ? std::vector<UiText>{ui_text(names[std::size_t(val)])} : std::vector<UiText>{ui_text("Undef")};
}

// The patterns whose values are a subset of the ones their enum declares.
bool is_infill_pattern(const std::string& opt_key)
{
    return opt_key == "top_surface_pattern" || opt_key == "bottom_surface_pattern" || opt_key == "internal_solid_infill_pattern" ||
           opt_key == "sparse_infill_pattern" || opt_key == "ironing_pattern" || opt_key == "support_ironing_pattern" ||
           opt_key == "support_pattern" || opt_key == "support_interface_pattern";
}

// A value of the edited preset, which is no msgid: it is filled in as an argument.
UiText value_text(const std::string& value)
{
    return ui_text("%1%", {value});
}

}  // namespace

std::string double_to_string(const double value, const int max_precision)
{
    // wxNumberFormatter::ToString(value, max_precision, wxNumberFormatter::Style_None)
    const int length = std::snprintf(nullptr, 0, "%.*f", max_precision, value);
    std::string s(size_t(std::max(length, 0)), '\0');
    std::snprintf(s.data(), s.size() + 1, "%.*f", max_precision, value);

    // The following code comes from wxNumberFormatter::RemoveTrailingZeroes(wxString& s)
    // with the exception that here one sets the decimal separator explicitely to dot.
    // If number is in scientific format, trailing zeroes belong to the exponent and cannot be removed.
    if (s.find_first_of("eE") == std::string::npos) {
        const size_t pos_dec_sep = s.find('.');
        // No decimal point => removing trailing zeroes irrelevant for integer number.
        if (pos_dec_sep != std::string::npos) {
            // Find the last character to keep.
            size_t pos_last_non_zero = s.find_last_not_of('0');
            // If it's the decimal separator itself, don't keep it either.
            if (pos_last_non_zero == pos_dec_sep) {
                --pos_last_non_zero;
            }
            s.erase(pos_last_non_zero + 1);
            // Remove sign from orphaned zero.
            if (s == "-0") {
                s = "0";
            }
        }
    }
    return s;
}

std::string get_thumbnail_string(const Slic3r::Vec2d& value)
{
    return point_coordinate(value[0]) + "x" + point_coordinate(value[1]);
}

std::string get_thumbnails_string(const std::vector<Slic3r::Vec2d>& values)
{
    std::string text;
    for (size_t i = 0; i < values.size(); ++i) {
        const Slic3r::Vec2d& el = values[i];
        text += (i == 0 ? "" : ", ") + std::to_string(int(el[0])) + "x" + std::to_string(int(el[1]));
    }
    return text;
}

boost::any config_value(const Slic3r::DynamicPrintConfig& config, const std::string& opt_key, const int opt_index)
{
    const size_t idx = opt_index == -1 ? 0 : size_t(opt_index);
    const ConfigOptionDef* opt = config.def()->get(opt_key);
    if (opt == nullptr || config.option(opt_key) == nullptr) {
        return {};
    }

    if (opt->nullable) {
        switch (opt->type) {
        case Slic3r::coPercents:
        case Slic3r::coFloats: {
            if (opt_index < 0 ? config.option(opt_key)->is_nil()
                              : dynamic_cast<const Slic3r::ConfigOptionVectorBase*>(config.option(opt_key))->is_nil(opt_index)) {
                return std::string("N/A");
            }
            const double val = opt->type == Slic3r::coFloats ? config.option<Slic3r::ConfigOptionFloatsNullable>(opt_key)->get_at(idx)
                                                             : config.option<Slic3r::ConfigOptionPercentsNullable>(opt_key)->get_at(idx);
            return double_to_string(val);
        }
        case Slic3r::coFloatsOrPercents: {
            if (opt_index < 0 ? config.option(opt_key)->is_nil()
                              : dynamic_cast<const Slic3r::ConfigOptionVectorBase*>(config.option(opt_key))->is_nil(opt_index)) {
                return std::string("N/A");
            }
            const auto& value = config.option<Slic3r::ConfigOptionFloatsOrPercentsNullable>(opt_key)->get_at(idx);
            return double_to_string(value.value) + (value.percent ? "%" : "");
        }
        case Slic3r::coBools:
            return static_cast<unsigned char>(config.option<Slic3r::ConfigOptionBoolsNullable>(opt_key)->values[idx]);
        case Slic3r::coInts:
            return int(config.option<Slic3r::ConfigOptionIntsNullable>(opt_key)->get_at(idx));
        case Slic3r::coEnums:
            return int(config.option<Slic3r::ConfigOptionEnumsGenericNullable>(opt_key)->get_at(idx));
        default:
            return {};
        }
    }

    switch (opt->type) {
    case Slic3r::coFloatOrPercent: {
        const auto& value = *config.option<Slic3r::ConfigOptionFloatOrPercent>(opt_key);
        return double_to_string(value.value) + (value.percent ? "%" : "");
    }
    case Slic3r::coFloatsOrPercents: {
        const auto& value = config.option<Slic3r::ConfigOptionFloatsOrPercents>(opt_key)->get_at(idx);
        return double_to_string(value.value) + (value.percent ? "%" : "");
    }
    case Slic3r::coPercent:
        return double_to_string(config.option<Slic3r::ConfigOptionPercent>(opt_key)->value);
    case Slic3r::coPercents:
    case Slic3r::coFloats:
    case Slic3r::coFloat: {
        if (opt_key == "extruder_printable_height") {
            const auto& values = dynamic_cast<const Slic3r::ConfigOptionFloatsNullable*>(config.option(opt_key))->values;
            return values.empty() ? std::string() : double_to_string(values[idx]);
        }
        const double val = opt->type == Slic3r::coFloats  ? config.opt_float(opt_key, idx)
                           : opt->type == Slic3r::coFloat ? config.opt_float(opt_key)
                                                          : config.option<Slic3r::ConfigOptionPercents>(opt_key)->get_at(idx);
        return double_to_string(val);
    }
    case Slic3r::coString:
        return config.opt_string(opt_key);
    case Slic3r::coStrings: {
        if (opt_key == "compatible_printers" || opt_key == "compatible_prints") {
            return config.option<Slic3r::ConfigOptionStrings>(opt_key)->values;
        }
        const auto& values = config.option<Slic3r::ConfigOptionStrings>(opt_key)->values;
        if (values.empty()) {
            return std::string();
        }
        if (opt->gui_flags == "serialized") {
            std::string text;
            if (!values[0].empty()) {
                for (const std::string& el : values) {
                    text += el + ";";
                }
            }
            return text;
        }
        return config.opt_string(opt_key, static_cast<unsigned int>(idx));
    }
    case Slic3r::coBool:
        return config.opt_bool(opt_key);
    case Slic3r::coBools:
        return static_cast<unsigned char>(config.opt_bool(opt_key, idx));
    case Slic3r::coInt:
        return config.opt_int(opt_key);
    case Slic3r::coInts:
        return config.opt_int(opt_key, idx);
    case Slic3r::coEnum:
        return config.option(opt_key)->getInt();
    case Slic3r::coEnums:
        return config.opt_int(opt_key, idx);
    case Slic3r::coPoint:
        return config.option<Slic3r::ConfigOptionPoint>(opt_key)->value;
    case Slic3r::coPoints:
        if (opt_key == "printable_area" || opt_key == "bed_exclude_area" || opt_key == "wrapping_exclude_area") {
            return get_thumbnails_string(config.option<Slic3r::ConfigOptionPoints>(opt_key)->values);
        }
        return config.option<Slic3r::ConfigOptionPoints>(opt_key)->get_at(idx);
    case Slic3r::coPointsGroups:
        if (opt_key == "extruder_printable_area") {
            const auto& values = config.option<Slic3r::ConfigOptionPointsGroups>(opt_key)->values;
            return values.empty() ? std::string() : get_thumbnails_string(config.option<Slic3r::ConfigOptionPointsGroups>(opt_key)->get_at(idx));
        }
        return {};
    case Slic3r::coNone:
    default:
        return {};
    }
}

std::string field_text(const Slic3r::ConfigOptionDef& opt, const boost::any& value)
{
    if (value.empty()) {
        return {};
    }
    if (const std::string* text = boost::any_cast<std::string>(&value)) {
        return *text;
    }
    if (const bool* checked = boost::any_cast<bool>(&value)) {
        return *checked ? "1" : "0";
    }
    if (const unsigned char* checked = boost::any_cast<unsigned char>(&value)) {
        return *checked != 0 ? "1" : "0";
    }
    if (const int* number = boost::any_cast<int>(&value)) {
        if (opt.type == Slic3r::coEnum || opt.type == Slic3r::coEnums) {
            // The key of the value, as the definition names it. The combo box
            // of the desktop app picks an entry by its place in the list, which
            // the bed type of the plate is not at (its list has no default).
            if (opt.enum_keys_map != nullptr) {
                for (const auto& [key, enum_value] : *opt.enum_keys_map) {
                    if (enum_value == *number && std::find(opt.enum_values.begin(), opt.enum_values.end(), key) != opt.enum_values.end()) {
                        return key;
                    }
                }
            }
            return *number >= 0 && size_t(*number) < opt.enum_values.size() ? opt.enum_values[*number] : std::to_string(*number);
        }
        return std::to_string(*number);
    }
    if (const double* number = boost::any_cast<double>(&value)) {
        return double_to_string(*number);
    }
    if (const Slic3r::Vec2d* point = boost::any_cast<Slic3r::Vec2d>(&value)) {
        return get_thumbnail_string(*point);
    }
    if (const auto* names = boost::any_cast<std::vector<std::string>>(&value)) {
        return boost::join(*names, ";");
    }
    return {};
}

std::string field_text(const Slic3r::DynamicPrintConfig& config, const std::string& opt_key, const int opt_index)
{
    const ConfigOptionDef* opt = config.def()->get(opt_key);
    if (opt == nullptr) {
        return {};
    }
    return field_text(*opt, config_value(config, opt_key, opt_index));
}

bool is_filament_list_option(const std::string& key)
{
    // Choice::register_dynamic_list() in Sidebar::Sidebar()
    static const char* const keys[] = {
        "support_filament",         "support_interface_filament", "outer_wall_filament_id",  "inner_wall_filament_id",
        "sparse_infill_filament_id", "internal_solid_filament_id", "top_surface_filament_id", "bottom_surface_filament_id",
        "wipe_tower_filament",
    };
    for (const char* const list_key : keys) {
        if (key == list_key) {
            return true;
        }
    }
    return false;
}

boost::any field_value(const Slic3r::ConfigOptionDef& opt, const std::string& opt_id, const Slic3r::DynamicPrintConfig& config, const int opt_index,
                       const std::string& text, SettingsDialogs& dialogs, const bool changed_only)
{
    const boost::any held_value = config_value(config, opt.opt_key, opt_index);
    const std::string current_text = field_text(opt, held_value);
    std::string str = text;
    boost::any value;
    switch (field_control(opt)) {
    case FieldControl::check_box: {
        // CheckBox::get_value()
        const bool checked = str == "1" || str == "true";
        if (opt.type == Slic3r::coBool) {
            value = checked;
        } else {
            value = static_cast<unsigned char>(checked);
        }
        break;
    }
    case FieldControl::spin: {
        // SpinCtrl: the text is clamped to the range of the control, and a text
        // that is no number reloads the field.
        long parsed = 0;
        if (!to_long(str, parsed) || parsed < INT_MIN || parsed > INT_MAX) {
            return {};
        }
        const int min_val = opt.min == -FLT_MAX ? 0 : int(opt.min);
        const int max_val = opt.max < FLT_MAX ? int(opt.max) : INT_MAX;
        value = std::min(std::max(int(parsed), min_val), max_val);
        break;
    }
    case FieldControl::choice: {
        // Choice::get_value()
        if (opt.type == Slic3r::coEnum || opt.type == Slic3r::coEnums) {
            if (opt.enum_keys_map == nullptr) {
                return {};
            }
            const auto entry = opt.enum_keys_map->find(str);
            if (entry == opt.enum_keys_map->end()) {
                return {};
            }
            value = entry->second;
            break;
        }
        // Choice::propagate_value(): an empty text reloads the field.
        if (str.empty() && opt.type != Slic3r::coString && opt.type != Slic3r::coStrings) {
            return {};
        }
        const auto enum_value = std::find(opt.enum_values.begin(), opt.enum_values.end(), str);
        if (!is_filament_list_option(opt.opt_key) && enum_value != opt.enum_values.end() && opt.type != Slic3r::coStrings) {
            if (opt.type == Slic3r::coFloatOrPercent) {
                value = *enum_value;
            } else if (opt.type == Slic3r::coInt) {
                value = std::atoi(enum_value->c_str());
            } else {
                value = Slic3r::string_to_double_decimal_point(*enum_value);
            }
        } else {
            std::string last_text = current_text;
            const boost::any last_value = value_by_opt_type(opt, opt_id, last_text, {}, nullptr);
            value = value_by_opt_type(opt, opt_id, str, last_value, &dialogs);
            if (value.empty()) {
                return {};
            }
        }
        break;
    }
    case FieldControl::colour:
        // ColourPicker::get_value(): the colour the picker shows.
        if (str.empty()) {
            return {};
        }
        value = str;
        break;
    case FieldControl::point:
    case FieldControl::text: {
        // TextCtrl::propagate_value(): an empty text reloads the field, and
        // the field changes only when value_was_changed().
        if (str.empty() && opt.type != Slic3r::coString && opt.type != Slic3r::coStrings && opt.type != Slic3r::coPoints) {
            return {};
        }
        std::string last_text = current_text;
        const boost::any last_value = value_by_opt_type(opt, opt_id, last_text, {}, nullptr);
        value = value_by_opt_type(opt, opt_id, str, last_value, &dialogs);
        if (value.empty()) {
            return {};
        }
        break;
    }
    case FieldControl::other:
        return {};
    }

    if (!changed_only) {
        return value;
    }
    // The field propagates the value when it differs from the one the field
    // held, which it read from the setting.
    switch (field_control(opt)) {
    case FieldControl::check_box: {
        const bool checked = opt.type == Slic3r::coBool ? boost::any_cast<bool>(value) : boost::any_cast<unsigned char>(value) != 0;
        return checked == (current_text == "1") ? boost::any() : value;
    }
    case FieldControl::spin: {
        const int held = opt.type == Slic3r::coInts ? config.opt_int(opt.opt_key, size_t(opt_index == -1 ? 0 : opt_index)) : config.opt_int(opt.opt_key);
        return held == boost::any_cast<int>(value) ? boost::any() : value;
    }
    case FieldControl::choice: {
        if (opt.type == Slic3r::coEnum || opt.type == Slic3r::coEnums) {
            const int held = opt.type == Slic3r::coEnum ? config.option(opt.opt_key)->getInt() : config.opt_int(opt.opt_key, size_t(opt_index == -1 ? 0 : opt_index));
            return held == boost::any_cast<int>(value) ? boost::any() : value;
        }
        std::string held_text = current_text;
        const boost::any held = value_by_opt_type(opt, opt_id, held_text, {}, nullptr);
        return choice_value_changed(opt.type, held, value) ? value : boost::any();
    }
    case FieldControl::colour:
        return current_text == boost::any_cast<std::string>(value) ? boost::any() : value;
    case FieldControl::point: {
        // PointCtrl::value_was_changed()
        const std::string held_text = current_text;
        return held_text == field_text(opt, value) ? boost::any() : value;
    }
    case FieldControl::text: {
        std::string held_text = current_text;
        const boost::any held = value_by_opt_type(opt, opt_id, held_text, {}, nullptr);
        return text_value_changed(opt, held, value) ? value : boost::any();
    }
    default:
        return {};
    }
}

std::vector<UiText> changed_value_text(const std::string& opt_id, const Slic3r::DynamicPrintConfig& config)
{
    int orig_opt_idx = -1;
    std::string opt_key = opt_id;
    const std::size_t hash = opt_key.find('#');
    if (hash != std::string::npos && hash > 0) {
        orig_opt_idx = std::atoi(opt_key.substr(hash + 1).c_str());
        opt_key = opt_key.substr(0, hash);
    }
    const int opt_idx = orig_opt_idx >= 0 ? orig_opt_idx : 0;
    const Slic3r::ConfigOption* option = config.option(opt_key);
    if (option == nullptr) {
        return {ui_text("N/A")};
    }

    if ((option->is_scalar() && option->is_nil()) ||
        (option->is_vector() && dynamic_cast<const Slic3r::ConfigOptionVectorBase*>(option)->is_nil(std::size_t(opt_idx)))) {
        return {ui_text("N/A")};
    }

    const ConfigOptionDef* opt = config.def()->get(opt_key);
    if (opt == nullptr) {
        return {ui_text("N/A")};
    }
    const bool is_nullable = opt->nullable;

    switch (opt->type) {
    case Slic3r::coInt:
        return {value_text(std::to_string(config.opt_int(opt_key)))};
    case Slic3r::coInts: {
        if (is_nullable) {
            const auto* values = config.opt<Slic3r::ConfigOptionIntsNullable>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(std::to_string(values->get_at(std::size_t(opt_idx))))};
            }
        } else {
            const auto* values = config.opt<Slic3r::ConfigOptionInts>(opt_key);
            if (orig_opt_idx >= 0 && std::size_t(orig_opt_idx) < values->size()) {
                return {value_text(std::to_string(values->get_at(std::size_t(opt_idx))))};
            }
            std::string value_str;
            for (std::size_t i = 0; i < values->size(); ++i) {
                value_str += std::to_string(values->get_at(i));
                if (i != values->size() - 1) {
                    value_str += ",";
                }
            }
            return {value_text(value_str)};
        }
        return {ui_text("Undef")};
    }
    case Slic3r::coBool:
        return {value_text(config.opt_bool(opt_key) ? "true" : "false")};
    case Slic3r::coBools: {
        if (is_nullable) {
            const auto* values = config.opt<Slic3r::ConfigOptionBoolsNullable>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(values->get_at(std::size_t(opt_idx)) != 0 ? "true" : "false")};
            }
        } else {
            const auto* values = config.opt<Slic3r::ConfigOptionBools>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(values->get_at(std::size_t(opt_idx)) != 0 ? "true" : "false")};
            }
        }
        return {ui_text("Undef")};
    }
    case Slic3r::coPercent:
        return {value_text(std::to_string(int(config.optptr(opt_key)->getFloat())) + "%")};
    case Slic3r::coPercents: {
        if (is_nullable) {
            const auto* values = config.opt<Slic3r::ConfigOptionPercentsNullable>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(double_to_string(values->get_at(std::size_t(opt_idx))) + "%")};
            }
        } else {
            const auto* values = config.opt<Slic3r::ConfigOptionPercents>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(double_to_string(values->get_at(std::size_t(opt_idx))) + "%")};
            }
        }
        return {ui_text("Undef")};
    }
    case Slic3r::coFloat:
        return {value_text(double_to_string(config.opt_float(opt_key)))};
    case Slic3r::coFloats: {
        if (is_nullable) {
            const auto* values = config.opt<Slic3r::ConfigOptionFloatsNullable>(opt_key);
            if (std::size_t(opt_idx) < values->size()) {
                return {value_text(double_to_string(values->get_at(std::size_t(opt_idx))))};
            }
        } else {
            const auto* values = config.opt<Slic3r::ConfigOptionFloats>(opt_key);
            if (values != nullptr && std::size_t(opt_idx) < values->size()) {
                return {value_text(double_to_string(values->get_at(std::size_t(opt_idx))))};
            }
        }
        return {ui_text("Undef")};
    }
    case Slic3r::coString:
        return {value_text(config.opt_string(opt_key))};
    case Slic3r::coStrings: {
        const auto* strings = config.opt<Slic3r::ConfigOptionStrings>(opt_key);
        if (strings != nullptr) {
            if (opt_key == "compatible_printers" || opt_key == "compatible_prints") {
                if (strings->empty()) {
                    return {ui_text("All")};
                }
                std::string out;
                for (std::size_t id = 0; id < strings->size(); ++id) {
                    out += strings->get_at(id) + "\n";
                }
                out.erase(out.size() - 1);
                return {value_text(out)};
            }
            if (!strings->empty() && std::size_t(opt_idx) < strings->values.size()) {
                return {value_text(strings->get_at(std::size_t(opt_idx)))};
            }
        }
        break;
    }
    case Slic3r::coFloatOrPercent: {
        const auto* value = config.opt<Slic3r::ConfigOptionFloatOrPercent>(opt_key);
        return value == nullptr ? std::vector<UiText>{} : std::vector<UiText>{value_text(double_to_string(value->value) + (value->percent ? "%" : ""))};
    }
    case Slic3r::coEnum:
        return string_from_enum(opt_key, config, is_infill_pattern(opt_key));
    case Slic3r::coEnums:
        return string_from_enum(opt_key, config, is_infill_pattern(opt_key), opt_idx);
    case Slic3r::coPoint: {
        const Slic3r::Vec2d value = config.opt<Slic3r::ConfigOptionPoint>(opt_key)->value;
        return {value_text("[" + Slic3r::ConfigOptionPoint(value).serialize() + "]")};
    }
    case Slic3r::coPoints: {
        if (opt_key == "printable_area" || opt_key == "thumbnails" || opt_key == "bed_exclude_area" || opt_key == "head_wrap_detect_zone" ||
            opt_key == "wrapping_exclude_area") {
            return {value_text(get_thumbnails_string(config.option<Slic3r::ConfigOptionPoints>(opt_key)->values))};
        }
        const Slic3r::Vec2d value = config.opt<Slic3r::ConfigOptionPoints>(opt_key)->get_at(std::size_t(opt_idx));
        return {value_text("[" + Slic3r::ConfigOptionPoint(value).serialize() + "]")};
    }
    default:
        break;
    }
    return {};
}

void change_opt_value(Slic3r::DynamicPrintConfig& config, const std::string& opt_key, const boost::any& value, const int opt_index)
{
    try {
        set_opt_value(config, opt_key, value, opt_index);
    } catch (const std::exception& e) {
        BOOST_LOG_TRIVIAL(error) << "Internal error when changing value for " << opt_key << ": " << e.what();
    }
}

}  // namespace orcinus::orca::detail

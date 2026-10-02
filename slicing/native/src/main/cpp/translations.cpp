#include "orca_engine_adapter.hpp"

#include <memory>
#include <mutex>
#include <sstream>
#include <string>
#include <unordered_map>

#include "libslic3r/I18N.hpp"

namespace orcinus::orca {

namespace {

using Catalog = std::unordered_map<std::string, std::string>;

std::mutex catalog_mutex;
std::shared_ptr<const Catalog> catalog;

// The text of a quoted gettext string, with its escapes resolved.
std::string unquote(const std::string& line)
{
    const std::size_t first = line.find('"');
    const std::size_t last = line.rfind('"');
    if (first == std::string::npos || last <= first) {
        return {};
    }
    std::string text;
    text.reserve(last - first);
    for (std::size_t i = first + 1; i < last; ++i) {
        const char c = line[i];
        if (c != '\\' || i + 1 >= last) {
            text += c;
            continue;
        }
        const char escaped = line[++i];
        switch (escaped) {
        case 'n': text += '\n'; break;
        case 't': text += '\t'; break;
        case 'r': text += '\r'; break;
        default: text += escaped; break;
        }
    }
    return text;
}

// wxGetTranslation() of libslic3r's messages: the catalogue's translation, the
// message itself where there is none.
std::string translate(const char* message)
{
    std::shared_ptr<const Catalog> current;
    {
        const std::lock_guard<std::mutex> lock(catalog_mutex);
        current = catalog;
    }
    if (current != nullptr) {
        const auto found = current->find(message);
        if (found != current->end()) {
            return found->second;
        }
    }
    return message;
}

}  // namespace

void set_translations(const std::string& po)
{
    auto entries = std::make_shared<Catalog>();
    // One entry at a time: its context, msgid and the first msgstr, each of
    // which may go on over the next lines.
    enum class Field { none, context, id, plural, translation, other_translation };
    Field field = Field::none;
    std::string context;
    std::string id;
    std::string translation;
    bool has_context = false;
    const auto commit = [&]() {
        // libslic3r's L() has no context; the header has an empty msgid.
        if (!has_context && !id.empty() && !translation.empty()) {
            entries->emplace(id, translation);
        }
        context.clear();
        id.clear();
        translation.clear();
        has_context = false;
    };
    std::istringstream lines(po);
    std::string line;
    while (std::getline(lines, line)) {
        if (!line.empty() && line.back() == '\r') {
            line.pop_back();
        }
        if (line.empty() || line[0] == '#') {
            continue;
        }
        if (line.rfind("msgctxt", 0) == 0) {
            commit();
            has_context = true;
            context = unquote(line);
            field = Field::context;
        } else if (line.rfind("msgid_plural", 0) == 0) {
            field = Field::plural;
        } else if (line.rfind("msgid", 0) == 0) {
            if (field == Field::translation || field == Field::other_translation) {
                commit();
            }
            id = unquote(line);
            field = Field::id;
        } else if (line.rfind("msgstr[0]", 0) == 0 || line.rfind("msgstr ", 0) == 0) {
            translation = unquote(line);
            field = Field::translation;
        } else if (line.rfind("msgstr", 0) == 0) {
            field = Field::other_translation;
        } else if (line[0] == '"') {
            switch (field) {
            case Field::context: context += unquote(line); break;
            case Field::id: id += unquote(line); break;
            case Field::translation: translation += unquote(line); break;
            default: break;
            }
        }
    }
    commit();

    const std::lock_guard<std::mutex> lock(catalog_mutex);
    catalog = entries->empty() ? nullptr : std::shared_ptr<const Catalog>(entries);
    Slic3r::I18N::set_translate_callback(catalog != nullptr ? &translate : nullptr);
}

}  // namespace orcinus::orca

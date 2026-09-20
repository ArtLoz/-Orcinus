#include "settings_dialogs.hpp"

#include <algorithm>

namespace orcinus::orca::detail {

UiText ui_text(std::string msgid, std::vector<std::string> args, const bool translate_args)
{
    UiText text;
    text.msgid = std::move(msgid);
    text.args = std::move(args);
    text.translate_args = translate_args;
    return text;
}

SettingsDialogs::SettingsDialogs(const DialogAnswers& answers) : m_answers(answers) {}

void SettingsDialogs::inform(std::string id, std::vector<UiText> text, std::vector<UiText> title, const DialogIcon icon)
{
    SettingsDialog& dialog = m_notices.emplace_back();
    dialog.id = std::move(id);
    dialog.icon = icon;
    dialog.title = std::move(title);
    dialog.text = std::move(text);
}

void SettingsDialogs::error(std::string id, std::vector<UiText> text)
{
    inform(std::move(id), std::move(text), {}, DialogIcon::error);
}

bool SettingsDialogs::ask(std::string id, std::vector<UiText> text, std::vector<UiText> title, UiText yes, UiText no)
{
    const auto answer = std::find_if(m_answers.begin(), m_answers.end(), [&id](const auto& entry) { return entry.first == id; });
    if (answer != m_answers.end()) {
        return answer->second;
    }
    QuestionPending pending;
    pending.dialog.id = std::move(id);
    pending.dialog.icon = DialogIcon::warning;
    pending.dialog.title = std::move(title);
    pending.dialog.text = std::move(text);
    pending.dialog.question = true;
    pending.dialog.yes = std::move(yes);
    pending.dialog.no = std::move(no);
    throw pending;
}

std::vector<SettingsDialog> SettingsDialogs::take_notices()
{
    return std::exchange(m_notices, {});
}

}  // namespace orcinus::orca::detail

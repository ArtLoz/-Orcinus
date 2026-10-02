#pragma once

// The message boxes the desktop app's settings tabs show while they apply a
// change. The engine cannot wait for an answer: boxes that only inform are
// collected, and a question without an answer abandons the change by throwing
// QuestionPending; the app asks the user and requests the change again with
// the answer.

#include <string>
#include <utility>
#include <vector>

#include "orca_engine_adapter.hpp"

namespace orcinus::orca::detail {

UiText ui_text(std::string msgid, std::vector<std::string> args = {}, bool translate_args = false);

struct QuestionPending {
    SettingsDialog dialog;
};

class SettingsDialogs {
public:
    explicit SettingsDialogs(const DialogAnswers& answers);

    // MessageDialog(..., wxOK).ShowModal()
    void inform(std::string id, std::vector<UiText> text, std::vector<UiText> title = {}, DialogIcon icon = DialogIcon::warning);

    // show_error(): an ErrorDialog.
    void error(std::string id, std::vector<UiText> text);

    // MessageDialog(..., wxYES | wxNO).ShowModal() == wxID_YES. Throws
    // QuestionPending when the question has no answer yet.
    bool ask(std::string id, std::vector<UiText> text, std::vector<UiText> title = {}, UiText yes = {}, UiText no = {});

    // RichMessageDialog with ShowCheckBox(checkbox, checked): the answer, and
    // whether the box was left checked (the answer CHECKBOX_ANSWER after the id).
    std::pair<bool, bool> ask_checked(std::string id, std::vector<UiText> text, std::vector<UiText> title, UiText checkbox, bool checked,
                                      DialogIcon icon = DialogIcon::warning);

    // The boxes shown from now on are told apart from the same boxes of
    // another file of the load by scope, which follows their ids.
    void set_scope(std::string scope);

    std::vector<SettingsDialog> take_notices();

private:
    const bool* answer(const std::string& id) const;

    const DialogAnswers& m_answers;
    std::vector<SettingsDialog> m_notices;
    std::string m_scope;
};

}  // namespace orcinus::orca::detail

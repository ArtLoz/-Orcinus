#include "step_mesh.hpp"

#include <algorithm>
#include <memory>
#include <mutex>

#include "engine_context.hpp"
#include "libslic3r/AppConfig.hpp"
#include "libslic3r/Exception.hpp"
#include "libslic3r/Format/STEP.hpp"
#include "libslic3r/LocalesUtils.hpp"

namespace orcinus::orca {

namespace {

// The file StepMeshDialog asks about (its m_file), kept between the requests
// of the dialog, which the engine's lock does not guard: a count runs while
// another request stops it.
std::mutex step_mutex;
std::shared_ptr<Slic3r::Step> step_file;
std::string step_path;

// One count at a time, as the dialog runs one task.
std::mutex count_mutex;

std::shared_ptr<Slic3r::Step> kept_file()
{
    const std::lock_guard<std::mutex> lock(step_mutex);
    return step_file;
}

}  // namespace

std::int64_t step_triangle_count(const std::string& path, const double linear_deflection, const double angle_deflection)
{
    std::shared_ptr<Slic3r::Step> file;
    {
        const std::lock_guard<std::mutex> lock(step_mutex);
        if (step_path != path) {
            return 0;
        }
        file = step_file;
    }
    if (file == nullptr) {
        return 0;
    }
    const std::lock_guard<std::mutex> count_lock(count_mutex);
    // StepMeshDialog::stop_task() stopped the count before, and clears the flag for the next.
    file->m_stop_mesh.store(false);
    return static_cast<std::int64_t>(file->get_triangle_num(linear_deflection, angle_deflection));
}

void stop_step_triangle_count()
{
    if (const std::shared_ptr<Slic3r::Step> file = kept_file(); file != nullptr) {
        file->m_stop_mesh.store(true);
    }
}

void release_step_file()
{
    stop_step_triangle_count();
    const std::lock_guard<std::mutex> lock(step_mutex);
    step_file.reset();
    step_path.clear();
}

namespace detail {

StepMeshParameters step_mesh_parameters(const std::string& path, const StepMeshChoice& choice, const bool replacing)
{
    Slic3r::AppConfig& config = *engine().config;
    double linear = Slic3r::string_to_double_decimal_point(config.get("linear_defletion"));
    double angle = Slic3r::string_to_double_decimal_point(config.get("angle_defletion"));
    if (replacing) {
        linear = std::max(0.003, linear);
        angle = std::max(0.5, angle);
    } else {
        if (linear <= 0) linear = 0.003;
        if (angle <= 0) angle = 0.5;
    }
    const bool split = config.get_bool("is_split_compound");
    if (!config.get_bool("enable_step_mesh_setting")) {
        return {linear, angle, split};
    }
    if (!choice.chosen) {
        // Model::read_from_step() loads the file before it asks, so a file that
        // cannot be read fails before the dialog; the dialog counts the
        // triangles of the file it loaded.
        auto file = std::make_shared<Slic3r::Step>(path);
        if (file->load() != Slic3r::Step::Step_Status::LOAD_SUCCESS) {
            throw Slic3r::RuntimeError("Loading of a model file failed.");
        }
        {
            const std::lock_guard<std::mutex> lock(step_mutex);
            step_file = std::move(file);
            step_path = path;
        }
        throw StepMeshPending{linear, angle, split};
    }
    // The dialog was answered, and its file is not needed any more.
    release_step_file();
    return {choice.linear_deflection, choice.angle_deflection, choice.split_compound};
}

}  // namespace detail

}  // namespace orcinus::orca
